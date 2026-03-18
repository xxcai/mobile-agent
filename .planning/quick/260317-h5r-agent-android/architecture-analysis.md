# agent-android 代码结构优化分析报告

## 一、当前代码结构概览

```
agent-android/src/main/java/com/hh/agent/
├── android/                          # 主模块
│   ├── AgentFragment.java            # UI层：Fragment + 流式状态管理 + 语音处理
│   ├── AgentInitializer.java         # 初始化
│   ├── AndroidToolManager.java       # 工具管理：注册 + 执行 + JSON生成
│   ├── MobileAgentApplication.java   # Application
│   ├── WorkspaceManager.java         # 工作空间管理
│   ├── contract/
│   │   └── MainContract.java        # MVP契约：View(16+方法) + Presenter
│   ├── presenter/
│   │   ├── MainPresenter.java        # 业务逻辑：会话+消息+流式+状态+线程管理
│   │   └── NativeMobileAgentApiAdapter.java  # API适配：初始化+转换
│   ├── ui/
│   │   └── MessageAdapter.java       # RecyclerView适配器
│   └── voice/
│       ├── IVoiceRecognizer.java     # 语音识别接口
│       └── VoiceRecognizerHolder.java
├── floating/                         # 悬浮球模块（独立包）
│   ├── FloatingBallManager.java
│   ├── FloatingBallView.java
│   ├── FloatingBallReceiver.java
│   └── ContainerActivity.java
```

---

## 二、架构问题分析

### 2.1 模块职责划分问题

#### 问题 1：MainPresenter 职责过重

**现状：** MainPresenter (275行) 承担了以下职责：
- 会话管理：`getSession(sessionKey)`
- 消息加载：`loadMessages()`
- 消息发送：`sendMessage(content)`
- 流式处理：`sendMessageStream()` + AgentEventListener 回调
- 状态管理：`isThinking` 状态
- 线程管理：两个 ExecutorService
- View绑定：attachView/detachView

**违反原则：** 单一职责原则 (SRP)

**影响：** 难以测试，逻辑分散，修改时牵一发而动全身

#### 问题 2：NativeMobileAgentApiAdapter 职责混乱

**现状：** 混合了以下职责：
- 配置加载：`loadConfigFromAssets()`
- Native库加载：`System.loadLibrary("icraw")`
- Workspace初始化：`WorkspaceManager.initialize()`
- API适配：`getSession()`, `sendMessage()`, `getHistory()`
- 模型转换：`convertMessage()`, `convertSession()`

**建议拆分：**
```
NativeMobileAgentApiAdapter
    ├── AgentConfigLoader  (配置加载)
    ├── NativeLibraryLoader (Native库加载)
    └── MobileAgentApiAdapter (API适配，仅保留转换逻辑)
```

#### 问题 3：AgentFragment 承担过多UI之外的工作

**现状：** AgentFragment (473行) 包含：
- 权限管理：录音权限请求
- 语音识别：setupVoiceButtonListener(), 语音状态管理
- 流式状态：`isStreaming`, `streamTextBuffer`
- UI更新：消息列表操作
- 思考状态：showThinking/hideThinking

**建议：** 将流式状态管理和语音逻辑提取到独立组件

---

### 2.2 依赖关系混乱

#### 问题 4：过度使用静态单例

**现状：**
```java
// MainPresenter.java:215
com.hh.agent.library.NativeAgent.cancelStream();

// NativeMobileAgentApiAdapter.java:27
this.nativeApi = NativeMobileAgentApi.getInstance();

// AndroidToolManager.java:43
NativeMobileAgentApi.getInstance().setToolCallback(this);
```

**问题：**
- 静态单例难以mock，单元测试困难
- 隐藏了真正的依赖关系
- 全局状态可能导致不可预期的行为
- 无法通过依赖注入管理生命周期

**建议：** 通过构造函数注入接口，而非直接获取单例

#### 问题 5：缺少接口抽象层

**现状：** Presenter 直接依赖具体实现
```java
// MainPresenter.java:30
private final MobileAgentApi mobileAgentApi;

// MainPresenter.java:55
this.mobileAgentApi = new NativeMobileAgentApiAdapter();
```

**问题：** 虽然有 MobileAgentApi 接口，但创建时直接 new 具体类，无法切换实现

**建议：** 通过工厂或依赖注入提供实现

---

### 2.3 接口设计问题

#### 问题 6：MainContract.View 接口过大

