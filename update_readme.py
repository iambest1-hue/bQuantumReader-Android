import base64, sys

with open('/tmp/README_orig.md', 'r', encoding='utf-8') as f:
    content = f.read()

old = '## 📱 Android App（开发中）\n\n将 bQuantumReader 的核心能力移植到 Android 平台，原生体验，无需电脑。\n\n### 规划功能\n\n- **字幕提取** — 解析 B站视频页，获取 CC 字幕并生成 Markdown\n- **评论提取** — 获取视频热门评论\n- **语音识别** — 通过 HTTP 调用电脑端 Whisper 服务进行转写\n- **本地存储** — 导出 Markdown 文件到设备本地\n\n### 技术栈\n\n- **语言/UI** — Kotlin + Jetpack Compose\n- **网络** — Retrofit + OkHttp\n- **架构** — MVVM + Repository Pattern\n\n### 状态\n\n目前处于规划阶段，欢迎参与开发。开发计划与算法参考见 [`archive/android-plan`](https://github.com/iambest1-hue/bQuantumReader/tree/archive/android-plan) 分支。'

new = '## 📱 Android App\n\n将 bQuantumReader 的核心能力移植到 Android 平台，原生体验，无需电脑。已独立为 [bQuantumReader-Android](https://github.com/iambest1-hue/bQuantumReader-Android) 仓库。\n\n### 功能\n\n- **链接解析** — 粘贴 B站 视频链接，自动提取 bvid\n- **视频信息展示** — 封面、标题、UP主、时长\n- **字幕提取** — 调用 B站 CC 字幕 API（WBI 签名），提取带时间轴的字幕内容\n- **Markdown 生成** — 自动整理为结构化 Markdown\n- **结果操作** — 预览、复制、分享、保存为 .md 文件\n- **评论提取** — 同步获取视频热门评论\n- **B站 登录** — 扫码登录\n\n### 下载\n\n[**下载 v0.2.1 APK**](https://github.com/iambest1-hue/bQuantumReader-Android/releases/tag/v0.2.1)\n要求：Android 10.0+（API 29+）\n\n### 技术栈\n\nKotlin + Jetpack Compose + Material3 / Retrofit + OkHttp / MVVM + Repository\n\n### 更多信息\n\n前往 [bQuantumReader-Android](https://github.com/iambest1-hue/bQuantumReader-Android) 查看完整说明、安装步骤、构建指南和项目结构。'

content = content.replace(old, new)
sys.stdout.write(base64.b64encode(content.encode('utf-8')).decode('utf-8'))
