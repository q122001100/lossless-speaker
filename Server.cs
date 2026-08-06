using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using NAudio.Wave;
using NAudio.CoreAudioApi;

namespace WuSunSpeaker
{
    public static class Program
    {
        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public class MainForm : Form
    {
        private readonly Label _status;
        private readonly TextBox _urlBox;
        private readonly Button _startBtn;
        private readonly Button _stopBtn;
        private readonly ListBox _log;
        private readonly Label _bottom;
        private readonly System.Windows.Forms.Timer _uiTimer;
        private readonly ComboBox _sourceBox;
        private SpeakerServer _server;
        private readonly Label _quoteLabel;
        private int _quoteIndex = 0;
        private readonly string[] _quotes = new string[] {
            "其实地上本没有路,走的人多了,也便成了路。—— 鲁迅",
            "愿中国青年都摆脱冷气,只是向上走,不必听自暴自弃者流的话。—— 鲁迅",
            "不在沉默中爆发,就在沉默中灭亡。—— 鲁迅",
            "我们自古以来,就有埋头苦干的人,有拼命硬干的人,有为民请命的人,有舍身求法的人,这就是中国的脊梁。—— 鲁迅",
            "横眉冷对千夫指,俯首甘为孺子牛。—— 鲁迅",
            "真的勇士,敢于直面惨淡的人生,敢于正视淋漓的鲜血。—— 鲁迅",
            "哪里有天才,我是把别人喝咖啡的工夫都用在工作上的。—— 鲁迅",
            "时间就像海绵里的水,只要愿挤,总还是有的。—— 鲁迅",
            "哀其不幸,怒其不争。—— 鲁迅",
            "愿所有人都能摆脱冷气和黑暗,向上走。—— 鲁迅",
            "希望是附丽于存在的,有存在,便有希望,有希望,便是光明。—— 鲁迅",
            "巨大的建筑,总是由一木一石叠起来的,我们何妨做做这一木一石呢。—— 鲁迅",
            "人生最苦痛的是梦醒了无路可以走。—— 鲁迅",
            "无穷的远方,无数的人们,都和我有关。—— 鲁迅",
            "从来如此,便对么?—— 鲁迅",
            "有缺点的战士终竟是战士,完美的苍蝇也终竟不过是苍蝇。—— 鲁迅",
            "无情未必真豪杰,怜子如何不丈夫。—— 鲁迅",
            "度尽劫波兄弟在,相逢一笑泯恩仇。—— 鲁迅",
            "我好像是一只牛,吃的是草,挤出的是奶、血。—— 鲁迅",
            "人既发扬踔厉矣,则邦国亦以兴起。—— 鲁迅",
            "空谈之类,是谈不久,也谈不出什么来的,它终必被事实的镜子照出原形。—— 鲁迅",
            "巨大的建筑,总是由一木一石叠起来的。—— 鲁迅",
            "只要能培一朵花,就不妨做做会朽的腐草。—— 鲁迅",
            "做一件事,无论大小,倘无恒心,是很不好的。—— 鲁迅",
            "人类的悲欢并不相通,我只觉得他们吵闹。—— 鲁迅"
        };
        private static string ConfigPath
        {
            get { return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "无损音箱_配置.ini"); }
        }

