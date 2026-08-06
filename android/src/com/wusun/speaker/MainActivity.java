package com.wusun.speaker;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1;
    private static final int REQ_NOTIF = 2;
    private static final int REQ_PROJECTION = 3;
    private static final int REQ_FGS_PROJ = 4;
    private boolean pendingProjectionRequest;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SpeakerService service;
    private Discover discover;
    private TextView statusView;
    private TextView serverStatusView;
    private LinearLayout deviceList;
    private LinearLayout receivePanel;
    private LinearLayout sendPanel;
    private TextView serverInfoView;
    private Button startSend;
    private int srcMode = SpeakerService.SRC_MIC;
    private boolean projectionGranted;
    private boolean en;
    private boolean sendVisible;
    private Runnable quoteRunnable;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SpeakerService.LocalBinder) binder).getService();
            service.addListener(listener);
            refreshFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    private final SpeakerService.StatusListener listener = new SpeakerService.StatusListener() {
        @Override
        public void onReceiveStatus(final String text) {
            ui.post(new Runnable() {
                public void run() { statusView.setText(text); }
            });
        }

        @Override
        public void onSendStatus(final String text) {
            ui.post(new Runnable() {
                public void run() { serverStatusView.setText(text); }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFFF7F3FB);
            getWindow().setNavigationBarColor(0xFFF7F3FB);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        en = getSharedPreferences("wusun", MODE_PRIVATE).getBoolean("en", false);
        SpeakerService.EN = en;
        setContentView(buildUi());
        discover = new Discover(new Discover.Callback() {
            @Override
            public void onPacket(final String name, final String ip, final int port) {
                ui.post(new Runnable() {
                    public void run() { onDiscovered(name, ip, port); }
                });
            }
        });
        discover.startListen();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, SpeakerService.class), conn, Context.BIND_AUTO_CREATE);
        Intent si = new Intent(this, SpeakerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
        else startService(si);
    }

    @Override
    protected void onStop() {
        if (service != null) {
            service.removeListener(listener);
            service = null;
        }
        try {
            unbindService(conn);
        } catch (Exception ignored) { }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (discover != null) discover.stop();
        super.onDestroy();
    }

    private void refreshFromService() {
        if (service == null) return;
        statusView.setText(service.getRecvStatus());
        serverStatusView.setText(service.getSendStatus());
        String st = service.getSendStatus();
        if (en ? st.equals("Not started") : st.contains("未启动")) {
            serverInfoView.setText(tr("电脑端访问\nhttp://", "On PC open\nhttp://") + Discover.getLocalIp() + ":8600\n" + tr("即可接收手机声音", "to receive phone audio"));
        }
    }

    private View buildUi() {
        final int C_BG = 0xFFF7F3FB;
        final int C_CARD = 0xFFFFFFFF;
        final int C_PRIMARY = 0xFF8B7BD8;
        final int C_PRIMARY_DK = 0xFF6F5FB8;
        final int C_ACCENT = 0xFFE8A0BF;
        final int C_SAGE = 0xFFA8C5A0;
        final int C_TEXT = 0xFF3D3A45;
        final int C_SUB = 0xFF8A84A8;
        final int C_LINE = 0xFFE4DFF2;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        scroll.addView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(tr("无损音箱", "Lossless Speaker"));
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY_DK);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final Button langBtn = makeButton(this, tr("EN", "中"), C_PRIMARY, 0xFF7B6BD3, 0xFFFFFFFF, dp(12), 0);
        langBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(langBtn, new LinearLayout.LayoutParams(dp(56), dp(36)));
        root.addView(titleRow);

        TextView sub = new TextView(this);
        sub.setText(tr("电脑/手机传音 · 未压缩无损 · 双模式", "PC/Phone audio · Lossless · Dual mode"));
        sub.setTextSize(13);
        sub.setTextColor(C_SUB);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(14));
        root.addView(sub);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button recvMode = makeButton(this, tr("接收传音", "Receive"), C_PRIMARY, 0xFF7B6BD3, 0xFFFFFFFF, dp(12), 0);
        final Button sendMode = makeButton(this, tr("发送传音", "Send"), 0xFFFFFFFF, 0xFFDCD3F5, C_PRIMARY_DK, dp(12), 0);
        recvMode.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sendMode.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        modeRow.addView(recvMode, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams lpSend = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lpSend.leftMargin = dp(8);
        modeRow.addView(sendMode, lpSend);
        root.addView(modeRow);

        TextView batteryOpt = new TextView(this);
        batteryOpt.setText(tr("忽略电池优化(防止后台被杀)", "Ignore battery optimization"));
        batteryOpt.setTextColor(0xFF7C6FE0);
        batteryOpt.setTextSize(13);
        batteryOpt.setGravity(Gravity.CENTER);
        batteryOpt.setPadding(0, dp(8), 0, dp(8));
        batteryOpt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    setStatus(tr("系统不支持直接跳转,请在系统设置-应用-电池优化中手动设置",
                            "Direct jump unsupported; set battery optimization manually in System Settings"));
                }
            }
        });
        root.addView(batteryOpt);

        receivePanel = new LinearLayout(this);
        receivePanel.setOrientation(LinearLayout.VERTICAL);
        receivePanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        receivePanel.setBackground(roundRect(0xFFFFFFFF, dp(16)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8);
        root.addView(receivePanel, rp);

        TextView devTitle = new TextView(this);
        devTitle.setText(tr("自动发现的设备(电脑/手机):", "Discovered devices (PC/phone):"));
        devTitle.setTextSize(14);
        devTitle.setTextColor(C_TEXT);
        devTitle.setPadding(0, 0, 0, dp(2));
        receivePanel.addView(devTitle);

        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        receivePanel.addView(deviceList);

        LinearLayout manualRow = new LinearLayout(this);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        manualRow.setPadding(0, dp(10), 0, 0);
        final EditText ipInput = new EditText(this);
        ipInput.setHint(tr("手动输入 IP 地址", "Enter IP address"));
        ipInput.setText(Discover.getLocalIp());
        ipInput.setTextSize(14);
        ipInput.setSingleLine(true);
        ipInput.setBackground(roundRect(0xFFF3F0FA, dp(10)));
        final Button connectBtn = makeButton(this, tr("连接", "Connect"), C_ACCENT, 0xFFE08BA9, 0xFFFFFFFF, dp(10), 0);
        manualRow.addView(ipInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(dp(80), dp(44));
        lpC.leftMargin = dp(8);
        manualRow.addView(connectBtn, lpC);
        receivePanel.addView(manualRow);

        statusView = new TextView(this);
        statusView.setText(tr("未连接", "Not connected"));
        statusView.setTextSize(14);
        statusView.setTextColor(C_SUB);
        statusView.setPadding(0, dp(12), 0, 0);
        receivePanel.addView(statusView);

        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setPadding(0, dp(12), 0, 0);
        TextView volLabel = new TextView(this);
        volLabel.setText(tr("音量", "Volume"));
        volLabel.setTextSize(14);
        volLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        volLabel.setTextColor(C_PRIMARY_DK);
        volLabel.setGravity(Gravity.CENTER);
        volLabel.setBackground(roundRect(0xFFF3F0FA, dp(10)));
        volRow.addView(volLabel, new LinearLayout.LayoutParams(dp(64), dp(34)));
        SeekBar volBar = new SeekBar(this);
        volBar.setMax(100);
        volBar.setProgress(100);
        LinearLayout.LayoutParams lpVol = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lpVol.leftMargin = dp(8);
        volRow.addView(volBar, lpVol);
        receivePanel.addView(volRow);
        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) SpeakerService.action(MainActivity.this, SpeakerService.ACTION_VOLUME, null, false, p / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) { }

            @Override
            public void onStopTrackingTouch(SeekBar sb) { }
        });

        LinearLayout.LayoutParams lpDisc = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lpDisc.topMargin = dp(12);
        Button disconnectBtn = makeButton(this, tr("断开连接", "Disconnect"), 0xFFFFFFFF, 0xFFDCD3F5, C_PRIMARY_DK, dp(10), 0);
        receivePanel.addView(disconnectBtn, lpDisc);
        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SpeakerService.action(MainActivity.this, SpeakerService.ACTION_DISCONNECT, null, false, 0f);
            }
        });

        sendPanel = new LinearLayout(this);
        sendPanel.setOrientation(LinearLayout.VERTICAL);
        sendPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        sendPanel.setBackground(roundRect(0xFFFFFFFF, dp(16)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(8);
        sendPanel.setVisibility(View.GONE);
        root.addView(sendPanel, sp);

        serverInfoView = new TextView(this);
        serverInfoView.setText(tr("电脑端访问\nhttp://", "On PC open\nhttp://") + Discover.getLocalIp() + ":8600\n" + tr("即可接收手机声音", "to receive phone audio"));
        serverInfoView.setTextSize(16);
        serverInfoView.setTextColor(C_PRIMARY_DK);
        serverInfoView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        serverInfoView.setGravity(Gravity.CENTER);
        serverInfoView.setLineSpacing(0, 1.25f);
        serverInfoView.setBackground(roundRect(0xFFF3F0FA, dp(12)));
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        siLp.topMargin = dp(10);
        siLp.bottomMargin = dp(4);
        serverInfoView.setPadding(dp(12), dp(14), dp(12), dp(14));
        sendPanel.addView(serverInfoView, siLp);

        TextView srcLabel = new TextView(this);
        srcLabel.setText(tr("选择音源", "Choose source"));
        srcLabel.setTextSize(13);
        srcLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        srcLabel.setTextColor(C_PRIMARY_DK);
        srcLabel.setPadding(0, dp(10), 0, dp(4));
        sendPanel.addView(srcLabel);

        LinearLayout srcRow = new LinearLayout(this);
        srcRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button micBtn = makeButton(this, tr("麦克风", "Mic"), C_PRIMARY, 0xFF7B6BD3, 0xFFFFFFFF, dp(10), 0);
        final Button inBtn = makeButton(this, tr("内置声音", "App audio"), 0xFFFFFFFF, 0xFFDCD3F5, C_PRIMARY_DK, dp(10), 0);
        micBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        srcRow.addView(micBtn);
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lpIn.leftMargin = dp(8);
        srcRow.addView(inBtn, lpIn);
        sendPanel.addView(srcRow);

        micBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                srcMode = SpeakerService.SRC_MIC;
                micBtn.setTag(0xFF8B7BD8);
                micBtn.setBackground(roundRect(0xFF8B7BD8, dp(10)));
                micBtn.setTextColor(0xFFFFFFFF);
                inBtn.setTag(0xFFFFFFFF);
                inBtn.setBackground(roundRect(0xFFFFFFFF, dp(10)));
                inBtn.setTextColor(C_PRIMARY_DK);
                startSend.setText(tr("开始发送", "Start"));
                SpeakerService.action(MainActivity.this, SpeakerService.ACTION_SET_SOURCE, null,
                        false, 0f, SpeakerService.SRC_MIC);
            }
        });
        inBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                srcMode = SpeakerService.SRC_INTERNAL;
                micBtn.setTag(0xFFFFFFFF);
                micBtn.setBackground(roundRect(0xFFFFFFFF, dp(10)));
                micBtn.setTextColor(C_PRIMARY_DK);
                inBtn.setTag(0xFF8B7BD8);
                inBtn.setBackground(roundRect(0xFF8B7BD8, dp(10)));
                inBtn.setTextColor(0xFFFFFFFF);
                startSend.setText(tr("开始发送", "Start"));
                SpeakerService.action(MainActivity.this, SpeakerService.ACTION_SET_SOURCE, null,
                        false, 0f, SpeakerService.SRC_INTERNAL);
                if (!projectionGranted) requestProjection();
            }
        });
        micBtn.setSelected(true);

        final Button stopSend = makeButton(this, tr("停止发送", "Stop"), 0xFFFFFFFF, 0xFFDCD3F5, C_PRIMARY_DK, dp(10), dp(8));
        Button startSendB = makeButton(this, tr("开始发送", "Start"), C_PRIMARY, 0xFF7B6BD3, 0xFFFFFFFF, dp(10), dp(8));
        startSend = startSendB;
        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpStart = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lpStart.topMargin = dp(8);
        LinearLayout.LayoutParams lpStop = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lpStop.topMargin = dp(8);
        lpStop.leftMargin = dp(8);
        sendRow.addView(startSend, lpStart);
        sendRow.addView(stopSend, lpStop);
        sendPanel.addView(sendRow);

        serverStatusView = new TextView(this);
        serverStatusView.setText(tr("未启动", "Not started"));
        serverStatusView.setTextSize(14);
        serverStatusView.setTextColor(C_SUB);
        serverStatusView.setGravity(Gravity.CENTER);
        serverStatusView.setPadding(0, dp(10), 0, 0);
        sendPanel.addView(serverStatusView);

        final String[] qZh = new String[] {
                "其实地上本没有路,走的人多了,也便成了路。",
                "愿中国青年都摆脱冷气,只是向上走,不必听自暴自弃者流的话。",
                "不在沉默中爆发,就在沉默中灭亡。",
                "我们自古以来,就有埋头苦干的人,有拼命硬干的人,有为民请命的人,有舍身求法的人,这就是中国的脊梁。",
                "横眉冷对千夫指,俯首甘为孺子牛。",
                "真的勇士,敢于直面惨淡的人生,敢于正视淋漓的鲜血。",
                "哪里有天才,我是把别人喝咖啡的工夫都用在工作上的。",
                "时间就像海绵里的水,只要愿挤,总还是有的。",
                "哀其不幸,怒其不争。",
                "愿所有人都能摆脱冷气和黑暗,向上走。",
                "希望是附丽于存在的,有存在,便有希望,有希望,便是光明。",
                "巨大的建筑,总是由一木一石叠起来的,我们何妨做做这一木一石呢。",
                "人生最苦痛的是梦醒了无路可以走。",
                "无穷的远方,无数的人们,都和我有关。",
                "从来如此,便对么?",
                "有缺点的战士终竟是战士,完美的苍蝇也终竟不过是苍蝇。",
                "无情未必真豪杰,怜子如何不丈夫。",
                "度尽劫波兄弟在,相逢一笑泯恩仇。",
                "我好像是一只牛,吃的是草,挤出的是奶、血。",
                "人既发扬踔厉矣,则邦国亦以兴起。",
                "空谈之类,是谈不久,也谈不出什么来的,它终必被事实的镜子照出原形。",
                "只要能培一朵花,就不妨做做会朽的腐草。",
                "做一件事,无论大小,倘无恒心,是很不好的。",
                "人类的悲欢并不相通,我只觉得他们吵闹。"
        };
        final String[] qEn = new String[] {
                "There was no path on the ground; once many walk it, a path is made.",
                "May the youth of China shed the chill and keep moving upward, heeding not the self-abandoned.",
                "Break out in silence, or perish in silence.",
                "Since ancient times we have had the hard-working, the resolute, the pleaders for the people, the self-sacrificing — they are China's backbone.",
                "With cold brow I face a thousand pointing fingers; with head bowed I serve like an ox.",
                "A true warrior dares face bleak life and dripping blood.",
                "Where is genius? I simply spent the time others spent drinking coffee on work.",
                "Time is like water in a sponge — squeeze and there is always more.",
                "Grieve at her misfortune, rage at her unwillingness to fight.",
                "May everyone cast off chill and darkness and move upward.",
                "Hope attaches to existence: with existence, hope; with hope, light.",
                "A great building rises from single logs and stones — why not be such a log or stone?",
                "The bitterest pain in life is waking from a dream with no road to walk.",
                "The endless distance and countless people all concern me.",
                "Just because it has always been so, is it right?",
                "A flawed warrior is still a warrior; a perfect fly is still but a fly.",
                "Without emotion one is not truly a hero; loving one's son does not make one less a man.",
                "After the storms of fate, brothers remain; a smile at reunion dissolves all grudges.",
                "I am like an ox — feeding on grass, giving milk and blood.",
                "When one strides vigorously, the nation rises with him.",
                "Empty talk never lasts and yields nothing; the mirror of facts will show its true shape.",
                "If I can nurture a flower, I would gladly be the decaying grass.",
                "Whatever you do, great or small, without persistence it comes to nothing.",
                "Human joys and sorrows are not shared — I only find them noisy."
        };
        final String[] quotes = en ? qEn : qZh;
        final TextView quoteView = new TextView(this);
        quoteView.setText(quotes[0]);
        quoteView.setTextSize(12);
        quoteView.setTextColor(C_SUB);
        quoteView.setGravity(Gravity.CENTER);
        quoteView.setLineSpacing(0, 1.2f);
        quoteView.setPadding(dp(4), dp(10), dp(4), dp(2));
        root.addView(quoteView);
        final int[] qi = {0};
        quoteRunnable = new Runnable() {
            public void run() {
                qi[0] = (qi[0] + 1) % quotes.length;
                quoteView.setText(quotes[qi[0]]);
                ui.postDelayed(this, 3000);
            }
        };
        ui.postDelayed(quoteRunnable, 3000);

        Button aboutBtn = makeButton(this, tr("关于", "About"), 0xFFFFFFFF, 0xFFDCD3F5, C_PRIMARY_DK, dp(12), dp(8));
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        abLp.topMargin = dp(12);
        root.addView(aboutBtn, abLp);
        aboutBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showAboutDialog(); }
        });

        recvMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendVisible = false;
                receivePanel.setVisibility(View.VISIBLE);
                sendPanel.setVisibility(View.GONE);
                recvMode.setTag(0xFF8B7BD8);
                recvMode.setBackground(roundRect(0xFF8B7BD8, dp(12)));
                recvMode.setTextColor(0xFFFFFFFF);
                sendMode.setTag(0xFFFFFFFF);
                sendMode.setBackground(roundRect(0xFFFFFFFF, dp(12)));
                sendMode.setTextColor(C_PRIMARY_DK);
            }
        });
        sendMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendVisible = true;
                receivePanel.setVisibility(View.GONE);
                sendPanel.setVisibility(View.VISIBLE);
                recvMode.setTag(0xFFFFFFFF);
                recvMode.setBackground(roundRect(0xFFFFFFFF, dp(12)));
                recvMode.setTextColor(C_PRIMARY_DK);
                sendMode.setTag(0xFF8B7BD8);
                sendMode.setBackground(roundRect(0xFF8B7BD8, dp(12)));
                sendMode.setTextColor(0xFFFFFFFF);
            }
        });

        if (sendVisible) {
            recvMode.setTag(0xFFFFFFFF);
            recvMode.setBackground(roundRect(0xFFFFFFFF, dp(12)));
            recvMode.setTextColor(C_PRIMARY_DK);
            sendMode.setTag(0xFF8B7BD8);
            sendMode.setBackground(roundRect(0xFF8B7BD8, dp(12)));
            sendMode.setTextColor(0xFFFFFFFF);
            receivePanel.setVisibility(View.GONE);
            sendPanel.setVisibility(View.VISIBLE);
        } else {
            receivePanel.setVisibility(View.VISIBLE);
            sendPanel.setVisibility(View.GONE);
        }

        langBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setLanguage(!en); }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String ip = ipInput.getText().toString().trim();
                if (ip.isEmpty()) return;
                SpeakerService.action(MainActivity.this, SpeakerService.ACTION_CONNECT, ip, false, 0f);
            }
        });

        startSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startSending(); }
        });
        stopSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SpeakerService.action(MainActivity.this, SpeakerService.ACTION_SERVER_STOP, null, false, 0f);
            }
        });

        return scroll;
    }

    private void showAboutDialog() {
        final int C_BG = 0xFFF7F3FB;
        final int C_PRIMARY_DK = 0xFF6F5FB8;
        final int C_SUB = 0xFF8A84A8;
        final int C_TEXT = 0xFF3D3A45;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(22));
        card.setBackground(roundRect(0xFFFFFFFF, dp(20)));

        TextView title = new TextView(this);
        title.setText(tr("关于无损音箱", "About Lossless Speaker"));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY_DK);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(tr("电脑/手机传音 · 未压缩无损 · 双模式", "PC/Phone audio · Lossless · Dual mode"));
        desc.setTextSize(12);
        desc.setTextColor(C_SUB);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(4), 0, dp(6));
        card.addView(desc);

        int qrId = getResources().getIdentifier("reward_qrcode", "drawable", getPackageName());
        if (qrId != 0) {
            ImageView qr = new ImageView(this);
            qr.setImageResource(qrId);
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int qrSize = dp(240);
            qr.setAdjustViewBounds(true);
            card.addView(qr, new LinearLayout.LayoutParams(qrSize, qrSize));

            TextView tip = new TextView(this);
            tip.setText(tr("觉得好用,扫码请我喝杯奶茶就好;\n不喜欢也没关系,缘聚缘散。",
                    "Enjoy it? Scan the code to buy me a milk tea;\nif not, that's fine too — fate brings us together."));
            tip.setTextSize(13);
            tip.setTextColor(C_TEXT);
            tip.setGravity(Gravity.CENTER);
            tip.setLineSpacing(0, 1.3f);
            tip.setPadding(0, dp(8), 0, 0);
            card.addView(tip);
        }

        TextView author = new TextView(this);
        author.setText(tr("作者: 悟道天师\nQQ: 122001100", "Author: Wu Dao Tianshi\nQQ: 122001100"));
        author.setTextSize(13);
        author.setTextColor(C_SUB);
        author.setGravity(Gravity.CENTER);
        author.setLineSpacing(0, 1.3f);
        author.setPadding(0, dp(14), 0, 0);
        card.addView(author);

        TextView tipClose = new TextView(this);
        tipClose.setText(tr("点击任意位置关闭", "Tap anywhere to close"));
        tipClose.setTextSize(11);
        tipClose.setTextColor(C_SUB);
        tipClose.setGravity(Gravity.CENTER);
        tipClose.setPadding(0, dp(12), 0, 0);
        card.addView(tipClose);

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setView(card)
                .setOnCancelListener(null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
        dlg.show();
        dlg.setOnDismissListener(null);
    }

    private void onDiscovered(final String name, final String ip, final int port) {        for (int i = 0; i < deviceList.getChildCount(); i++) {
            Button b = (Button) deviceList.getChildAt(i);
            if (b.getTag() != null && b.getTag().toString().equals(ip)) return;
        }
        Button b = new Button(this);
        b.setText(name + "  " + ip + ":" + port);
        b.setTag(ip);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFF6F5FB8);
        b.setBackground(roundRect(0xFFFFFFFF, dp(10)));
        b.setHapticFeedbackEnabled(true);
        b.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.setBackground(roundRect(0xFFDCD3F5, dp(10)));
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.setBackground(roundRect(0xFFFFFFFF, dp(10)));
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        }
                        break;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                connect((String) v.getTag());
            }
        });
        deviceList.addView(b, lp);
    }

    private void connect(String ip) {
        SpeakerService.action(this, SpeakerService.ACTION_CONNECT, ip, false, 0f);
    }

    private void startSending() {
        if (srcMode == SpeakerService.SRC_INTERNAL) {
            if (!projectionGranted) {
                requestProjection();
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (service != null && service.isReceiving()) {
            SpeakerService.action(this, SpeakerService.ACTION_DISCONNECT, null, false, 0f);
        }
        SpeakerService.action(this, SpeakerService.ACTION_SERVER_START, null, false, 0f);
    }

    private void requestProjection() {
        if (Build.VERSION.SDK_INT < 29) {
            setStatus(tr("内置声音需要安卓10及以上", "App audio needs Android 10+"));
            return;
        }
        String fgsProjectionPermission = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION;
        if (Build.VERSION.SDK_INT >= 34 && checkSelfPermission(fgsProjectionPermission) != PackageManager.PERMISSION_GRANTED) {
            pendingProjectionRequest = true;
            requestPermissions(new String[]{fgsProjectionPermission}, REQ_FGS_PROJ);
            return;
        }
        openProjectionDialog();
    }

    private void openProjectionDialog() {
        try {
            Intent up = new Intent(this, SpeakerService.class);
            up.setAction(SpeakerService.ACTION_UPGRADE_FGS);
            startService(up);
            startActivityForResult(
                    ((android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE))
                            .createScreenCaptureIntent(),
                    REQ_PROJECTION);
        } catch (Exception e) {
            setStatus(tr("启动内置声音授权失败: ", "Failed to start App audio authorization: ") + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PROJECTION) return;
        if (resultCode == RESULT_OK && data != null) {
            projectionGranted = true;
            Intent act = new Intent(this, SpeakerService.class);
            act.setAction(SpeakerService.ACTION_SET_PROJECTION);
            act.putExtra(SpeakerService.EXTRA_INT, resultCode);
            act.putExtra(SpeakerService.EXTRA_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(act);
            else startService(act);
            setStatus(tr("内置声音已授权", "App audio authorized"));
            SpeakerService.action(this, SpeakerService.ACTION_SERVER_START, null, false, 0f);
        } else {
            setStatus(tr("未授权内置声音,发送未启动", "App audio not authorized; sending not started"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startSending();
        } else if (code == REQ_FGS_PROJ && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            openProjectionDialog();
        } else if (code == REQ_FGS_PROJ) {
            setStatus(tr("需要录屏权限才能使用内置声音", "Screen-capture permission required for App audio"));
        }
    }

    private void setStatus(String s) {
        if (statusView != null) statusView.setText(s);
    }

    private String tr(String zh, String enText) {
        return en ? enText : zh;
    }

    private void setLanguage(boolean e) {
        en = e;
        SpeakerService.EN = e;
        getSharedPreferences("wusun", MODE_PRIVATE).edit().putBoolean("en", e).apply();
        if (quoteRunnable != null) ui.removeCallbacks(quoteRunnable);
        setContentView(buildUi());
        if (service != null) {
            service.removeListener(listener);
            service.addListener(listener);
            refreshFromService();
        }
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }

    private static android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static Button makeButton(android.content.Context ctx, String text, int fg, int radius, int topPadding) {
        return makeButton(ctx, text, 0xFFFFFFFF, 0xFFDCD3F5, fg, radius, topPadding);
    }

    private static Button makeButton(android.content.Context ctx, String text, int bg, int press, int fg, int radius, int topPadding) {
        final Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTag(bg);
        b.setTextColor(fg);
        b.setBackground(roundRect(bg, radius));
        if (topPadding > 0) b.setPadding(0, topPadding, 0, topPadding);
        b.setHapticFeedbackEnabled(true);
        b.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.setBackground(roundRect(press, radius));
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.setBackground(roundRect((Integer) v.getTag(), radius));
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        }
                        break;
                }
                return false;
            }
        });
        return b;
    }
}
