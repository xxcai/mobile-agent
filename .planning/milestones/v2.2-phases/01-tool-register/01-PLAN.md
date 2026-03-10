---
phase: 01-tool-register
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
  - app/src/main/java/com/hh/agent/LauncherActivity.java
  - agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java
autonomous: true
requirements:
  - INJT-01
  - INJT-02
  - INJT-03
must_haves:
  truths:
    - App 层可以通过 registerTool(ToolExecutor) 注册自定义 Tool
    - 注册的 Tool 在应用运行期间动态添加，无需重启
    - Tool 注册时自动获取名称、描述和执行器
    - 同名 Tool 重复注册抛出异常
    - 注册后自动推送给 Agent (LLM)
  artifacts:
    - path: "agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java"
      provides: "registerTool() 方法和 generateToolsJson()"
      contains: "public void registerTool(ToolExecutor"
    - path: "app/src/main/java/com/hh/agent/LauncherActivity.java"
      provides: "内置 Tool 实例化和注册"
      contains: "registerTool(new ShowToastTool"
    - path: "agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java"
      provides: "移除重复的 AndroidToolManager 初始化"
      contains: "不再调用 toolManager.initialize()"
  key_links:
    - from: "app/LauncherActivity.java"
      to: "AndroidToolManager.registerTool()"
      via: "实例化 Tool 并调用 registerTool"
      pattern: "registerTool.*Tool"
    - from: "AndroidToolManager.registerTool()"
      to: "NativeMobileAgentApi.setToolsJson()"
      via: "注册后自动推送"
      pattern: "generateToolsJson.*setToolsJson"
---

<objective>
实现 AndroidToolManager.registerTool(ToolExecutor) 接口，支持 App 层动态注册自定义 Tool。

Purpose: 让 app 层能够扩展 Android 能力，实现 Tool 的运行时动态注册。
Output: 可运行的 Tool 注册接口和内置 Tool 迁移到 app 层。
</objective>

<execution_context>
@/Users/caixiao/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/phases/01-tool-register/01-CONTEXT.md

从 CONTEXT.md 的关键决策:
1. 接口签名: 只传 ToolExecutor，从 getName() 和 getDescription() 获取元信息
2. 内置 Tool: 6 个内置 Tool 改为在 app 层通过 registerTool() 统一注册
3. 重复注册: 同名 Tool 抛出异常拒绝
4. 保留 generateToolsJson() 在 AndroidToolManager 中

当前代码结构:
- ToolExecutor 接口: agent-core/src/main/java/com/hh/agent/library/ToolExecutor.java
- AndroidToolManager: agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
- 6 个内置 Tool: ShowToastTool, DisplayNotificationTool, ReadClipboardTool, TakeScreenshotTool, SearchContactsTool, SendImMessageTool
- MainPresenter 调用 toolManager.initialize() 初始化

