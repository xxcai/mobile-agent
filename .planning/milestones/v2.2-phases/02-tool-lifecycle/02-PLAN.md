---
phase: 02-tool-lifecycle
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
autonomous: true
requirements:
  - INJT-04
  - INJT-05
  - INJT-06
user_setup: []
must_haves:
  truths:
    - "App 层可以查询已注册的所有 Tool 列表"
    - "App 层可以注销已注册的 Tool"
    - "Tool 变更后主动推送给 Agent，LLM 能感知到新增/移除的 Tool"
    - "批量操作失败时全部回滚，保证原子性"
  artifacts:
    - path: "agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java"
      provides: "Tool 生命周期管理实现"
      contains:
        - "getRegisteredTools()"
        - "unregisterTool(String)"
        - "registerTools(HashMap)"
        - "unregisterTools(ArrayList)"
  key_links:
    - from: "AndroidToolManager"
      to: "NativeMobileAgentApi"
      via: "generateToolsJson() + setToolsJson()"
      pattern: "push.*tools.json.*after.*change"
---

<objective>
实现 Tool 的查询、注销和批量操作接口，并确保变更能主动推送给 Agent（LLM）

Purpose: 完善 AndroidToolManager 的生命周期管理能力，支持查询、单个/批量注销和批量注册，所有变更后主动推送 tools.json

Output: AndroidToolManager 新增方法
</objective>

<execution_context>
@/Users/caixiao/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
</context>

<tasks>

<task type="auto">
  <name>Task 1: 添加查询和单个注销接口</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java</files>
  <action>
在 AndroidToolManager 类中添加以下方法：

1. getRegisteredTools() - 返回 Map<String, ToolExecutor>
   - 返回内部 tools Map 的副本（防止外部修改内部状态）
   - 返回空Map而不是null

2. unregisterTool(String toolName) - 返回 boolean
   - 当 Tool 不存在时返回 false，不抛异常
   - 移除后调用 generateToolsJson() + setToolsJson() 推送到 native 层
   - Log 记录注销操作

参考现有 registerTool() 的推送模式实现。
  </action>
  <verify>
<automated>./gradlew assembleDebug 编译成功</automated>
  </verify>
  <done>
- getRegisteredTools() 返回 Map<String, ToolExecutor> 非空
- unregisterTool() 对不存在 Tool 返回 false
- 注销后 tools.json 自动推送到 native 层
</done>
</task>

<task type="auto">
  <name>Task 2: 添加批量操作接口（原子性）</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java</files>
  <action>
在 AndroidToolManager 类中添加以下方法：

1. registerTools(Map<String, ToolExecutor> toolsToRegister) - 返回 boolean
   - 参数检查：Map 不能为 null，内部元素不能为 null
   - 原子性：先验证所有 Tool 名称合法且无重复，再执行批量添加
   - 验证失败则抛出 IllegalArgumentException，不执行任何变更
   - 添加完成后调用 generateToolsJson() + setToolsJson() 推送一次

2. unregisterTools(List<String> toolNames) - 返回 boolean
   - 参数检查：List 不能为 null
   - 原子性：先验证所有 Tool 名称存在，再执行批量删除
   - 验证失败（如任何 Tool 不存在）则抛出 IllegalArgumentException，不执行任何变更
   - 删除完成后调用 generateToolsJson() + setToolsJson() 推送一次

使用 HashMap 和 ArrayList 作为参数类型（不要使用接口类型），确保兼容性。
  </action>
  <verify>
<automated>./gradlew assembleDebug 编译成功</automated>
  </verify>
  <done>
- registerTools() 验证失败时全部回滚，不产生部分注册
- unregisterTools() 验证失败时全部回滚，不产生部分注销
- 批量操作只推送一次 tools.json
</done>
</task>

<task type="auto">
  <name>Task 3: 验证 Tool 生命周期完整性</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java</files>
  <action>
确保所有变更场景都触发 tools.json 推送：

1. registerTool() - 已有推送逻辑
2. unregisterTool() - Task 1 添加推送逻辑
3. registerTools() - Task 2 添加推送逻辑
4. unregisterTools() - Task 2 添加推送逻辑

检查每个方法的推送调用位置是否正确（在变更成功后调用一次）。
  </action>
  <verify>
<automated>./gradlew assembleDebug 编译成功</automated>
  </verify>
  <done>
- 所有 4 个注册/注销方法都会触发 tools.json 推送
- LLM 能感知到新增/移除的 Tool
</done>
</task>

</tasks>

<verification>
- INJT-04: getRegisteredTools() 可查询已注册 Tool 列表
- INJT-05: unregisterTool() 可注销已注册 Tool
- INJT-06: 任何 Tool 变更后主动推送 tools.json 到 native 层
</verification>

<success_criteria>
- 编译通过
- 方法签名符合设计决策
- 原子性保证：批量操作失败时全部回滚
- 推送机制：变更后立即推送到 native 层
</success_criteria>

<output>
After completion, create `.planning/phases/02-tool-lifecycle/02-{plan}-SUMMARY.md`
</output>
