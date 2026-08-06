package com.wusun.speaker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Base64;

public class WsClient {
    public interface Listener {
        void onControl(int sr, int ch);
        void onData(byte[] pcm);
        void onStatus(String text);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private volatile boolean stop;
    private Socket socket;
    private boolean noticed;
    private long dataCnt;
    private final Object writeLock = new Object();
    private static final byte[] SEND_MASK = new byte[] { 0x2A, (byte) 0x91, 0x5C, 0x37 };

    public WsClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void stop() {
        android.util.Log.w("wusun-ws", "stop() called", new Exception("stop-called"));
        stop = true;
        try { socket.close(); } catch (Exception ignored) { }
    }

    public void connect() {
        int attempt = 0;
        while (!stop) {
            attempt++;
            try {
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setReceiveBufferSize(16384);
                socket.setSendBufferSize(32768);
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(15000);
                handshake();
                android.util.Log.w("wusun-ws", "connected to " + host + ":" + port);
                listener.onStatus("已连接,等待声音");
                readLoop();
                android.util.Log.w("wusun-ws", "readLoop returned normally, stop=" + stop + " closed=" + socket.isClosed() + " attempt=" + attempt);
            } catch (Exception e) {
                android.util.Log.w("wusun-ws", "loop-ex attempt=" + attempt + " stop=" + stop + " " + e + " conn=" + (socket != null ? socket.isConnected() : false), e);
            }
            if (stop) { android.util.Log.w("wusun-ws", "loop exit due to stop"); return; }
            listener.onStatus("连接断开,2秒后自动重连");
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    private void handshake() throws Exception {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        OutputStream out = socket.getOutputStream();
        String req = "GET / HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes("UTF-8"));
        out.flush();
        readHeader();
    }

    private String readHeader() throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int a = -1, b = -1, c = -1, d = -1;
        while (true) {
            int x = in.read();
            if (x < 0) throw new IOException("eof");
            baos.write(x);
            a = b; b = c; c = d; d = x;
            if (a == '\r' && b == '\n' && c == '\r' && d == '\n') break;
            if (baos.size() > 16384) throw new IOException("huge header");
        }
        return baos.toString("UTF-8");
    }

    private void readLoop() throws Exception {
        InputStream in = socket.getInputStream();
        while (!stop) {
            int b0 = in.read();
            if (b0 < 0) throw new IOException("eof");
            int b1 = in.read();
            if (b1 < 0) throw new IOException("eof");
            int op = b0 & 0x0F;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) in.read() << 8) | in.read();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    int x = in.read();
                    if (x < 0) throw new IOException("eof");
                    len = (len << 8) | x;
                }
            }
            boolean masked = (b1 & 0x80) != 0;
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                readExact(in, mask);
            }
            if (len > 4 * 1024 * 1024) throw new IOException("frame too big");
            byte[] payload = new byte[(int) len];
            if (len > 0) readExact(in, payload);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            }
            if (op == 0x1) {
                try { parseControl(new String(payload, "UTF-8")); } catch (Exception ignored) { }
            } else if (op == 0x2) {
                try { listener.onData(payload); } catch (Exception ignored) { }
            } else if (op == 0x9) {
                sendMasked(0x8A, payload);
            } else if (op == 0x8) {
                android.util.Log.w("wusun-ws", "recv close frame op=8 b0=" + b0 + " b1=" + b1 + " len=" + len);
                try { sendMasked(0x88, new byte[0]); } catch (Exception ignored) { }
                break;
            } else {
                if (!noticed) { android.util.Log.w("wusun-ws", "recv op=" + op + " len=" + len + " b0=" + b0); noticed = true; }
            }
        }
    }

    public void sendData(byte[] pcm) {
        try {
            if (socket != null && socket.isConnected()) {
                sendMasked(0x2, pcm);
                if (dataCnt++ % 200 == 0) android.util.Log.w("wusun-ws", "dataSend len=" + pcm.length + " cnt=" + dataCnt);
            }
        } catch (Exception ignored) { }
    }

    public void sendControl(String text) {
        try {
            if (socket != null && socket.isConnected()) {
                byte[] payload = text.getBytes("UTF-8");
                byte[] p = payload.length > 4096 ? new byte[0] : payload;
                sendMasked(0x1, p);
            }
        } catch (Exception ignored) { }
    }

    private void sendMasked(int opcode, byte[] payload) throws IOException {
        synchronized (writeLock) {
            OutputStream out = socket.getOutputStream();
            byte[] mask = SEND_MASK;
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            head.write(0x80 | opcode);
            int len = payload.length;
            if (len <= 125) {
                head.write(0x80 | len);
            } else if (len <= 65535) {
                head.write(0x80 | 126);
                head.write(len >> 8);
                head.write(len & 0xFF);
            } else {
                head.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) head.write((len >> (i * 8)) & 0xFF);
            }
            head.write(mask[0]); head.write(mask[1]); head.write(mask[2]); head.write(mask[3]);
            byte[] masked = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) masked[i] = (byte) (payload[i] ^ mask[i & 3]);
            out.write(head.toByteArray());
            out.write(masked);
            out.flush();
        }
    }

    private void parseControl(String text) {
        try {
            String body = text.replace("{", "").replace("}", "");
            int sr = 48000, ch = 2;
            for (String part : body.split(",")) {
                String[] kv = part.split(":");
                if (kv.length != 2) continue;
                String k = kv[0].trim().replace("\"", "");
                String v = kv[1].trim();
                if (k.equals("sr")) sr = Integer.parseInt(v);
                else if (k.equals("ch")) ch = Integer.parseInt(v);
            }
            listener.onControl(sr, ch);
        } catch (Exception ignored) { }
    }

    private static void readExact(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n <= 0) throw new IOException("eof");
            off += n;
        }
    }
}