**现状：** 16个方法
```java
interface View {
    void onMessagesLoaded(List<Message> messages);
    void onMessageReceived(Message message);
    void onUserMessageSent(Message message);
    void onError(String error);
    void showLoading();
    void hideLoading();
    void showThinking();
    void hideThinking();
    void onStreamTextDelta(String textDelta);
    void onStreamToolUse(String id, String name, String argumentsJson);
    void onStreamToolResult(String id, String result);
    void onStreamMessageEnd(String finishReason);
    void onStreamError(String errorCode, String errorMessage);
}
```

**违反原则：** 接口隔离原则 (ISP)

**建议拆分：**
```java
// 基础UI接口
interface MainView {
    void onError(String error);
    void showLoading();
    void hideLoading();
}

// 消息相关
interface MessageListView {
    void onMessagesLoaded(List<Message> messages);
    void onMessageReceived(Message message);
    void onUserMessageSent(Message message);
}

// 流式响应
interface StreamingView {
    void onStreamTextDelta(String textDelta);
    void onStreamToolUse(String id, String name, String argumentsJson);
    void onStreamToolResult(String id, String result);
    void onStreamMessageEnd(String finishReason);
    void onStreamError(String errorCode, String errorMessage);
    void showThinking();
    void hideThinking();
}

// AgentFragment 实现所有接口
class AgentFragment implements MainView, MessageListView, StreamingView
```

---

### 2.4 包结构问题

#### 问题 7：包命名不一致

| 包 | 路径 |
|---|---|
| 主模块 | `com.hh.agent.android` |
| 悬浮球 | `com.hh.agent.floating` |
| 工具 | `com.hh.agent.android` (AndroidToolManager) |

**建议统一：**
```
com.hh.agent.android
├── AgentFragment.java
├── AndroidToolManager.java
└── ...

com.hh.agent.floating
├── FloatingBallManager.java
├── FloatingBallView.java
└── ContainerActivity.java

com.hh.agent.core        # 核心模块（API适配、业务逻辑）
com.hh.agent.ui          # UI组件
com.hh.agent.voice       # 语音模块
```

---

### 2.5 线程管理问题

#### 问题 8：线程池管理混乱

**现状：**
```java
// MainPresenter.java:56-57
this.executor = Executors.newSingleThreadExecutor();
this.loadMessagesExecutor = Executors.newSingleThreadExecutor();
```

**问题：**
- 两个独立线程池，资源浪费
- 没有统一的线程调度策略
- destroy() 时需要手动关闭，可能遗漏

**建议：** 使用统一的线程池管理类
```java
// ThreadPoolManager.java (建议)
public class ThreadPoolManager {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;

    private static ExecutorService ioExecutor;
    private static ExecutorService computeExecutor;

    public static ExecutorService getIoExecutor() { ... }
    public static ExecutorService getComputeExecutor() { ... }
}
```

---

### 2.6 状态管理问题

#### 问题 9：状态分散

**现状：**
- `isThinking` 状态在 MainPresenter
- `isStreaming` 状态在 AgentFragment
- `streamTextBuffer` 状态在 AgentFragment
- `sPermissionGranted` 静态变量

**建议：** 统一状态管理
```java
// AgentState.java (建议)
public class AgentState {
    private final MutableStateFlow<ThinkingState> thinkingState;
    private final MutableStateFlow<StreamingState> streamingState;

    public StateFlow<ThinkingState> getThinkingState() { ... }
    public StateFlow<StreamingState> getStreamingState() { ... }
}
```

---

## 三、重构示例代码

### 3.1 拆分 MainContract.View 接口

```java
// ==================== 原始接口 ====================
// contract/MainContract.java - 接口过大

// ==================== 重构后 ====================
// contract/MainContract.java

public interface MainContract {

    /**
     * 基础View接口 - 所有View必须实现
     */
    interface BaseView {
        void onError(String error);
        void showLoading();
        void hideLoading();
    }

    /**
     * 消息列表View
     */
    interface MessageListView extends BaseView {
        void onMessagesLoaded(List<Message> messages);
        void onMessageReceived(Message message);
        void onUserMessageSent(Message message);
    }

    /**
     * 流式响应View
     */
    interface StreamingView extends BaseView {
        void onStreamTextDelta(String textDelta);
        void onStreamToolUse(String id, String name, String argumentsJson);
        void onStreamToolResult(String id, String result);
        void onStreamMessageEnd(String finishReason);
        void onStreamError(String errorCode, String errorMessage);
        void showThinking();
        void hideThinking();
    }

    /**
     * Presenter接口
     */
    interface Presenter {
        void loadMessages();
        void sendMessage(String content);
        void attachView(MessageListView view, StreamingView streamingView);
        void detachView();
        void cancelStream();
    }
}

// ==================== 使用示例 ====================
// AgentFragment.java
public class AgentFragment extends Fragment
    implements MainContract.MessageListView, MainContract.StreamingView {

    // 无需实现非必要的BaseView方法（如果AgentFragment不需要loading状态）
    // 可以只关注自己需要的接口
}
```

