package com.wusun.speaker;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ArrayBlockingQueue;

public class AudioPlayer {
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(24);
    private volatile boolean running = true;
    private final Thread thread;
    private volatile float volume = 1f;
    private volatile int curSr = -1;
    private volatile int curCh = -1;
    private AudioTrack track;

    public AudioPlayer() {
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "audio-player");
        thread.setDaemon(true);
        thread.start();
    }

    public void setVolume(float v) {
        volume = v;
        AudioTrack t = track;
        if (t != null) t.setVolume(v);
    }

    public void onFormat(int sr, int ch) {
        if (sr == curSr && ch == curCh) return;
        curSr = sr;
        curCh = ch;
        AudioTrack old = track;
        track = null;
        if (old != null) {
            try { old.stop(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
        int channelMask = ch >= 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int minBuf = AudioTrack.getMinBufferSize(sr, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, 6 * 1024);
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(channelMask)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufSize)
                    .build();
            track.setVolume(volume);
            track.play();
        } catch (Exception ignored) { }
    }

    public void push(byte[] pcm) {
        if (!running) return;
        if (queue.size() >= 24) queue.poll();
        queue.offer(pcm);
    }

    private void loop() {
        while (running) {
            byte[] data;
            try {
                data = queue.take();
            } catch (InterruptedException e) {
                break;
            }
            AudioTrack t = track;
            if (t == null) {
                curSr = -1;
                curCh = -1;
                continue;
            }
            try {
                if (t.getState() == AudioTrack.STATE_INITIALIZED &&
                        t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    t.play();
                }
            } catch (Exception ignored) { }
            try {
                int off = 0;
                while (off < data.length) {
                    int n;
                    try {
                        n = t.write(data, off, data.length - off);
                    } catch (IllegalStateException e) {
                        try { t.play(); } catch (Exception ignored) { }
                        break;
                    }
                    if (n <= 0) break;
                    off += n;
                }
            } catch (Exception ignored) { }
        }
    }

    public void stop() {
        running = false;
        thread.interrupt();
        AudioTrack t = track;
        if (t != null) {
            try { t.stop(); } catch (Exception ignored) { }
            try { t.release(); } catch (Exception ignored) { }
        }
        track = null;
    }
}