        public MainForm()
        {
            Text = "无损音箱 - PC 端";
            ClientSize = new Size(468, 430);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            Font = new Font("Microsoft YaHei UI", 9F);
            BackColor = Color.FromArgb(247, 243, 251);

            Color cPrimary = Color.FromArgb(139, 123, 216);
            Color cPrimaryDk = Color.FromArgb(111, 95, 184);
            Color cAccent = Color.FromArgb(232, 160, 191);
            Color cText = Color.FromArgb(61, 58, 69);
            Color cSub = Color.FromArgb(138, 132, 168);
            Color cLine = Color.FromArgb(228, 223, 242);

            var title = new Label
            {
                Text = "无损音箱 · PC 端",
                Location = new Point(12, 10),
                AutoSize = true,
                Font = new Font("Microsoft YaHei UI", 13F, FontStyle.Bold),
                ForeColor = cPrimaryDk
            };
            var subTitle = new Label
            {
                Text = "电脑/手机传音 · 未压缩无损 · 双模式",
                Location = new Point(13, 38),
                AutoSize = true,
                Font = new Font("Microsoft YaHei UI", 8.5F),
                ForeColor = cSub
            };

            var lbl = new Label { Text = "运行状态:", Location = new Point(12, 70), AutoSize = true, ForeColor = cText };
            _status = new Label { Text = "未启动", Location = new Point(74, 70), AutoSize = true, ForeColor = cSub };

            var lblUrl = new Label { Text = "手机访问地址(同一 WiFi 或 USB 网络共享):", Location = new Point(12, 94), AutoSize = true, ForeColor = cText };
            _urlBox = new TextBox
            {
                ReadOnly = true,
                Location = new Point(12, 116),
                Width = 444,
                Font = new Font("Consolas", 11F),
                BackColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle
            };

            _startBtn = new Button { Text = "启动服务", Location = new Point(12, 150), Width = 104, Height = 30 };
            _stopBtn = new Button { Text = "停止服务", Location = new Point(124, 150), Width = 104, Height = 30, Enabled = false };
            _startBtn.Click += (s, e) => StartServer();
            _stopBtn.Click += (s, e) => StopServer();

            var lblSrc = new Label { Text = "音源:", Location = new Point(246, 154), AutoSize = true, ForeColor = cText };
            _sourceBox = new ComboBox
            {
                Location = new Point(286, 150),
                Width = 170,
                Height = 28,
                DropDownStyle = ComboBoxStyle.DropDownList,
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.White
            };
            _sourceBox.Items.AddRange(new object[] { "系统声音", "电脑麦克风" });
            _sourceBox.SelectedIndex = LoadSource() ? 1 : 0;
            _sourceBox.SelectedIndexChanged += (s, e) =>
            {
                SaveSource(_sourceBox.SelectedIndex == 1);
                Log("音源已切换为: " + _sourceBox.SelectedItem);
                if (_server != null)
                {
                    StopServer();
                    StartServer();
                }
            };

            _log = new ListBox
            {
                Location = new Point(12, 192),
                Width = 444,
                Height = 118,
                BorderStyle = BorderStyle.FixedSingle,
                HorizontalScrollbar = true,
                BackColor = Color.White,
                ForeColor = cText
            };

            _bottom = new Label { Location = new Point(12, 318), AutoSize = true, ForeColor = cSub };

            _quoteLabel = new Label
            {
                Location = new Point(12, 338),
                AutoSize = false,
                Size = new Size(444, 22),
                ForeColor = cSub,
                Font = new Font("Microsoft YaHei UI", 9F),
                TextAlign = ContentAlignment.MiddleLeft
            };
            _quoteLabel.Text = _quotes[0];

            var aboutBtn = new Button
            {
                Text = "关于",
                Location = new Point(12, 362),
                Width = 444,
                Height = 32,
                FlatStyle = FlatStyle.Flat
            };
            aboutBtn.FlatAppearance.BorderSize = 0;
            aboutBtn.BackColor = Color.FromArgb(243, 240, 250);
            aboutBtn.ForeColor = cPrimaryDk;
            aboutBtn.Click += (s, e) => ShowAboutDialog();

            Controls.AddRange(new Control[] { title, subTitle, lbl, _status, lblUrl, _urlBox, _startBtn, _stopBtn, lblSrc, _sourceBox, _log, _bottom, _quoteLabel, aboutBtn });

            StyleButton(_startBtn, cPrimary, Color.White);
            StyleButton(_stopBtn, Color.FromArgb(243, 240, 250), cPrimaryDk);

            _uiTimer = new System.Windows.Forms.Timer { Interval = 600 };
            _uiTimer.Tick += (s, e) => UpdateBottom();
            _uiTimer.Start();

            var quoteTimer = new System.Windows.Forms.Timer { Interval = 3000 };
            quoteTimer.Tick += (s, e) =>
            {
                _quoteIndex = (_quoteIndex + 1) % _quotes.Length;
                _quoteLabel.Text = _quotes[_quoteIndex];
            };
            quoteTimer.Start();

            FormClosed += (s, e) => { _uiTimer.Stop(); StopServer(); };
            Shown += (s, e) =>
            {
                _urlBox.Text = MakeUrl();
                if (_urlBox.Text == "") { _urlBox.Text = "(未检测到网卡地址)"; }
                StartServer();
            };
        }

        private static void StyleButton(Button b, Color bg, Color fg)
        {
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 0;
            b.BackColor = bg;
            b.ForeColor = fg;
        }

