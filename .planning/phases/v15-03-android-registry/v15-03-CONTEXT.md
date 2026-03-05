# Phase 3: Android 注册表实现 - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

实现 Android 端 function → Executor 注册表，将 LLM 传入的 function 名称映射到具体的 ToolExecutor 实现。

核心目标（PIPE-03）:
- 建立 Android 端的工具注册表机制
- 支持扩展注册新工具
- 与 C++ 端通过 JNI 传递的 tools.json 配合工作

</domain>

<decisions>
## Implementation Decisions

### 注册方式
- **注解扫描注册**: 使用 @AndroidTool 注解标记工具类，框架自动扫描注册
- 原因: 与现有手动注册相比，更易扩展，新工具只需添加注解即可
- 扫描范围: `com.hh.agent.library.tools` 包

### Schema 验证
- **验证 function 名称**: 检查 function 是否在注册表中存在
- **可选验证 args**: 根据 tools.json 中的 schema 验证参数结构
- 原因: 保证 LLM 调用的是已注册的工具，提高安全性

### 错误处理
- **返回 JSON 错误格式**: 保持与现有 ToolExecutor.execute() 一致的返回格式
- 错误类型: tool_not_found, invalid_args, execution_failed
- 原因: 与现有代码一致，便于 C++ 端解析

### 动态重载
- **不支持热重载**: 初始化时一次性加载，后续不支持动态重载
- 原因: v1.5 保持简单，动态重载作为后续迭代

</decisions>

<specifics>
## Specific Ideas

**现有代码可复用:**
- `ToolExecutor` 接口 - 保持不变
- `AndroidToolManager` - 扩展支持注解扫描
- `AndroidToolCallback` - JNI 回调接口保持不变

**新增组件:**
1. `@AndroidTool` 注解 - 标记工具类
2. `ToolRegistry` 类 - 工具注册表核心（可从 AndroidToolManager 提取）
3. 工具类移到 `com.hh.agent.library.tools` 包

**工具注册示例:**
```java
@AndroidTool(name = "show_toast")
public class ShowToastTool implements ToolExecutor {
    @Override
    public String execute(JSONObject args) {
        // implementation
    }
}
```

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ToolExecutor` 接口 - 工具执行标准
- `AndroidToolManager` - 现有注册表实现
- `AndroidToolCallback` - JNI 回调接口
- `ShowToastTool` - 现有工具实现示例

### Established Patterns
- 工具注册: Map<String, ToolExecutor>
- 返回格式: JSON 字符串 ({"success": true/false, ...})
- 错误处理: 返回包含 error 字段的 JSON

### Integration Points
- AndroidToolManager.initialize() - 添加注解扫描逻辑
- tools.json - 提供工具清单（通过 JNI 传递）
- NativeAgent - 通过 AndroidToolCallback 接收调用

</code_context>

<deferred>
## Deferred Ideas

- 动态重载/热更新 - 后续迭代
- 工具版本管理 - 后续迭代
- 权限检查框架 - 后续迭代
- 插件系统 - 后续迭代

</deferred>

---

*Phase: v15-03-android-registry*
*Context gathered: 2026-03-05*