### 3.2 提取 SessionManager

```java
// ==================== 新增文件 ====================
// session/SessionManager.java

public class SessionManager {
    private final MobileAgentApi api;
    private final String defaultSessionKey;
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();

    public SessionManager(MobileAgentApi api, String defaultSessionKey) {
        this.api = api;
        this.defaultSessionKey = defaultSessionKey;
    }

    /**
     * 获取或创建会话
     */
    public Session getOrCreateSession(String sessionKey) {
        return sessionCache.computeIfAbsent(sessionKey, key -> {
            Session session = api.getSession(key);
            return session != null ? session : api.createSession("default", key);
        });
    }

    /**
     * 获取历史消息
     */
    public List<Message> getHistory(String sessionKey, int maxMessages) {
        return api.getHistory(sessionKey, maxMessages);
    }

    /**
     * 清理会话缓存
     */
    public void clearCache() {
        sessionCache.clear();
    }
}

// ==================== 使用示例 ====================
// MainPresenter.java

public class MainPresenter implements MainContract.Presenter {

    // 依赖注入，而非直接创建
    private final MobileAgentApi api;
    private final SessionManager sessionManager;  // 新增

    private MessageListView messageListView;
    private StreamingView streamingView;

    public MainPresenter(MobileAgentApi api) {
        this.api = api;
        this.sessionManager = new SessionManager(api, "native:default");
    }

    @Override
    public void loadMessages() {
        // 使用 SessionManager
        List<Message> messages = sessionManager.getHistory(sessionKey, 50);
        // ...
    }
}
```

### 3.3 提取 StreamingManager

```java
// ==================== 新增文件 ====================
// streaming/StreamingManager.java

public class StreamingManager {

    public interface StreamingCallback {
        void onTextDelta(String text);
        void onToolUse(String id, String name, String argumentsJson);
        void onToolResult(String id, String result);
        void onMessageEnd(String finishReason);
        void onError(String errorCode, String errorMessage);
    }

    private final MobileAgentApi api;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private StreamingCallback callback;

    public StreamingManager(MobileAgentApi api) {
        this.api = api;
    }

    public void setCallback(StreamingCallback callback) {
        this.callback = callback;
    }

    public boolean isStreaming() {
        return isStreaming.get();
    }

    public void sendMessageStream(String content, String sessionKey) {
        if (isStreaming.get()) {
            return;
        }
        isStreaming.set(true);

        AgentEventListener listener = new AgentEventListener() {
            @Override
            public void onTextDelta(String text) {
                if (callback != null) callback.onTextDelta(text);
            }

            @Override
            public void onToolUse(String id, String name, String argumentsJson) {
                if (callback != null) callback.onToolUse(id, name, argumentsJson);
            }

            @Override
            public void onToolResult(String id, String result) {
                if (callback != null) callback.onToolResult(id, result);
            }

            @Override
            public void onMessageEnd(String finishReason) {
                isStreaming.set(false);
                if (callback != null) callback.onMessageEnd(finishReason);
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                isStreaming.set(false);
                if (callback != null) callback.onError(errorCode, errorMessage);
            }
        };

        api.sendMessageStream(content, sessionKey, listener);
    }

    public void cancel() {
        if (isStreaming.getAndSet(false)) {
            NativeAgent.cancelStream();
        }
    }
}

// ==================== 使用示例 ====================
// MainPresenter.java

public class MainPresenter implements MainContract.Presenter {

    private final StreamingManager streamingManager;  // 新增

    public MainPresenter(MobileAgentApi api) {
        this.api = api;
        this.streamingManager = new StreamingManager(api);  // 初始化
    }

    @Override
    public void sendMessage(String content) {
        // 使用 StreamingManager
        streamingManager.setCallback(new StreamingManager.StreamingCallback() {
            @Override
            public void onTextDelta(String text) {
                mainHandler.post(() -> view.onStreamTextDelta(text));
            }
            // ... 其他回调
        });
        streamingManager.sendMessageStream(content, sessionKey);
    }
}
```

### 3.4 依赖注入示例

