package com.wusun.speaker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpeakerService extends Service {
    public static final String ACTION_CONNECT = "com.wusun.speaker.CONNECT";
    public static final String ACTION_DISCONNECT = "com.wusun.speaker.DISCONNECT";
    public static final String ACTION_SERVER_START = "com.wusun.speaker.SERVER_START";
    public static final String ACTION_SERVER_STOP = "com.wusun.speaker.SERVER_STOP";
    public static final String ACTION_VOLUME = "com.wusun.speaker.VOLUME";
    public static final String ACTION_SET_SOURCE = "com.wusun.speaker.SET_SOURCE";
    public static final String ACTION_SET_PROJECTION = "com.wusun.speaker.SET_PROJECTION";
    public static final String ACTION_UPGRADE_FGS = "com.wusun.speaker.UPGRADE_FGS";
    public static final String EXTRA_IP = "ip";
    public static final String EXTRA_BOOL = "bool";
    public static final String EXTRA_FLOAT = "float";
    public static final String EXTRA_INT = "int";
    public static final String EXTRA_DATA = "data";

    public static final int SRC_MIC = 0;
    public static final int SRC_INTERNAL = 1;

    private static final String PREFS = "wusun";
    private static final String CHANNEL_ID = "speaker_keepalive";
    private static final int NOTIF_ID = 1001;

    public interface StatusListener {
        void onReceiveStatus(String text);
        void onSendStatus(String text);
    }

    private final List<StatusListener> listeners = new CopyOnWriteArrayList<StatusListener>();

    private AudioPlayer player;
    private WsClient client;
    private WsServer server;
    private volatile boolean micRunning;
    private AudioRecord recorder;
    private Thread micThread;
    private long sendCnt;
    private String lastIp = "";
    private String lastRecvStatus = "未连接";
    private String lastSendStatus = "未启动";
    private int lastSr = 48000;
    private int lastCh = 1;
    private volatile int sourceMode = SRC_MIC;
    private int projResultCode;
    private Intent projData;
    private MediaProjection projection;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public class LocalBinder extends android.os.Binder {
        public SpeakerService getService() {
            return SpeakerService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    public static void action(Context ctx, String action, String ip, boolean b, float f) {
        action(ctx, action, ip, b, f, 0);
    }

    public static void action(Context ctx, String action, String ip, boolean b, float f, int extraInt) {
        Intent i = new Intent(ctx, SpeakerService.class);
        i.setAction(action);
        if (ip != null) i.putExtra(EXTRA_IP, ip);
        i.putExtra(EXTRA_BOOL, b);
        i.putExtra(EXTRA_FLOAT, f);
        i.putExtra(EXTRA_INT, extraInt);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public void addListener(StatusListener l) {
        listeners.add(l);
    }

    public void removeListener(StatusListener l) {
        listeners.remove(l);
    }

    public static volatile boolean EN;

    public String getRecvStatus() { return EN ? translateStatus(lastRecvStatus) : lastRecvStatus; }
    public String getSendStatus() { return EN ? translateStatus(lastSendStatus) : lastSendStatus; }

    private static String translateStatus(String zh) {
        if (zh.startsWith("正在连接 ")) return "Connecting to " + zh.substring(5);
        if (zh.startsWith("发送启动失败: ")) return "Failed to start sending: " + zh.substring(8);
        if (zh.startsWith("发送端运行中(内置声音 ")) return "Sending (internal audio " + zh.substring(13);
        if (zh.startsWith("内置声音启动失败: ")) return "Internal audio start failed: " + zh.substring(10);
        if (zh.equals("未连接")) return "Not connected";
        if (zh.equals("未启动")) return "Not started";
        if (zh.equals("已连接,等待声音")) return "Connected, waiting for audio";
        if (zh.equals("内置声音已授权,点开始发送")) return "Internal audio authorized. Tap Start Sending";
        if (zh.equals("音源: 内置声音(需授权)")) return "Source: Internal audio (needs permission)";
        if (zh.equals("音源: 麦克风")) return "Source: Microphone";
        if (zh.equals("发送端运行中(内置声音)")) return "Sending (internal audio)";
        if (zh.equals("发送端运行中(麦克风)")) return "Sending (microphone)";
        if (zh.equals("麦克风初始化失败")) return "Microphone init failed";
        if (zh.equals("内置声音需要安卓10及以上")) return "Internal audio needs Android 10+";
        if (zh.equals("内置声音未授权,请点\"授权\"")) return "Internal audio not authorized, tap \"Authorize\"";
        if (zh.equals("内置声音授权失败,请重新授权")) return "Internal audio authorization failed, re-authorize";
        if (zh.equals("内置声音采集初始化失败")) return "Internal audio capture init failed";
        return zh;
    }
    public boolean isReceiving() { return client != null; }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new AudioPlayer();
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wusun:keepalive");
            wakeLock.acquire();
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wusun:wifi");
                wifiLock.acquire();
            }
        } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String a = intent.getAction();
            if (ACTION_CONNECT.equals(a)) {
                connect(intent.getStringExtra(EXTRA_IP));
            } else if (ACTION_DISCONNECT.equals(a)) {
                disconnect();
            } else if (ACTION_SERVER_START.equals(a)) {
                startServer();
            } else if (ACTION_SERVER_STOP.equals(a)) {
                stopServer();
            } else if (ACTION_UPGRADE_FGS.equals(a)) {
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        startForeground(NOTIF_ID, buildNotification(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                    } catch (Exception ignored) { }
                }
            } else if (ACTION_SET_SOURCE.equals(a)) {
                setSource(intent.getIntExtra(EXTRA_INT, SRC_MIC));
            } else if (ACTION_SET_PROJECTION.equals(a)) {
                projResultCode = intent.getIntExtra(EXTRA_INT, 0);
                projData = (Intent) intent.getParcelableExtra(EXTRA_DATA);
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        startForeground(NOTIF_ID, buildNotification(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                    } catch (Exception ignored) { }
                }
                lastSendStatus = "内置声音已授权,点开始发送";
                notifySend();
                updateNotification();
            } else if (ACTION_VOLUME.equals(a)) {
                player.setVolume(intent.getFloatExtra(EXTRA_FLOAT, 1f));
            }
        } else {
            restore();
        }
        return START_STICKY;
    }

    private void restore() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        sourceMode = p.getInt("src", SRC_MIC);
        boolean send = p.getBoolean("send", false);
        String ip = p.getString(EXTRA_IP, "");
        if (send) startServer();
        if (!ip.isEmpty()) connect(ip);
        if (!send && ip.isEmpty()) stopIfIdle();
    }

    private void connect(String ip) {
        if (ip == null || ip.isEmpty()) return;
        if (client != null) {
            if (ip.equals(lastIp)) return;
            client.stop();
            client = null;
        }
        lastIp = ip;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(EXTRA_IP, ip).apply();
        lastRecvStatus = "正在连接 " + ip + " …";
        notifyRecv();
        updateNotification();
        client = new WsClient(ip, 8600, new WsClient.Listener() {
            @Override
            public void onControl(int sr, int ch) {
                lastSr = sr;
                lastCh = ch;
                player.onFormat(sr, ch);
            }
            @Override
            public void onData(byte[] pcm) {
                player.push(pcm);
            }

            @Override
            public void onStatus(String text) {
                lastRecvStatus = text;
                notifyRecv();
                updateNotification();
            }
        });
        Thread t = new Thread(new Runnable() {
            public void run() { client.connect(); }
        }, "ws-connect");
        t.setDaemon(true);
        t.start();
    }

    private void disconnect() {
        if (client != null) client.stop();
        client = null;
        lastIp = "";
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(EXTRA_IP).apply();
        lastRecvStatus = "未连接";
        notifyRecv();
        updateNotification();
        stopIfIdle();
    }

    private void setSource(int mode) {
        if (mode == sourceMode) return;
        sourceMode = mode;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("src", mode).apply();
        if (server != null && micRunning) {
            micRunning = false;
            try { if (recorder != null) recorder.stop(); } catch (Exception ignored) { }
            Thread old = micThread;
            if (old != null) {
                try { old.join(1500); } catch (InterruptedException ignored) { }
            }
            startMic();
        }
        lastSendStatus = mode == SRC_INTERNAL ? "音源: 内置声音(需授权)" : "音源: 麦克风";
        notifySend();
        updateNotification();
    }

    private void startServer() {
        if (server != null) {
            if (!micRunning) startMic();
            return;
        }
        try {
            server = new WsServer();
            server.start(8600);
            pushFormat(48000, 1);
            Discover.startAnnounce("手机发送端", 8600);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("send", true).apply();
            lastSendStatus = sourceMode == SRC_INTERNAL ? "发送端运行中(内置声音)" : "发送端运行中(麦克风)";
            startMic();
        } catch (Exception e) {
            server = null;
            lastSendStatus = "发送启动失败: " + e.getMessage();
        }
        notifySend();
        updateNotification();
    }

    private void pushPcm(byte[] chunk) {
        if (server != null) server.broadcast(chunk);
        WsClient c = client;
        if (c != null) {
            c.sendData(chunk);
            if (sendCnt++ % 200 == 0) android.util.Log.w("wusun-ws", "SEND pcm len=" + chunk.length + " n=" + sendCnt);
        } else if (sendCnt++ % 200 == 0) {
            android.util.Log.w("wusun-ws", "MIC pcm len=" + chunk.length + " n=" + sendCnt + " clients=" + (server != null ? server.clientCount() : -1));
        }
    }

    private void pushFormat(int sr, int ch) {
        if (server != null) server.setFormat(sr, ch);
        if (client != null) client.sendControl("{\"sr\":" + sr + ",\"ch\":" + ch + "}");
    }

    private void startMic() {
        micRunning = true;
        micThread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (sourceMode == SRC_INTERNAL) {
                        startInternalCapture();
                        return;
                    }
                    int minBuf = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    AudioRecord.Builder rb = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setSampleRate(48000)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                    .build())
                            .setBufferSizeInBytes(Math.max(minBuf * 2, 9600));
                    recorder = rb.build();
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        lastSendStatus = "麦克风初始化失败";
                        notifySend();
                        return;
                    }
                    if (AcousticEchoCanceler.isAvailable()) {
                        try {
                            AcousticEchoCanceler aec = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                            if (aec != null) aec.setEnabled(true);
                        } catch (Exception ignored) { }
                    }
                    recorder.startRecording();
                    byte[] buf = new byte[4800];
                    int silentReads = 0;
                    while (micRunning && server != null) {
                        int n = recorder.read(buf, 0, buf.length);
                        if (n > 0) {
                            silentReads = 0;
                            byte[] chunk = new byte[n];
                            System.arraycopy(buf, 0, chunk, 0, n);
                            pushPcm(chunk);
                        } else {
                            silentReads++;
                            if (silentReads == 1 || silentReads % 200 == 0) {
                                android.util.Log.w("wusun-ws", "mic read n=" + n + " silent=" + silentReads);
                            }
                        }
                    }
                    try { recorder.stop(); } catch (Exception ignored) { }
                    recorder.release();
                    recorder = null;
                } catch (Exception ignored) { } finally {
                    micRunning = false;
                }
            }
        }, "mic");
        micThread.setDaemon(true);
        micThread.start();
    }

    private void startInternalCapture() {
        if (Build.VERSION.SDK_INT < 29) {
            lastSendStatus = "内置声音需要安卓10及以上";
            notifySend();
            return;
        }
        if (projData == null || projResultCode == 0) {
            lastSendStatus = "内置声音未授权,请点\"授权\"";
            notifySend();
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                startForeground(NOTIF_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (Exception ignored) { }
        }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(projResultCode, projData);
            if (projection == null) {
                lastSendStatus = "内置声音授权失败,请重新授权";
                notifySend();
                return;
            }
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioRecord.Builder rb = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(48000)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build())
                    .setBufferSizeInBytes(9600);
            recorder = rb.build();
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                lastSendStatus = "内置声音采集初始化失败";
                notifySend();
                return;
            }
            int sr = recorder.getSampleRate();
            if (sr != 48000) sr = 48000;
            pushFormat(sr, 2);
            lastSendStatus = "发送端运行中(内置声音 " + sr + "Hz)";
            notifySend();
            recorder.startRecording();
            byte[] buf = new byte[4800];
            while (micRunning && server != null) {
                int n = recorder.read(buf, 0, buf.length);
                if (n > 0) {
byte[] chunk = new byte[n];
                            System.arraycopy(buf, 0, chunk, 0, n);
                            pushPcm(chunk);
                }
            }
            try { recorder.stop(); } catch (Exception ignored) { }
            recorder.release();
            recorder = null;
            if (projection != null) {
                try { projection.stop(); } catch (Exception ignored) { }
                projection = null;
            }
        } catch (Exception e) {
            lastSendStatus = "内置声音启动失败: " + e.getMessage();
            notifySend();
        } finally {
            micRunning = false;
        }
    }

    private void stopServer() {
        micRunning = false;
        try {
            if (recorder != null) recorder.stop();
        } catch (Exception ignored) { }
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) { }
            projection = null;
        }
        if (server != null) server.stop();
        server = null;
        Discover.stopAnnounce();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("send", false).apply();
        lastSendStatus = "未启动";
        notifySend();
        updateNotification();
        stopIfIdle();
    }

    private void stopIfIdle() {
        if (client == null && server == null) {
            stopSelf();
        }
    }

    private void notifyRecv() {
        for (StatusListener l : listeners) l.onReceiveStatus(lastRecvStatus);
    }

    private void notifySend() {
        for (StatusListener l : listeners) l.onSendStatus(lastSendStatus + (server != null ? " | 客户端 " + server.clientCount() : ""));
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (client != null) client.stop();
        client = null;
        micRunning = false;
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) { }
        if (server != null) server.stop();
        server = null;
        Discover.stopAnnounce();
        if (player != null) player.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "后台保活", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持无损音箱后台运行");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        String text;
        if (server != null && client != null) text = "接收中 + 发送中(客户端 " + server.clientCount() + ")";
        else if (server != null) text = "发送传音中(客户端 " + server.clientCount() + ")";
        else if (client != null) text = "接收中:" + lastIp;
        else text = "后台待机";
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_media_play);
        b.setContentTitle("无损音箱");
        b.setContentText(text);
        b.setContentIntent(pi);
        b.setOngoing(true);
        b.setShowWhen(false);
        return b.build();
    }
}
