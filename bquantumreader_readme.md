# bQuantumReader (b量子速读)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Chrome MV3](https://img.shields.io/badge/Chrome-MV3-blue)
![Edge](https://img.shields.io/badge/Edge-Chromium-purple)

从 B站视频提取 CC 字幕和评论，支持本地 Whisper 语音识别，生成结构化 Markdown 知识库。兼容 Chrome 与 Edge 浏览器。

> A browser extension (Chrome / Edge) to extract Bilibili subtitles and comments with local Whisper ASR support. Generates structured Markdown.

---

## 📦 浏览器扩展

兼容 Chrome 和 Edge 浏览器。

### 功能特性

- **字幕提取** — 自动获取 B站 CC 字幕，带时间戳生成 Markdown
- **评论提取** — 同步获取热门评论，集成到输出文档
- **语音识别** — 支持本地 Whisper 服务（faster-whisper），无需上传，保护隐私
- **一键启动** — 原生消息主机 + 安装向导，零配置启动 Whisper
- **后台转录** — 关闭弹窗后继续处理，随时回来查看结果
- **独立窗口** — 可脱离 Chrome 工具栏独立操作

### 下载

从 [Releases](https://github.com/iambest1-hue/bQuantumReader/releases) 下载最新版 `bQuantumReader-v*.zip`。

### 安装步骤

**Chrome：**
1. 解压下载的 zip 包
2. 地址栏输入 `chrome://extensions/` 回车
3. 打开右上角 **开发者模式**
4. 点击 **加载已解压的扩展程序**，选择解压后的文件夹

**Edge：**
1. 解压下载的 zip 包
2. 地址栏输入 `edge://extensions/` 回车
3. 打开左下角 **开发人员模式**
4. 点击 **加载解压缩的扩展**，选择解压后的文件夹

安装后打开任意 B站 视频页面，点击扩展图标即可使用。

> 语音识别服务的详细安装指南见 [INSTALL.md](INSTALL.md)。

### 语音识别

支持本地 Whisper 服务，数据无需上传，完全本地处理。

| 模型 | 大小 | 速度 | 适用场景 |
|------|------|------|---------|
| tiny | ~80MB | 最快 | 测试/低配电脑 |
| base | ~150MB | 快 | 日常使用 |
| small | ~500MB | 中等 | 默认推荐 |
| medium | ~1.5GB | 慢 | 高质量需求 |
| large-v3 | ~3GB | 最慢 | 最高精度 |

### 技术栈

- **浏览器扩展** — Manifest V3, 纯 JavaScript (兼容 Chrome / Edge)
- **Whisper 服务** — Python Flask, faster-whisper (CTranslate2)
- **通信** — Native Messaging API, HTTP REST

### 项目结构

```
bQuantumReader/
├── manifest.json              # 扩展配置 (Manifest V3)
├── background/                # Service Worker (消息路由、API 调用)
├── content/                   # 页面注入脚本 (B站视频信息提取)
├── popup/                     # 主界面 + 安装向导
│   ├── popup.html/js/css
│   └── install_wizard.html/js
├── options/                   # 设置页面
├── offscreen/                 # 后台转录文档
├── shared/                    # 公共模块
│   ├── bilibili-api.js        # B站 API 封装 (WBI 签名)
│   ├── asr.js                 # Whisper ASR 通信协议
│   └── markdown.js            # Markdown 生成器
├── help/                      # 帮助页面
├── icons/                     # 扩展图标
└── whisper_server/            # 语音识别服务 (Python/Flask)
    ├── server.py              # Flask HTTP 服务
    ├── native_host.py         # Native Messaging 主机进程
    ├── install.ps1 / .bat     # 一键安装脚本
    └── start_server.ps1 / .bat# 启动脚本
```

---

## 📱 Android App

将 bQuantumReader 的核心能力移植到 Android 平台，原生体验，无需电脑。已独立为 [bQuantumReader-Android](https://github.com/iambest1-hue/bQuantumReader-Android) 仓库。

### 功能

- **链接解析** — 粘贴 B站 视频链接，自动提取 bvid
- **视频信息展示** — 封面、标题、UP主、时长
- **字幕提取** — 调用 B站 CC 字幕 API（WBI 签名），提取带时间轴的字幕内容
- **Markdown 生成** — 自动整理为结构化 Markdown
- **结果操作** — 预览、复制、分享、保存为 .md 文件
- **评论提取** — 同步获取视频热门评论
- **B站 登录** — 扫码登录

### 下载

[**下载 APK**](https://github.com/iambest1-hue/bQuantumReader-Android/releases)
要求：Android 10.0+（API 29+）

### 技术栈

Kotlin + Jetpack Compose + Material3 / Retrofit + OkHttp / MVVM + Repository

### 更多信息

前往 [bQuantumReader-Android](https://github.com/iambest1-hue/bQuantumReader-Android) 查看完整说明、安装步骤、构建指南和项目结构。

---

## 自愿捐助

如果这个项目对你有帮助，欢迎请作者喝杯咖啡 ☕

| 微信 | 支付宝 |
|------|--------|
| ![微信收款码](assets/donate/wechat.png) | ![支付宝收款码](assets/donate/alipay.png) |

## 许可证

[MIT License](LICENSE)