```java
// ==================== 新增文件 ====================
// di/AgentContainer.java

public class AgentContainer {
    private static AgentContainer instance;

    // 依赖
    private final MobileAgentApi mobileAgentApi;
    private final SessionManager sessionManager;
    private final StreamingManager streamingManager;
    private final ThreadPoolManager threadPoolManager;

    private AgentContainer(Context context) {
        // 创建依赖
        this.mobileAgentApi = createMobileAgentApi(context);
        this.threadPoolManager = new ThreadPoolManager();
        this.sessionManager = new SessionManager(mobileAgentApi, "native:default");
        this.streamingManager = new StreamingManager(mobileAgentApi);
    }

    public static void init(Context context) {
        instance = new AgentContainer(context);
    }

    public static AgentContainer getInstance() {
        return instance;
    }

    private MobileAgentApi createMobileAgentApi(Context context) {
        NativeMobileAgentApiAdapter adapter = new NativeMobileAgentApiAdapter();
        adapter.setContext(context);
        return adapter;
    }

    // 提供依赖
    public MobileAgentApi getMobileAgentApi() { return mobileAgentApi; }
    public SessionManager getSessionManager() { return sessionManager; }
    public StreamingManager getStreamingManager() { return streamingManager; }
    public ThreadPoolManager getThreadPoolManager() { return threadPoolManager; }
}

// ==================== 使用示例 ====================
// MainPresenter.java - 接收依赖注入

public class MainPresenter implements MainContract.Presenter {

    private final MobileAgentApi api;
    private final SessionManager sessionManager;
    private final StreamingManager streamingManager;

    // 构造函数注入
    public MainPresenter(
            MobileAgentApi api,
            SessionManager sessionManager,
            StreamingManager streamingManager) {
        this.api = api;
        this.sessionManager = sessionManager;
        this.streamingManager = streamingManager;
    }

    // 静态工厂方法，方便创建
    public static MainPresenter create() {
        AgentContainer container = AgentContainer.getInstance();
        return new MainPresenter(
                container.getMobileAgentApi(),
                container.getSessionManager(),
                container.getStreamingManager()
        );
    }
}
```

### 3.5 统一线程池管理

```java
// ==================== 新增文件 ====================
// thread/ThreadPoolManager.java

public class ThreadPoolManager {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    // IO密集型任务线程池
    private static final ExecutorService IO_EXECUTOR =
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("agent-io-" + t.getId());
            return t;
        });

    // 计算密集型任务线程池
    private static final ExecutorService COMPUTE_EXECUTOR =
        Executors.newFixedThreadPool(CPU_COUNT + 1, r -> {
            Thread t = new Thread(r);
            t.setName("agent-compute-" + t.getId());
            return t;
        });

    private ThreadPoolManager() {}

    /**
     * IO密集型任务（网络、文件操作）
     */
    public static void executeIo(Runnable task) {
        IO_EXECUTOR.execute(task);
    }

    /**
     * 计算密集型任务
     */
    public static void executeCompute(Runnable task) {
        COMPUTE_EXECUTOR.execute(task);
    }

    /**
     * 带回调的IO任务
     */
    public static <T> Future<T> submitIo(Callable<T> task) {
        return IO_EXECUTOR.submit(task);
    }

    /**
     * 关闭所有线程池（应用退出时调用）
     */
    public static void shutdown() {
        IO_EXECUTOR.shutdown();
        COMPUTE_EXECUTOR.shutdown();
    }
}
```

---

## 四、重构优先级建议

| 优先级 | 任务 | 预期收益 | 工作量 |
|--------|------|----------|--------|
| P0 | 拆分 MainContract.View 接口 | 降低耦合，提高可测试性 | 低 |
| P0 | 提取 StreamingManager | 解耦流式状态管理 | 中 |
| P1 | 统一线程池管理 | 资源优化，可预测行为 | 中 |
| P1 | 提取 SessionManager | 分离会话管理逻辑 | 中 |
| P2 | 引入依赖注入容器 | 提高可测试性，可替换实现 | 高 |
| P2 | 统一包结构 | 代码组织清晰 | 低 |
| P3 | 统一状态管理 | 状态一致性 | 高 |

---

## 五、总结

当前 agent-android 模块存在以下核心问题：

1. **职责不清**：MainPresenter、NativeMobileAgentApiAdapter 承担过多职责
2. **依赖混乱**：过度使用静态单例，难以测试
3. **接口臃肿**：MainContract.View 违反接口隔离原则
4. **状态分散**：isThinking/isStreaming 状态分散在多处
5. **线程无管理**：多个独立线程池，无统一调度

建议按照本报告的重构示例逐步优化，优先从 P0 级别任务开始。

---

*报告生成时间：2026-03-17*
