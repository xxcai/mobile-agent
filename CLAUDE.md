# Mobile Agent - 技术文档

## 项目概述

- **项目名称**: Mobile Agent
- **包名**: com.hh.agent
- **构建工具**: Gradle 8.12.1, AGP 8.3.2
- **语言**: Java 21, C++ (NDK)
- **架构**: MVP (Model-View-Presenter)
- **平台**: Android

## 功能特性

1. **聊天界面** - 与 Nanobot 对话的 Android 前端
2. **HTTP 连接** - 通过 HTTP API 连接本地运行的 Nanobot 服务
3. **Markdown 渲染** - 支持 Markdown 格式的助手消息展示
4. **消息气泡** - 微信风格的用户/助手消息气泡 UI
5. **思考提示** - 显示 Nanobot 正在思考的交互状态

## 目录结构

```
mobile-agent/
├── app/                         # Android应用模块
│   └── src/main/
│       ├── java/com/hh/agent/
│       │   ├── MainActivity.java        # 主界面 Activity
│       │   ├── contract/
│       │   │   └── MainContract.java   # MVP 契约接口
│       │   ├── presenter/
│       │   │   └── MainPresenter.java  # 业务逻辑处理
│       │   └── ui/
│       │       └── MessageAdapter.java # 消息列表适配器
│       └── res/
│           ├── layout/                   # 布局文件
│           ├── drawable/                #Drawable 资源
│           ├── values/                  # 颜色、主题等
│           └── xml/                     # 网络安全配置
├── lib/                         # Native Library模块
│   └── src/main/
│       ├── cpp/                    # C++源代码
│       └── java/com/hh/agent/lib/
│           ├── api/
│           │   └── NanobotApi.java    # API 接口定义
│           ├── config/
│           │   └── NanobotConfig.java # HTTP 配置
│           ├── dto/
│           │   ├── ChatRequest.java   # 请求 DTO
│           │   └── ChatResponse.java  # 响应 DTO
│           ├── http/
│           │   └── HttpNanobotApi.java # HTTP 实现
│           ├── impl/
│           │   └── MockNanobotApi.java # Mock 实现
│           └── model/
│               ├── Message.java        # 消息模型
│               └── Session.java       # 会话模型
├── build.gradle                  # 根项目配置
└── settings.gradle
```

## 技术栈

### 核心库
- **OkHttp 4.12.0** - HTTP 客户端
- **Gson 2.10.1** - JSON 序列化
- **Markwon 4.6.2** - Markdown 渲染
- **AndroidX** - RecyclerView, CardView, ConstraintLayout

### 架构
- **MVP 模式** - Model-View-Presenter
- **接口定义** - 通过 Contract 接口解耦

## 网络配置

### 连接 Nanobot

应用通过 HTTP 连接到本地运行的 Nanobot 服务：

1. **Nanobot HTTP 端口**: 18791 (可在 NanobotConfig 中修改)
2. **adb reverse**: `adb reverse tcp:18791 tcp:18791`
3. **网络安全配置**: 允许 localhost 明文 HTTP 请求

### NanobotConfig

```java
// 默认配置
baseUrl: "http://localhost:18791"
connectTimeout: 30秒
readTimeout: 60秒
```

## 构建命令

```bash
# 调试构建
./gradlew assembleDebug

# 清理构建
./gradlew clean assembleDebug

# 依赖更新
./gradlew dependencies

# 安装 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 提交规范

### 提交格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| 类型 | 说明 |
|------|------|
| feat | 新功能 |
| fix | Bug修复 |
| refactor | 代码重构 |
| perf | 性能优化 |
| build | 构建相关 |
| ci | CI/CD配置 |
| docs | 文档更新 |
| test | 测试相关 |
| chore | 杂项更新 |

### Scope 范围

- `app`: app模块相关的更改
- `lib`: lib模块相关的更改
- `native`: C++/NDK相关的更改
- `deps`: 依赖更新
- `gradle`: Gradle配置

### 规则

1. 标题不超过50个字符
2. 使用祈使句
3. 标题首字母小写
4. 结尾不加句号
5. Body和Footer可选
6. Footer使用 `Closes #issue` 或 `Fixes #issue`
7. 提交内容里面不要带上AI信息

## API 接口

### NanobotApi

```java
public interface NanobotApi {
    // 获取会话
    Session getSession(String sessionKey);

    // 获取历史消息
    List<Message> getHistory(String sessionKey, int limit);

    // 发送消息并获取回复
    Message sendMessage(String content, String sessionKey);
}
```

### Message 模型

```java
public class Message {
    private String role;      // user, assistant, system
    private String content;   // 消息内容
    private long timestamp;  // 时间戳
}
```

## 扩展开发

### 添加新的消息类型

1. 在 `Message.java` 中添加 role 类型
2. 在 `MessageAdapter.java` 中添加对应的 viewType
3. 创建对应的布局文件
4. 在 `bind()` 方法中处理渲染逻辑

### 修改 HTTP 端点

编辑 `NanobotConfig.java` 中的 `baseUrl`：

```java
NanobotConfig config = new NanobotConfig("http://your-server:port");
```