        private void ShowAboutDialog()
        {
            Color cPrimaryDk = Color.FromArgb(111, 95, 184);
            Color cSub = Color.FromArgb(138, 132, 168);
            Color cText = Color.FromArgb(61, 58, 69);

            var card = new Panel
            {
                Size = new Size(320, 430),
                BackColor = Color.FromArgb(247, 243, 251),
                Padding = new Padding(24)
            };

            var title = new Label
            {
                Text = "关于无损音箱",
                AutoSize = true,
                Font = new Font("Microsoft YaHei UI", 15F, FontStyle.Bold),
                ForeColor = cPrimaryDk,
                Location = new Point(0, 0)
            };

            var desc = new Label
            {
                Text = "电脑/手机传音 · 未压缩无损 · 双模式",
                AutoSize = true,
                ForeColor = cSub,
                Font = new Font("Microsoft YaHei UI", 9F),
                Location = new Point(1, 34)
            };

            var qr = new PictureBox
            {
                Size = new Size(220, 220),
                Location = new Point(38, 62),
                SizeMode = PictureBoxSizeMode.Zoom,
                BackColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle
            };
            LoadQrImage(qr);

            var tip = new Label
            {
                Text = "觉得好用,扫码请我喝杯奶茶就好;\n不喜欢也没关系,缘聚缘散。",
                AutoSize = true,
                ForeColor = cText,
                Font = new Font("Microsoft YaHei UI", 10F),
                Location = new Point(18, 296),
                TextAlign = ContentAlignment.MiddleCenter
            };

            var author = new Label
            {
                Text = "作者: 悟问天道   QQ: 122001100",
                AutoSize = true,
                ForeColor = cSub,
                Font = new Font("Microsoft YaHei UI", 9F),
                Location = new Point(50, 340)
            };

            card.Controls.AddRange(new Control[] { title, desc, qr, tip, author });

            var dlg = new Form
            {
                Text = "关于",
                FormBorderStyle = FormBorderStyle.FixedDialog,
                MaximizeBox = false,
                MinimizeBox = false,
                StartPosition = FormStartPosition.CenterParent,
                ClientSize = new Size(336, 396),
                BackColor = Color.FromArgb(247, 243, 251),
                ShowInTaskbar = false
            };
            card.Location = new Point(8, 8);
            dlg.Controls.Add(card);
            dlg.ShowDialog(this);
        }

        private static void LoadQrImage(PictureBox box)
        {
            try
            {
                string qrPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "reward_qrcode.png");
                if (File.Exists(qrPath))
                {
                    using (var fs = new FileStream(qrPath, FileMode.Open, FileAccess.Read))
                    {
                        box.Image = Image.FromStream(fs);
                    }
                }
            }
            catch { }
        }

        private static string MakeUrl()
        {
            var ips = SpeakerServer.GetLocalIPs();
            if (ips.Length == 0) return "";
            foreach (var ip in ips)
            {
                if (!ip.StartsWith("169.254")) return "http://" + ip + ":" + SpeakerServer.Port;
            }
            return "http://" + ips[0] + ":" + SpeakerServer.Port;
        }

        private static bool LoadSource()
        {
            try
            {
                if (!File.Exists(ConfigPath)) return false;
                string[] lines = File.ReadAllLines(ConfigPath, Encoding.UTF8);
                foreach (string l in lines)
                {
                    if (l.Trim().Equals("source=microphone", StringComparison.OrdinalIgnoreCase)) return true;
                }
            }
            catch { }
            return false;
        }

        private static void SaveSource(bool mic)
        {
            try
            {
                File.WriteAllText(ConfigPath, "[config]\r\nsource=" + (mic ? "microphone" : "system") + "\r\n", Encoding.UTF8);
            }
            catch { }
        }

        private void StartServer()
        {
            try
            {
                _server = new SpeakerServer(Log) { UseMic = _sourceBox.SelectedIndex == 1 };
                _server.Start();
                _status.Text = "运行中";
                _status.ForeColor = Color.FromArgb(111, 95, 184);
                _startBtn.Enabled = false;
                _stopBtn.Enabled = true;
                var ips = SpeakerServer.GetLocalIPs();
                Log("服务已启动,端口 " + SpeakerServer.Port);
                if (ips.Length == 0)
                {
                    Log("警告:未检测到局域网地址,手机可能无法连接");
                }
                foreach (var ip in ips)
                {
                    Log("手机打开: http://" + ip + ":" + SpeakerServer.Port);
                }
            }
            catch (Exception ex)
            {
                Log("启动失败: " + ex.Message);
            }
        }

