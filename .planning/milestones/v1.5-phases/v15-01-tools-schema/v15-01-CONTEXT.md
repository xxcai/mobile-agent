# Phase 1: 定义通用工具 schema - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

设计 `call_android_tool` 通用工具的 inputSchema，让 LLM 通过这个单一管道调用 Android 功能。

核心原则:
- 暴露给 LLM 的只有一个工具: call_android_tool
- function 参数使用 enum 限制可选值
- args 参数为通用 JSON 对象

</domain>

<decisions>
## Implementation Decisions

### schema 结构
- 工具名: `call_android_tool`
- 描述: 说明可用功能列表
- function 参数: 使用 enum 枚举可选功能
- args 参数: 通用 object 类型

### 可用功能列表 (enum)
- show_toast: 显示 Toast 消息
- display_notification: 显示通知
- read_clipboard: 读取剪贴板
- take_screenshot: 截屏

### Claude's Discretion
- 具体功能列表可以后续扩展
- args 的具体参数结构需要为每个功能定义
- function 描述可以在 description 中用自然语言说明

</decisions>

<specifics>
## Specific Ideas

```json
{
  "name": "call_android_tool",
  "description": "调用 Android 设备功能。可用功能:\n- display_notification: 显示通知, 参数: title, content\n- show_toast: 显示Toast, 参数: message, duration\n- read_clipboard: 读取剪贴板\n- take_screenshot: 截屏",
  "parameters": {
    "type": "object",
    "properties": {
      "function": {
        "type": "string",
        "description": "要调用的功能名称",
        "enum": ["display_notification", "show_toast", "read_clipboard", "take_screenshot"]
      },
      "args": {
        "type": "object",
        "description": "功能参数"
      }
    },
    "required": ["function", "args"]
  }
}
```

</specifics>

<deferred>
## Deferred Ideas

- 具体每个 function 的参数结构定义
- 更多 Android 功能扩展

</deferred>

---

*Phase: v15-01-tools-schema*
*Context gathered: 2026-03-05*
