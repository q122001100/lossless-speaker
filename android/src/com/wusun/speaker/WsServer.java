package com.wusun.speaker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class WsServer {
    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<Client>();
    private volatile boolean running;
    private final AtomicInteger lastSr = new AtomicInteger(48000);
    private final AtomicInteger lastCh = new AtomicInteger(1);

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() { acceptLoop(); }
        }, "ws-accept");
        t.setDaemon(true);
        t.start();
    }

    public void setFormat(int sr, int ch) {
        lastSr.set(sr);
        lastCh.set(ch);
        broadcastText("{\"sr\":" + sr + ",\"ch\":" + ch + "}");
    }

    public void broadcast(byte[] data) {
        for (Client c : clients) c.enqueue(data);
    }

    private void broadcastText(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        for (Client c : clients) c.enqueueControl(payload);
    }

    public int clientCount() { return clients.size(); }

    private void acceptLoop() {
        while (running) {
            Socket s;
            try { s = serverSocket.accept(); } catch (Exception e) { break; }
            final Client c;
            try { c = new Client(s); } catch (IOException e) { continue; }
            clients.add(c);
            Thread t = new Thread(new Runnable() {
                public void run() { handle(c); }
            }, "ws-client");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Client c) {
        try {
            c.handshake();
            android.util.Log.w("wusun-ws", "wsclient connected, total=" + clients.size());
            c.sendControl(("{\"sr\":" + lastSr.get() + ",\"ch\":" + lastCh.get() + "}").getBytes(StandardCharsets.UTF_8));
            Thread sender = new Thread(new Runnable() {
                public void run() { c.senderLoop(); }
            }, "ws-send");
            sender.setDaemon(true);
            sender.start();
            c.readLoop();
        } catch (Exception e) {
            android.util.Log.w("wusun-ws", "wsclient error: " + e.getMessage());
        } finally {
            android.util.Log.w("wusun-ws", "wsclient removed, total=" + (clients.size() - 1));
            c.close();
            clients.remove(c);
        }
    }

    public void stop() {
        running = false;
        try { serverSocket.close(); } catch (Exception ignored) { }
        for (Client c : clients) c.close();
        clients.clear();
    }

    private static String acceptKey(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static class Client {
        final Socket socket;
        final OutputStream out;
        final InputStream in;
        final Queue<byte[]> pending = new ConcurrentLinkedQueue<byte[]>();
        final Object wake = new Object();
        volatile boolean dead;

        Client(Socket s) throws IOException {
            socket = s;
            out = s.getOutputStream();
            in = s.getInputStream();
        }

        void handshake() throws Exception {
            String header = readHeader();
            String key = null;
            for (String line : header.split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            if (key == null) throw new IOException("no sec key");
            String resp = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey(key) + "\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private String readHeader() throws IOException {
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

        void sendControl(byte[] payload) {
            enqueueControl(payload);
        }

        void enqueueControl(byte[] payload) {
            synchronized (wake) {
                pending.add(wrap(0x81, payload));
                wake.notifyAll();
            }
        }

        void enqueue(byte[] data) {
            if (dead) return;
            synchronized (wake) {
                if (pending.size() > 60) pending.clear();
                pending.add(wrap(0x82, data));
                wake.notifyAll();
            }
        }

        private byte[] wrap(int opcode, byte[] payload) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(payload.length + 10);
            b.write(0x80 | opcode);
            int len = payload.length;
            if (len <= 125) {
                b.write(len);
            } else if (len <= 65535) {
                b.write(126);
                b.write(len >> 8);
                b.write(len & 0xFF);
            } else {
                b.write(127);
                for (int i = 7; i >= 0; i--) b.write((len >> (i * 8)) & 0xFF);
            }
            b.write(payload, 0, payload.length);
            return b.toByteArray();
        }

        void senderLoop() {
            try {
                while (!dead) {
                    byte[] frame;
                    synchronized (wake) {
                        while (pending.isEmpty() && !dead) {
                            wake.wait(1000);
                            break;
                        }
                        frame = pending.poll();
                    }
                    if (frame == null) continue;
                    out.write(frame);
                    out.flush();
                }
            } catch (Exception ignored) { }
            dead = true;
        }

        void readLoop() throws Exception {
            while (!dead) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;
                int op = b0 & 0x0F;
                long len = b1 & 0x7F;
                if (len == 126) {
                    len = ((long) in.read() << 8) | in.read();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
                }
                boolean masked = (b1 & 0x80) != 0;
                byte[] mask = new byte[4];
                if (masked) WsServer.readExact(in, mask);
                if (len > 1048576) break;
                byte[] payload = new byte[(int) len];
                if (len > 0) WsServer.readExact(in, payload);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                }
                if (op == 0x9) {
                    final byte[] p = payload;
                    synchronized (wake) {
                        pending.add(wrap(0x8A, p));
                        wake.notifyAll();
                    }
                } else if (op == 0x8) {
                    synchronized (wake) {
                        pending.add(wrap(0x88, new byte[0]));
                        wake.notifyAll();
                    }
                    break;
                }
            }
        }

        void close() {
            dead = true;
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    static void readExact(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n <= 0) throw new IOException("eof");
            off += n;
        }
    }
}