        private void StopServer()
        {
            if (_server == null) return;
            try { _server.Stop(); } catch { }
            _server = null;
            _status.Text = "未启动";
            _status.ForeColor = Color.Gray;
            _startBtn.Enabled = true;
            _stopBtn.Enabled = false;
        }

        private void UpdateBottom()
        {
            if (_server != null)
            {
                _bottom.Text = "客户端: " + _server.ClientCount + " | 采样率: " + _server.SampleRate + " Hz | 声道: " + _server.Channels;
            }
            else
            {
                _bottom.Text = "客户端: 0";
            }
        }

        private void Log(string msg)
        {
            try
            {
                System.IO.File.AppendAllText(@"C:\Users\问天道\AppData\Local\Temp\opencode\pclog.txt",
                    "[" + DateTime.Now.ToString("HH:mm:ss.fff") + "] " + msg + "\r\n");
            }
            catch { }
            if (IsDisposed || Disposing) return;
            try
            {
                bool isHighFreq = msg.StartsWith("sent bin") || msg.StartsWith("recv bin");
                if (isHighFreq) return;
                BeginInvoke(new Action(() =>
                {
                    if (IsDisposed || Disposing) return;
                    _log.Items.Add("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + msg);
                    while (_log.Items.Count > 200) _log.Items.RemoveAt(0);
                    _log.TopIndex = _log.Items.Count - 1;
                }));
            }
            catch { }
        }
    }

    public class SpeakerServer
    {
        public const int Port = 8600;
        private readonly Action<string> _log;
        private TcpListener _listener;
        private IWaveIn _capture;
        private readonly object _clientsLock = new object();
        private readonly List<ClientSession> _clients = new List<ClientSession>();
        private volatile bool _running;
        private int _sampleRate;
        private int _channels;
        private bool _pcm16;
        private readonly object _aggLock = new object();
        private byte[] _pending = new byte[0];
        private const int AggBytes = 5120;
        private WaveOutEvent _phoneOut;
        private BufferedWaveProvider _phoneBuf;
        private readonly object _phoneLock = new object();

        private int _dbgRecv;
        private readonly object _phoneActiveLock = new object();

        public SpeakerServer(Action<string> log) { _log = log; }

        public bool UseMic { get; set; }

        public int SampleRate { get { return _sampleRate; } }
        public int Channels { get { return _channels; } }

        public int ClientCount
        {
            get { lock (_clientsLock) return _clients.Count; }
        }

        public static string[] GetLocalIPs()
        {
            var list = new List<string>();
            try
            {
                foreach (var ip in Dns.GetHostAddresses(Dns.GetHostName()))
                {
                    if (ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))
                    {
                        list.Add(ip.ToString());
                    }
                }
            }
            catch { }
            return list.ToArray();
        }

        public void Start()
        {
            if (_running) return;
            try
            {
                if (UseMic)
                {
                    var wasapi = new WasapiCapture();
                    _capture = wasapi;
                    _pcm16 = wasapi.WaveFormat.Encoding == WaveFormatEncoding.Pcm && wasapi.WaveFormat.BitsPerSample == 16;
                    _log("音源: 电脑麦克风 " + wasapi.WaveFormat.SampleRate + "Hz/" + wasapi.WaveFormat.Channels + "ch/" + wasapi.WaveFormat.BitsPerSample + "bit");
                }
                else
                {
                    var loop = new WasapiLoopbackCapture();
                    _capture = loop;
                    _pcm16 = false;
                    _log("音源: 系统声音 " + loop.WaveFormat.SampleRate + "Hz/" + loop.WaveFormat.Channels + "ch");
                }
                _capture.DataAvailable += OnData;
                _capture.StartRecording();
            }
            catch (Exception ex)
            {
                throw new Exception("无法启动音频采集(" + (UseMic ? "请确认电脑有麦克风" : "请确认电脑有输出设备") + "): " + ex.Message);
            }
            _sampleRate = _capture.WaveFormat.SampleRate;
            _channels = _capture.WaveFormat.Channels;
            _running = true;
            _listener = new TcpListener(IPAddress.Any, Port);
            _listener.Start();
            var t = new Thread(AcceptLoop) { IsBackground = true };
            t.Start();
            StartDiscovery();
        }

        private UdpClient _udp;

