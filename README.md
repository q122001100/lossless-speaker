# 无损音箱 · Lossless Speaker

电脑 / 手机 / 网页 三端无损音频互传。

把一台设备的麦克风或应用声音，实时、无损地传输到局域网内的其他设备上播放——手机当麦克风、电脑当音箱、网页随时接入，同一局域网即可使用，无需服务器、无需公网。

## 功能

- **双模式**:手机端可一键切换「接收」/「发送」
- **三端互通**:PC(Windows)、Android、网页浏览器 之间任意互传
- **双音源**:麦克风 / 内置声音(应用播放的音频)都能作为音源
- **无损传输**:PCM 原始采样流,无重压缩
- **自动发现**:局域网广播(UDP)自动发现设备,无需手动填 IP
- **音量控制**:接收端可调音量,网页端同样支持
- **中英双语**:Android 与网页界面支持 中 / EN 切换
- **跨端同步**:内置 25 条鲁迅名言,三端实时轮播同步

## 快速开始

### PC 端(Windows)
运行 `无损音箱.exe`(需 .NET Framework + NAudio),从下拉框选择音源,点击「开始」即可。

### Android
构建安装 `android/build/speaker.apk`:
```
cd android
powershell -ExecutionPolicy Bypass -File build.ps1
```
打开 App 后选择「接收」或「发送」,自动发现连接同一 Wi-Fi 内的设备。

### 网页端
浏览器打开 PC 端启动后显示的地址(默认 `http://<PC的IP>:8600/`),可作为远程遥控/接收播放。

## 架构

```
┌──────────┐  PCM  ┌──────────┐  PCM  ┌──────────┐
│ 手机发送端 │ ────▶ │ 桌面 Server │ ────▶ │ 手机接收端 │
│ (AudioRecord) │      │ (NAudio) │      │ (AudioTrack) │
└──────────┘       └─────┬────┘      └──────────┘
                         │ HTTP :8600 + WebSocket
                         ▼
                     ┌──────────┐
                     │ 网页端    │
                     └──────────┘
```

- **发现**:UDP 广播 `WSSPEAKER|名称|优先IP|8600`
- **传输**:TCP + WebSocket,负载为 16bit/48kHz 的 PCM
- **Server.cs**:C# + NAudio,负责调度、转发与网页托管
- **android/src/com/wusun/speaker/**:Android 端源码(纯 Java + AudioRecord/AudioTrack,无第三方 SDK)
- **page.html**:网页端(单文件,无构建)

## 构建部署

| 端 | 方式 |
|---|---|
| PC | `Server.cs` 用 .NET(winforms) 编译,需 `NAudio.dll` |
| Android | `android/build.ps1`(aapt2/d8/apksigner 脚本) |
| 网页 | 直接由 PC 端内置服务托管 `page.html` |

## 免责声明

本项目仅供学习交流使用,请勿用于非法用途。传播或使用请遵守当地法律法规。

## 特别鸣谢

- [NAudio](https://github.com/naudio/NAudio) — .NET 音频收集