# Mobile Agent

让用户通过自然对话，指挥手机自动完成日常任务的 Android 自动化框架。

## 架构概览

```
┌─────────────────────────────────────────────┐
│  app (Android 应用模块)                       │
│  - UI 层: Activity, Presenter, Adapter       │
│  - 工具实现: ShowToastTool 等               │
│  - AndroidToolManager, WorkspaceManager     │
└─────────────────┬───────────────────────────┘
                  ↓ 依赖
┌─────────────────────────────────────────────┐
│  agent (Android Library + Native)            │
│  - Java: API 接口, ToolExecutor, 模型       │
│  - C++: JNI 绑定, Agent 核心逻辑            │
└─────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 职责 |
|------|------|
| **app** | Android 应用，包含 UI 和工具实现 |
| **agent** | Library 模块，提供 Java API 和 C++ 原生代码 |
| **cxxplatform** | 独立 C++ 平台，用于测试和参考 |

### 依赖关系

```
app → agent
```

## 快速开始

### 前置要求

- Android Studio Arctic Fox+
- Gradle 8.0+
- Android SDK 34+

### 构建步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd mobile-agent
   ```

2. **配置 API Key**

   复制配置模板文件并填写你的 API Key：
   ```bash
   cp config.json.template config.json
   ```

   编辑 `config.json`：
   ```json
   {
     "provider": {
       "apiKey": "your-api-key-here",
       "baseUrl": "https://api.minimaxi.com/v1"
     },
     "agent": {
       "model": "MiniMax-M2.5-highspeed"
     }
   }
   ```

3. **构建项目**
   ```bash
   ./gradlew assembleDebug
   ```

4. **安装运行**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 项目结构

```
mobile-agent/
├── app/                         # Android 应用模块
│   └── src/main/
│       ├── java/com/hh/agent/
│       │   ├── MainActivity.java        # 主界面
│       │   ├── LauncherActivity.java   # 启动页
│       │   ├── contract/               # MVP Contract
│       │   ├── presenter/              # 业务逻辑
│       │   ├── ui/                    # UI 组件
│       │   ├── tools/                 # Android 工具
│       │   │   ├── ShowToastTool.java
│       │   │   ├── TakeScreenshotTool.java
│       │   │   └── ...
│       │   ├── AndroidToolManager.java # 工具管理
│       │   └── WorkspaceManager.java   # 工作空间
│       ├── res/                       # 资源文件
│       └── AndroidManifest.xml
│
├── agent/                       # Android Library (Native)
│   └── src/main/
│       ├── java/com/hh/agent/library/
│       │   ├── api/                   # API 接口
│       │   │   ├── MobileAgentApi.java
│       │   │   └── NativeMobileAgentApi.java
│       │   ├── model/                # 数据模型
│       │   └── ToolExecutor.java     # 工具接口
│       └── cpp/                      # C++ 原生代码
│           ├── native_agent.cpp       # JNI 入口
│           ├── include/icraw/         # 头文件
│           └── src/core/              # 核心实现
│
├── cxxplatform/                 # 独立 C++ 平台 (测试/参考)
├── docs/                        # 文档
├── config.json.template         # 配置模板
├── build.gradle                 # 根构建文件
└── settings.gradle
```

## 扩展指南

### 添加新的 Android 工具

参考 [Android 工具扩展指南](docs/android-tool-extension.md)

### 配置说明

`config.json` 配置项：

| 字段 | 类型 | 说明 |
|------|------|------|
| provider.apiKey | string | LLM API Key |
| provider.baseUrl | string | API 基础 URL |
| agent.model | string | 使用的模型名称 |

### 工具列表

内置工具：

| 工具 | 功能 |
|------|------|
| show_toast | 显示 Toast 提示 |
| display_notification | 显示通知 |
| read_clipboard | 读取剪贴板 |
| take_screenshot | 截图 |
| search_contacts | 搜索联系人 |
| send_im_message | 发送即时消息 |

## 开发指南

### 模块职责

- **app**: Android 应用，包含 UI、工具实现、业务逻辑
- **agent**: Library 模块，提供 API 接口和 C++ 原生代码
- **cxxplatform**: 独立 C++ 实现，用于测试

### 代码风格

- Java: 驼峰命名，包名 `com.hh.agent`
- C++: snake_case 文件，PascalCase 类/命名空间
- 参考 [.planning/codebase/STRUCTURE.md](.planning/codebase/STRUCTURE.md)

## 许可证

MIT License