        private void StartDiscovery()
        {
            try
            {
                var ips = GetLocalIPs();
                string preferred = "";
                foreach (var ip in ips)
                {
                    if (!ip.StartsWith("169.254")) { preferred = ip; break; }
                }
                if (preferred == "" && ips.Length > 0) preferred = ips[0];
                if (preferred == "") return;
                _udp = new UdpClient();
                _udp.EnableBroadcast = true;
                var payload = Encoding.UTF8.GetBytes("WSSPEAKER|无损音箱|" + preferred + "|" + Port);
                var ep = new IPEndPoint(IPAddress.Broadcast, 8610);
                var udpThread = new Thread(() =>
                {
                    while (_running)
                    {
                        try { _udp.Send(payload, payload.Length, ep); } catch { }
                        Thread.Sleep(2000);
                    }
                }) { IsBackground = true };
                udpThread.Start();
                _log("已开启自动发现,手机 App 可自动搜索到本机");
            }
            catch { }
        }

        public void Stop()
        {
            _running = false;
            try { _listener.Stop(); } catch { }
            try { _udp.Close(); } catch { }
            try { if (_capture != null) { _capture.DataAvailable -= OnData; _capture.StopRecording(); } } catch { }
            try { if (_capture != null) _capture.Dispose(); } catch { }
            List<ClientSession> copy;
            lock (_clientsLock) { copy = new List<ClientSession>(_clients); _clients.Clear(); }
            foreach (var c in copy)
            {
                c.Dead = true;
                try { c.Tcp.Close(); } catch { }
            }
            _log("服务已停止");
            try { if (_phoneOut != null) { _phoneOut.Stop(); _phoneOut.Dispose(); } } catch { }
            _phoneOut = null;
            _phoneBuf = null;
        }

        private void AcceptLoop()
        {
            while (_running)
            {
                TcpClient tcp = null;
                try { tcp = _listener.AcceptTcpClient(); }
                catch { break; }
                var c = new ClientSession(tcp);
                lock (_clientsLock) _clients.Add(c);
                var th = new Thread(() => HandleClient(c)) { IsBackground = true };
                th.Start();
            }
        }

