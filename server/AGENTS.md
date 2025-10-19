---
name: "scrcpy-server"
description: "scrcpy 的服务端组件，在 Android 设备上运行，用于捕获屏幕、音频和控制事件，并将其流式传输到桌面客户端。"
category: "后端服务 / Android 工具"
author: "Genymobile"
authorUrl: "https://github.com/Genymobile/scrcpy"
tags: ["Java", "Android", "Gradle", "JUnit", "Screen Mirroring"]
lastUpdated: "2025-10-18"
---

# scrcpy-server

## 项目概述

`scrcpy-server` 是 [scrcpy](https://github.com/Genymobile/scrcpy) 项目的核心服务端组件。它是一个在 Android 设备上运行的 Java 应用程序，负责实时捕获设备的屏幕、音频和输入事件，并将这些数据流式传输到桌面客户端。该项目使得用户能够在电脑上查看和控制他们的 Android 设备，解决了在开发、测试和演示过程中需要与移动设备进行高效交互的问题。

## 技术栈

- **语言**: **Java** - 项目的核心编程语言。
- **平台**: **Android** - 专为 Android 平台设计，最低支持 API 级别 21 (Android 5.0)。
- **构建工具**: **Gradle** - 用于自动化构建、测试和依赖管理。同时项目也提供了 `build_without_gradle.sh` 脚本用于手动构建。
- **测试框架**: **JUnit** - 用于对核心工具类和控制协议进行单元测试。
- **核心库**: **Android SDK** - 直接使用 Android 系统 API (如 `MediaCodec` for encoding, `InputManager` for event injection) 来实现核心功能。

## 项目结构

项目的目录结构清晰，按功能模块进行组织：

```
scrcpy-server/
├── src/
│   ├── main/
│   │   ├── java/com/genymobile/scrcpy/
│   │   │   ├── audio/      # 音频捕获与编码
│   │   │   ├── control/    # 控制事件处理
│   │   │   ├── device/     # 设备连接与信息
│   │   │   ├── opengl/     # OpenGL 相关工具
│   │   │   ├── util/       # 通用工具类
│   │   │   ├── video/      # 视频捕获与编码
│   │   │   └── wrappers/   # Android 服务包装类
│   │   └── AndroidManifest.xml
│   └── test/
│       └── java/com/genymobile/scrcpy/ # 单元测试
├── build.gradle      # Gradle 构建脚本
├── meson.build       # Meson 构建配置
└── build_without_gradle.sh # 手动构建脚本
```

## 开发指南

### 代码风格

- 项目遵循标准的 Java 代码约定。
- 虽然未能读取 `checkstyle` 配置文件，但从代码库来看，项目注重代码的可读性和一致性，例如：
    - 使用清晰的命名。
    - 对公共 API 和复杂逻辑有适当的注释。

### 命名约定

- **类名**: `PascalCase` (e.g., `SurfaceEncoder`)
- **方法名**: `camelCase` (e.g., `createMediaCodec`)
- **变量名**: `camelCase` (e.g., `videoBitRate`)
- **常量**: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_I_FRAME_INTERVAL`)

### Git 工作流

项目托管在 GitHub 上，遵循标准的开源项目协作流程：

- **分支**: 功能开发和 bug 修复在单独的分支上进行。
- **提交**: 提交信息应清晰地描述变更内容。
- **Pull Request**: 所有的代码变更都通过 Pull Request (PR) 提交，经过代码审查后合并到主分支。

## 环境设置

### 开发要求

- **Android SDK**: `ANDROID_HOME` 环境变量必须正确设置。
- **Android Platform & Build Tools**: 需要特定版本的构建工具，例如 `platform-35` 和 `build-tools-35.0.0`。
- **JDK**: Java 1.8 或更高版本。

### 安装步骤

由于 `scrcpy-server` 是 `scrcpy` 的一部分，通常开发者会构建整个 `scrcpy` 项目。单独构建服务端的步骤如下：

```bash
# 1. 克隆 scrcpy 项目
git clone https://github.com/Genymobile/scrcpy.git
cd scrcpy/server

# 2. 使用 Gradle 构建
./gradlew assembleRelease

# 或者使用手动构建脚本
# (需要先配置好 ANDROID_HOME 和相关构建工具版本)
BUILD_DIR=build_manual ./build_without_gradle.sh
```

构建成功后，会在 `build/libs/` 或指定的 `BUILD_DIR` 目录下生成 `scrcpy-server.jar`。

## 核心功能实现

### 视频流模块

视频流的核心由 `SurfaceEncoder` 类负责。它通过 `SurfaceCapture` 从屏幕或摄像头获取数据，并使用 `MediaCodec` API 进行硬件或软件编码。

```java
// src/main/java/com/genymobile/scrcpy/video/SurfaceEncoder.java

// 关键步骤：
// 1. 创建 MediaCodec 实例
MediaCodec mediaCodec = createMediaCodec(codec, encoderName);
// 2. 配置编码格式 (分辨率，比特率，帧率等)
MediaFormat format = createFormat(codec.getMimeType(), videoBitRate, maxFps, codecOptions);
mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
// 3. 获取输入 Surface 并开始捕获
Surface surface = mediaCodec.createInputSurface();
capture.start(surface);
mediaCodec.start();
// 4. 从输出缓冲区获取编码后的数据并发送
encode(mediaCodec, streamer);
```

### 控制流模块

控制流由 `Controller` 类处理，它在一个独立的线程中运行，负责接收和分发来自客户端的控制消息。

```java
// src/main/java/com/genymobile/scrcpy/control/Controller.java

// 消息处理循环
private boolean handleEvent() throws IOException {
    ControlMessage msg = controlChannel.recv();
    switch (msg.getType()) {
        case ControlMessage.TYPE_INJECT_KEYCODE:
            injectKeycode(msg.getAction(), msg.getKeycode(), msg.getRepeat(), msg.getMetaState());
            break;
        case ControlMessage.TYPE_INJECT_TOUCH_EVENT:
            injectTouch(msg.getAction(), msg.getPointerId(), msg.getPosition(), msg.getPressure(), msg.getActionButton(), msg.getButtons());
            break;
        // ... 其他消息类型
    }
    return true;
}
```

## 测试策略

### 单元测试

- 项目使用 **JUnit** 进行单元测试，测试代码位于 `src/test/java`。
- 测试重点覆盖了核心工具类和协议解析的正确性，确保了项目的稳定性和可靠性。
- **示例**: `ControlMessageReaderTest` 详细测试了所有控制消息的二进制格式解析，确保了客户端和服务端之间的通信协议兼容性。

## 部署指南

### 构建过程

```bash
# 使用 Gradle 构建
./gradlew assembleRelease
```

### 部署步骤

`scrcpy-server` 由 `scrcpy` 客户端自动部署：

1.  客户端启动时，会通过 `adb push` 将 `scrcpy-server.jar` 上传到设备的 `/data/local/tmp/` 目录。
2.  客户端通过 `adb shell` 命令在设备上执行 `app_process` 来启动 `scrcpy-server` 的 `main` 方法。
3.  服务端启动后，通过 `LocalSocket` 与客户端建立连接，开始传输数据。

### 环境变量

服务端行为通过命令行参数进行配置，而不是环境变量。所有可配置项定义在 `Options.java` 中。

## 性能优化

### 视频优化

- **比特率控制**: 通过 `--video-bit-rate` 选项调整视频码率，平衡清晰度和流畅度。
- **帧率限制**: 使用 `--max-fps` 限制最高帧率，降低设备性能消耗。
- **分辨率调整**: 通过 `--max-size` 选项在编码前缩放视频分辨率。
- **编码器选择**: 允许通过 `--video-encoder` 手动指定性能更优的硬件编码器。
- **错误降级**: 在编码初始化失败时，会自动尝试降低分辨率重新编码 (`downsize-on-error`)。

### 音频优化

- **编解码器选择**: 支持 `Opus`, `AAC`, `FLAC` 和 `RAW` 等多种音频格式，用户可根据需求选择。
- **比特率控制**: 通过 `--audio-bit-rate` 选项调整音频码率。

## 安全考虑

### 通信安全

- `scrcpy-server` 的所有通信都依赖于 **Android Debug Bridge (ADB)**。
- 安全性完全由 ADB 的授权机制保障。只有在用户手动开启“USB 调试”并授权特定计算机后，`scrcpy` 才能连接和控制设备。
- 项目本身不包含额外的认证或加密机制。

## 监控和日志

### 日志管理

- 项目内置了日志系统 `Ln.java`。
- 日志级别可以通过 `--log-level` 命令行参数进行控制 (e.g., `debug`, `info`, `warn`, `error`)。
- 日志会输出到客户端的控制台，帮助开发者诊断问题。