接口定义 (ToolExecutor.java):
```java
public interface ToolExecutor {
    String getName();
    String execute(org.json.JSONObject args);
    String getDescription();
    String getArgsDescription();
    String getArgsSchema();
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: 添加 registerTool() 方法到 AndroidToolManager</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java</files>
  <action>
1. 在 AndroidToolManager 中添加 public 方法:
   `public void registerTool(ToolExecutor executor)`
2. 方法内部:
   - 从 executor.getName() 获取 Tool 名称
   - 检查 tools Map 中是否已存在同名 Tool，若存在则抛出 IllegalArgumentException
   - 将 ToolExecutor 添加到 tools Map 中
   - 调用 generateToolsJson() 生成新的 tools.json
   - 调用 NativeMobileAgentApi.getInstance().setToolsJson() 推送到 native 层
3. 修改 initialize() 方法:
   - 移除硬编码的 6 个内置 Tool 注册 (lines 44-50)
   - 只保留 loadToolsConfig() 和 setToolsJson() 调用
4. 导入必要的类 (ToolExecutor, Map.Entry)
  </action>
  <verify>
  <automated>编译检查: cd agent-android && ./gradlew compileDebugJavaWithJavac 2>&1 | head -50</automated>
  </verify>
  <done>
  - registerTool() 方法可接受 ToolExecutor 参数
  - 同名 Tool 重复注册抛出 IllegalArgumentException
  - 注册后自动调用 generateToolsJson() + setToolsJson()
  - initialize() 不再硬编码内置 Tool
  </done>
</task>

<task type="auto">
  <name>Task 2: 在 app 层注册 6 个内置 Tool</name>
  <files>app/src/main/java/com/hh/agent/LauncherActivity.java</files>
  <action>
1. 在 LauncherActivity 中导入 ToolExecutor 和 6 个 Tool 类:
   - com.hh.agent.library.ToolExecutor
   - com.hh.agent.android.tool.ShowToastTool
   - com.hh.agent.android.tool.DisplayNotificationTool
   - com.hh.agent.android.tool.ReadClipboardTool
   - com.hh.agent.android.tool.TakeScreenshotTool
   - com.hh.agent.android.tool.SearchContactsTool
   - com.hh.agent.android.tool.SendImMessageTool

2. 添加 AndroidToolManager 字段和 getInstance() 静态方法实现单例:
   ```java
   private static AndroidToolManager toolManager;

   public static AndroidToolManager getToolManager() {
       return toolManager;
   }
   ```

3. 在 onCreate() 中，context 可用后初始化:
   - 创建 AndroidToolManager 实例: `toolManager = new AndroidToolManager(this);`
   - 注册 6 个内置 Tool:
     ```java
     toolManager.registerTool(new ShowToastTool(this));
     toolManager.registerTool(new DisplayNotificationTool(this));
     toolManager.registerTool(new ReadClipboardTool(this));
     toolManager.registerTool(new TakeScreenshotTool(this));
     toolManager.registerTool(new SearchContactsTool());
     toolManager.registerTool(new SendImMessageTool());
     ```
   - 调用 toolManager.generateToolsJson() + NativeMobileAgentApi.setToolsJson() 推送 (或者在 AndroidToolManager 中注册后自动推送)

4. 移除 MainPresenter 中对 AndroidToolManager.initialize() 的调用 (因为现在由 app 层管理注册)
  </action>
  <verify>
  <automated>编译检查: cd app && ./gradlew compileDebugJavaWithJavac 2>&1 | head -50</automated>
  </verify>
  <done>
  - 6 个内置 Tool 在 app 层实例化并注册
  - LauncherActivity 提供 getToolManager() 静态方法获取实例
  - APK 编译成功
  </done>
</task>

<task type="auto">
  <name>Task 3: 验证 Tool 注册流程完整性</name>
  <files>agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java</files>
  <action>
1. 检查 MainPresenter.createApi() 方法，移除 AndroidToolManager 初始化代码 (lines 60-63)
   - 因为现在由 app 层负责 Tool 注册

2. 确保 NativeMobileAgentApi.setToolsJson() 已被正确调用

3. 如果 AndroidToolManager 需要在 app 层完全初始化后才能工作，确保 MainPresenter 的初始化顺序正确
  </action>
  <verify>
  <automated>编译检查: ./gradlew assembleDebug 2>&1 | tail -30</automated>
  </verify>
  <done>
  - MainPresenter 不再重复初始化 AndroidToolManager
  - APK 编译成功，无错误
  - Tool 注册流程正确: app 层注册 -> generateToolsJson -> setToolsJson
  </done>
</task>

</tasks>

<verification>
1. 编译: `./gradlew assembleDebug` 无错误
2. 代码审查: registerTool() 正确处理重复注册抛出异常
3. 流程验证: app 层 -> registerTool() -> generateToolsJson() -> setToolsJson() 完整链路
</verification>

<success_criteria>
- INJT-01: App 层可以通过 registerTool(ToolExecutor) 接口注册自定义 Tool
- INJT-02: Tool 注册支持运行时动态添加（注册后立即推送到 native 层）
- INJT-03: 从 ToolExecutor.getName() 和 getDescription() 获取元信息，三者不缺
</success_criteria>

<output>
After completion, create `.planning/phases/01-tool-register/01-SUMMARY.md`
</output>