        private void HandleClient(ClientSession c)
        {
            try
            {
                var s = c.Stream;
                string requestLine = ReadLine(s);
                if (requestLine == null) { MarkDead(c, false); return; }
                bool httpGet = requestLine.StartsWith("GET ", StringComparison.Ordinal);
                string secKey = null;
                while (true)
                {
                    string line = ReadLine(s);
                    if (line == null || line.Length == 0) break;
                    if (line.StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
                    {
                        secKey = line.Substring(line.IndexOf(':') + 1).Trim();
                    }
                }
                if (httpGet && secKey == null) { ServePage(c, requestLine); return; }
                if (secKey == null) { MarkDead(c, false); return; }

                string accept = Convert.ToBase64String(
                    SHA1.Create().ComputeHash(Encoding.ASCII.GetBytes(secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")));
                byte[] resp = Encoding.ASCII.GetBytes(
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n");
                s.Write(resp, 0, resp.Length);
                s.Flush();

                c.RemoteIP = c.Tcp.Client.RemoteEndPoint.ToString();
                _log("手机已连接: " + c.RemoteIP);
                SendTextFrame(c, "{\"sr\":" + _sampleRate + ",\"ch\":" + _channels + "}");
                var sender = new Thread(() => SenderLoop(c)) { IsBackground = true };
                sender.Start();
                ReceiverLoop(c);
            }
            catch (Exception ex)
            {
                _log("handle异常: " + ex.Message);
                MarkDead(c);
            }
        }

        private void ServePage(ClientSession c, string requestLine)
        {
            try
            {
                string reqPath = "/";
                int sp = requestLine.IndexOf(' ');
                if (sp > 0)
                {
                    string p = requestLine.Substring(sp + 1);
                    int sp2 = p.IndexOf(' ');
                    if (sp2 > 0) p = p.Substring(0, sp2);
                    reqPath = p;
                }
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                if (reqPath == "/reward_qrcode.png")
                {
                    string imgPath = Path.Combine(baseDir, "reward_qrcode.png");
                    if (File.Exists(imgPath))
                    {
                        byte[] img = File.ReadAllBytes(imgPath);
                        string imgHead = "HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: image/png\r\n" +
                                         "Content-Length: " + img.Length + "\r\n" +
                                         "Cache-Control: no-store\r\n" +
                                         "Connection: close\r\n\r\n";
                        byte[] imgHeadBytes = Encoding.ASCII.GetBytes(imgHead);
                        c.Stream.Write(imgHeadBytes, 0, imgHeadBytes.Length);
                        c.Stream.Write(img, 0, img.Length);
                        c.Stream.Flush();
                        MarkDead(c, false);
                        return;
                    }
                }
            }
            catch { }

            string html = null;
            try
            {
                string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "page.html");
                if (File.Exists(path)) html = File.ReadAllText(path, Encoding.UTF8);
            }
            catch { }
            if (html == null) html = "<meta charset=\"utf-8\"><h2>缺少 page.html 文件,请重新解压软件</h2>";
            byte[] body = Encoding.UTF8.GetBytes(html);
            string head = "HTTP/1.1 200 OK\r\n" +
                          "Content-Type: text/html; charset=utf-8\r\n" +
                          "Content-Length: " + body.Length + "\r\n" +
                          "Cache-Control: no-store\r\n" +
                          "Connection: close\r\n\r\n";
            byte[] headBytes = Encoding.ASCII.GetBytes(head);
            try
            {
                c.Stream.Write(headBytes, 0, headBytes.Length);
                c.Stream.Write(body, 0, body.Length);
                c.Stream.Flush();
            }
            catch { }
            MarkDead(c, false);
        }

        private void SenderLoop(ClientSession c)
        {
            DateTime lastPing = DateTime.UtcNow;
            try
            {
                while (!c.Dead)
                {
                    if (!c.Wake.Wait(500))
                    {
                        if ((DateTime.UtcNow - lastPing).TotalSeconds >= 5)
                        {
                            SendPingFrame(c);
                            _log("sent ping");
                            lastPing = DateTime.UtcNow;
                        }
                        continue;
                    }
                    c.Wake.Reset();
                    byte[] data = null;
                    lock (c.SendLock)
                    {
                        if (c.Pending.Count > 0) data = c.Pending.Dequeue();
                    }
                    if (data == null) continue;
                    WriteBinaryFrame(c, data);
                    _log("sent bin len=" + data.Length);
                    lastPing = DateTime.UtcNow;
                }
            }
            catch (Exception ex)
            {
                _log("发送异常: " + ex.Message);
                MarkDead(c);
            }
        }

        private static void SendPingFrame(ClientSession c)
        {
            byte[] frame = new byte[] { 0x89, 0x00 };
            c.Stream.Write(frame, 0, 2);
            c.Stream.Flush();
        }

        private void ReceiverLoop(ClientSession c)
        {
            var s = c.Stream;
            int frames = 0;
            try
            {
                while (!c.Dead)
                {
                    int b0 = s.ReadByte();
                    if (b0 < 0) break;
                    if (frames < 12) { _log("F" + frames + " b0=" + b0.ToString("X2")); frames++; }
                    int b1 = s.ReadByte();
                    if (b1 < 0) break;
                    int op = b0 & 0x0F;
                    long len = b1 & 0x7F;
                    if (len == 126)
                    {
                        int hi = s.ReadByte();
                        int lo = s.ReadByte();
                        if (hi < 0 || lo < 0) break;
                        len = (hi << 8) | lo;
                    }
                    else if (len == 127)
                    {
                        var l8 = new byte[8];
                        if (!ReadExact(s, l8)) break;
                        len = BitConverter.ToInt64(l8, 0);
                    }
                    bool masked = (b1 & 0x80) != 0;
                    byte[] mask = null;
                    if (masked)
                    {
                        mask = new byte[4];
                        if (!ReadExact(s, mask)) break;
                    }
                    if (len > 1048576) break;
                    byte[] payload = null;
                    if (len > 0)
                    {
                        payload = new byte[len];
                        if (!ReadExact(s, payload)) break;
                        if (mask != null)
                        {
                            for (int i = 0; i < payload.Length; i++) payload[i] ^= mask[i & 3];
                        }
                    }
                    if (op == 0x9)
                    {
                        var pong = new byte[2 + payload.Length];
                        pong[0] = 0x8A;
                        pong[1] = (byte)payload.Length;
                        Array.Copy(payload, 0, pong, 2, payload.Length);
                        lock (c.SendLock)
                        {
                            if (c.Pending.Count > 300) c.Pending.Clear();
                            c.Pending.Enqueue(pong);
                        }
                        c.Wake.Set();
                    }
                    else if (op == 0x1)
                    {
                        if (payload == null || payload.Length == 0) { _log("收到空文本帧"); continue; }
                        string txt = Encoding.UTF8.GetString(payload);
                        int sr, ch;
                        ParseFormat(txt, out sr, out ch);
                        EnsureRenderer(sr, ch);
                    }
                    else if (op == 0x2)
                    {
                        if (payload != null && payload.Length > 0)
                        {
                            if (_dbgRecv++ % 200 == 0) _log("recv bin len=" + payload.Length);
                            FeedPhoneAudio(c, payload);
                        }
                    }
                    else if (op == 0x8)
                    {
                        _log("recv close frame from phone");
                        try
                        {
                            s.Write(new byte[] { 0x88, 0x00 }, 0, 2);
                            s.Flush();
                        }
                        catch { }
                        break;
                    }
                    else
                    {
                        _log("recv op=" + op + " len=" + len + " b0=" + b0 + " b1=" + b1);
                    }
                }
            }
            catch (Exception ex)
            {
                _log("接收异常: " + ex.Message + " | " + ex.StackTrace);
            }
            MarkDead(c);
        }

        private static void WriteBinaryFrame(ClientSession c, byte[] data)
        {
            var s = c.Stream;
            if (data.Length <= 125)
            {
                var head = new byte[2];
                head[0] = 0x82;
                head[1] = (byte)data.Length;
                s.Write(head, 0, 2);
            }
            else if (data.Length <= 65535)
            {
                var head = new byte[4];
                head[0] = 0x82;
                head[1] = 126;
                head[2] = (byte)(data.Length >> 8);
                head[3] = (byte)(data.Length & 0xFF);
                s.Write(head, 0, 4);
            }
            else
            {
                var head = new byte[10];
                head[0] = 0x82;
                head[1] = 127;
                ulong l = (ulong)data.Length;
                for (int i = 0; i < 8; i++) head[2 + i] = (byte)(l >> (56 - i * 8));
                s.Write(head, 0, 10);
            }
            s.Write(data, 0, data.Length);
            s.Flush();
        }

        private static void SendTextFrame(ClientSession c, string text)
        {
            byte[] payload = Encoding.UTF8.GetBytes(text);
            var head = new byte[2];
            head[0] = 0x81;
            head[1] = (byte)payload.Length;
            c.Stream.Write(head, 0, 2);
            c.Stream.Write(payload, 0, payload.Length);
            c.Stream.Flush();
        }

        private void OnData(object sender, WaveInEventArgs e)
        {
            int samples;
            lock (_clientsLock)
            {
                if (_clients.Count == 0) return;
            }
            byte[] pcm;
            if (_pcm16)
            {
                samples = e.BytesRecorded / 2;
                if (samples <= 0) return;
                pcm = new byte[samples * 2];
                Buffer.BlockCopy(e.Buffer, 0, pcm, 0, samples * 2);
            }
            else
            {
                samples = e.BytesRecorded / 4;
                if (samples <= 0) return;
                pcm = new byte[samples * 2];
                for (int i = 0; i < samples; i++)
                {
                    float f = BitConverter.ToSingle(e.Buffer, i * 4);
                    if (f > 1f) f = 1f;
                    else if (f < -1f) f = -1f;
                    short v = (short)(f * 32767f);
                    pcm[i * 2] = (byte)v;
                    pcm[i * 2 + 1] = (byte)((ushort)v >> 8);
                }
            }
            lock (_aggLock)
            {
                if (_pending.Length + pcm.Length >= AggBytes)
                {
                    byte[] outBytes = new byte[_pending.Length + pcm.Length];
                    Buffer.BlockCopy(_pending, 0, outBytes, 0, _pending.Length);
                    Buffer.BlockCopy(pcm, 0, outBytes, _pending.Length, pcm.Length);
                    _pending = new byte[0];
                    Broadcast(outBytes);
                }
                else
                {
                    byte[] tmp = new byte[_pending.Length + pcm.Length];
                    Buffer.BlockCopy(_pending, 0, tmp, 0, _pending.Length);
                    Buffer.BlockCopy(pcm, 0, tmp, _pending.Length, pcm.Length);
                    _pending = tmp;
                }
            }
        }

        private void Broadcast(byte[] data)
        {
            int now = Environment.TickCount;
            ClientSession[] arr;
            lock (_clientsLock)
            {
                if (_clients.Count == 0) return;
                arr = _clients.ToArray();
            }
            foreach (var c in arr)
            {
                if (c.Dead) continue;
                lock (c.SendLock)
                {
                    if (c.Pending.Count > 300) c.Pending.Clear();
                    c.Pending.Enqueue(data);
                }
                c.Wake.Set();
            }
        }

        private void MarkDead(ClientSession c, bool log = true)
        {
            if (c.Dead) return;
            c.Dead = true;
            try { c.Tcp.Close(); } catch { }
            lock (_clientsLock) _clients.Remove(c);
            if (log && c.RemoteIP != null) _log("手机已断开: " + c.RemoteIP);
        }

        private void EnsureRenderer(int sr, int ch)
        {
            lock (_phoneLock)
            {
                if (ch < 1) ch = 1;
                if (sr < 8000) sr = 48000;
                if (_phoneBuf != null &&
                    _phoneBuf.WaveFormat.SampleRate == sr &&
                    _phoneBuf.WaveFormat.Channels == ch) return;
                try { if (_phoneOut != null) { _phoneOut.Stop(); _phoneOut.Dispose(); } } catch { }
                try
                {
                    _phoneBuf = new BufferedWaveProvider(new WaveFormat(sr, 16, ch));
                    _phoneBuf.DiscardOnBufferOverflow = true;
                    _phoneBuf.BufferDuration = TimeSpan.FromMilliseconds(80);
                    _phoneOut = new WaveOutEvent();
                    _phoneOut.Init(_phoneBuf);
                    _phoneOut.Play();
                    _log("播放手机传来的声音 " + sr + "Hz/" + ch + "ch");
                }
                catch (Exception ex)
                {
                    _log("手机声音播放失败: " + ex.Message);
                }
            }
        }

        private void FeedPhoneAudio(ClientSession c, byte[] pcm)
        {
            if (c != null) c.LastAudioMs = Environment.TickCount;
            lock (_phoneLock)
            {
                if (_phoneBuf == null) EnsureRenderer(48000, 2);
                if (_phoneBuf != null)
                {
                    if (_phoneBuf.BufferedBytes + pcm.Length > _phoneBuf.BufferLength)
                        _phoneBuf.ClearBuffer();
                    _phoneBuf.AddSamples(pcm, 0, pcm.Length);
                }
            }
        }

        private static void ParseFormat(string text, out int sr, out int ch)
        {
            sr = 48000; ch = 2;
            try
            {
                foreach (var part in text.Replace("{", "").Replace("}", "").Split(','))
                {
                    var kv = part.Split(':');
                    if (kv.Length != 2) continue;
                    var k = kv[0].Trim().Trim('"');
                    var v = kv[1].Trim();
                    if (k == "sr") sr = int.Parse(v);
                    else if (k == "ch") ch = int.Parse(v);
                }
            }
            catch { }
        }

        private static bool ReadExact(NetworkStream s, byte[] buf)
        {
            int off = 0;
            while (off < buf.Length)
            {
                int n = s.Read(buf, off, buf.Length - off);
                if (n <= 0) return false;
                off += n;
            }
            return true;
        }

        private static string ReadLine(NetworkStream s)
        {
            var sb = new StringBuilder();
            bool eof = false;
            while (true)
            {
                int b = s.ReadByte();
                if (b < 0) { eof = true; break; }
                if (b == 10) break;
                if (b == 13)
                {
                    int b2 = s.ReadByte();
                    if (b2 == 10 || b2 < 0) break;
                    sb.Append((char)b2);
                }
                else sb.Append((char)b);
                if (sb.Length > 8192) break;
            }
            if (sb.Length == 0 && eof) return null;
            return sb.ToString();
        }

        private class ClientSession
        {
            public readonly TcpClient Tcp;
            public readonly NetworkStream Stream;
            public readonly object SendLock = new object();
            public readonly Queue<byte[]> Pending = new Queue<byte[]>();
            public readonly ManualResetEventSlim Wake = new ManualResetEventSlim(false);
            public volatile bool Dead;
            public string RemoteIP;
            public volatile int LastAudioMs;

            public ClientSession(TcpClient tcp)
            {
                Tcp = tcp;
                tcp.NoDelay = true;
                try
                {
                    tcp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.KeepAlive, true);
                    tcp.Client.ReceiveBufferSize = 16384;
                    tcp.Client.SendBufferSize = 32768;
                }
                catch { }
                Stream = tcp.GetStream();
                Stream.ReadTimeout = 180000;
                Stream.WriteTimeout = 5000;
            }
        }
    }
}
