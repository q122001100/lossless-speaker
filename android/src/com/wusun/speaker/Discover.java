package com.wusun.speaker;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class Discover {
    public interface Callback {
        void onPacket(String name, String ip, int port);
    }

    private final Callback cb;
    private volatile boolean listen = true;
    private DatagramSocket listenSocket;
    private static DatagramSocket announceSocket;
    private static volatile boolean announce;

    public Discover(Callback cb) {
        this.cb = cb;
    }

    public void startListen() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    listenSocket = new DatagramSocket(8610);
                    listenSocket.setSoTimeout(2000);
                    byte[] buf = new byte[512];
                    while (listen) {
                        try {
                            DatagramPacket p = new DatagramPacket(buf, buf.length);
                            listenSocket.receive(p);
                            String s = new String(p.getData(), 0, p.getLength(), "UTF-8");
                            String[] parts = s.split("\\|");
                            if (parts.length >= 4 && parts[0].equals("WSSPEAKER")) {
                                try {
                                    final String name = parts[1];
                                    final String ip = parts[2];
                                    final int port = Integer.parseInt(parts[3]);
                                    cb.onPacket(name, ip, port);
                                } catch (Exception ignored) { }
                            }
                        } catch (java.net.SocketTimeoutException ignored) {
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
            }
        }, "discover");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        listen = false;
        try { listenSocket.close(); } catch (Exception ignored) { }
    }

    public static void startAnnounce(String name, int port) {
        stopAnnounce();
        announce = true;
        final String payload = "WSSPEAKER|" + name + "|" + getLocalIp() + "|" + port;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    announceSocket = new DatagramSocket();
                    announceSocket.setBroadcast(true);
                    InetAddress target = InetAddress.getByName("255.255.255.255");
                    byte[] data = payload.getBytes("UTF-8");
                    while (announce) {
                        try {
                            announceSocket.send(new DatagramPacket(data, data.length, target, 8610));
                        } catch (Exception ignored) { }
                        Thread.sleep(2000);
                    }
                } catch (Exception ignored) { }
            }
        }, "announce");
        t.setDaemon(true);
        t.start();
    }

    public static void stopAnnounce() {
        announce = false;
        try { announceSocket.close(); } catch (Exception ignored) { }
    }

    public static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (!ip.startsWith("169.254")) return ip;
                    }
                }
            }
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }
}
