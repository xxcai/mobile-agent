<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Test Skill**: im_sender (发送 IM 消息), located in agent/src/main/assets/workspace/skills/im_sender/
- **Function**: 根据用户描述的目标人名，搜索联系人并发送消息
- **New Tools**: search_contacts, send_im_message (mock implementation)
- **Implementation**: Mock 模拟（不依赖真实 APP）
- **Confirmation logic**: 多个匹配项 - 同时返回所有结果，让用户选择；单个匹配项 - 直接发送，不询问

### Claude's Discretion
- Skill 触发 Tool 调用的具体实现方式
- 多步骤 Tool 调用链的内部实现细节

### Deferred Ideas (OUT OF SCOPE)
- 完整的联系人搜索实现 — 需要访问系统联系人数据库
- 真实的 IM 发送实现 — 需要集成即时通讯 SDK

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CALL-01 | Agent 能够解析 Skill 定义，调用对应的 Android Tools | SkillLoader 已有完整实现，ToolRegistry 通过 tools.json 加载工具定义，call_android_tool 路由到 AndroidToolManager |
| CALL-02 | 支持多步骤的 Tool 调用链 | AgentLoop 已有 handle_tool_calls 方法支持多个 Tool 顺序调用，Skill 内容定义多步骤流程 |
| CALL-03 | 处理 Tool 调用结果并返回给 Agent | Tool 执行结果通过 tool message 返回给 LLM，构成对话上下文 |

</phase_requirements>

# Phase v16-02: Agent 调用 Tools - Research

**Researched:** 2026-03-06
**Domain:** Android Tool integration, C++ to Java JNI, Skill-to-Tool bridging
**Confidence:** HIGH

## Summary

Phase v16-02 implements the ability for Agent to call Android Tools through Skills. The existing architecture already provides:
1. **ToolRegistry** - loads tool definitions from tools.json and executes tools
2. **call_android_tool** - gateway function that routes to Android via JNI callback
3. **AndroidToolManager** - routes tool names to Java ToolExecutor implementations
4. **AgentLoop** - handles multiple sequential tool calls and returns results to LLM

**Primary recommendation:** Extend existing architecture by:
1. Adding search_contacts and send_im_message to tools.json enum
2. Creating mock Java ToolExecutor implementations
3. Creating test Skill im_sender with multi-step workflow

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| AndroidToolCallback | Existing | JNI callback interface | Already implemented |
| ToolExecutor | Existing | Java tool implementation interface | Already defined |
| AndroidToolManager | Existing | Routes tool names to executors | Already implemented |
| ToolRegistry | Existing | C++ tool loading and execution | Already implemented |

### Testing
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Catch2 | 3.x | C++ unit testing | Already in use |

**Installation:**
```bash
# No new dependencies needed - all existing components
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/assets/
├── tools.json                    # MODIFY - Add new tool enum values
└── workspace/skills/
    └── im_sender/                # NEW - Test skill
        └── SKILL.md

agent/src/main/java/com/hh/agent/library/tools/
├── SearchContactsTool.java       # NEW - Mock contact search
└── SendImMessageTool.java        # NEW - Mock IM sender
```

### Pattern 1: Tool Registration Flow
**What:** LLM generates tool call -> ToolRegistry::execute_tool -> g_android_tools.call_tool -> AndroidToolManager.callTool -> ToolExecutor.execute

**When to use:** All Android tool invocations

**Example:**
```cpp
// Source: tool_registry.cpp line 306-328
// For call_android_tool, params contains {"function": "tool_name", "args": {...}}
tools_["call_android_tool"] = [this](const nlohmann::json& params) -> std::string {
    std::string function_name = params.value("function", "");
    nlohmann::json tool_args = params.value("args", nlohmann::json::object());
    return icraw::g_android_tools.call_tool(function_name, tool_args);
};
```

### Pattern 2: Java ToolExecutor Implementation
**What:** Implement ToolExecutor interface, register in AndroidToolManager

**When to use:** Adding new Android tools

**Example:**
```java
// Source: ShowToastTool.java pattern
public class ShowToastTool implements ToolExecutor {
    private final Activity activity;

    public ShowToastTool(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getName() {
        return "show_toast";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            String message = args.getString("message");
            // Implementation...
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("result", "Toast shown");
            return result.toString();
        } catch (Exception e) {
            // Error handling...
        }
    }
}
```

### Pattern 3: Multi-step Tool Chain
**What:** Skill defines sequence of tool calls, Agent executes them sequentially

**When to use:** Complex workflows like "search then send message"

**Example:**
```
# SKILL.md content
当用户请求"给XXX发送消息"时：
1. 使用 search_contacts 搜索联系人 XXX
2. 如果返回多个结果，让用户选择具体是哪个人（通过工号区分）
3. 确定目标后，使用 send_im_message 发送消息
4. 返回发送结果给用户
```

### Anti-Patterns to Avoid
- **Skip tools.json update:** Always update enum when adding new tools - LLM needs schema
- **Direct JNI calls:** Use AndroidToolManager route, not direct native calls
- **Sync calls in background thread:** Tool calls are synchronous, keep on main thread or use proper async

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tool routing | Custom routing | AndroidToolManager | Already handles name->executor mapping |
| JNI callback | New JNI layer | AndroidToolCallback | Already implemented |
| Tool result format | Custom format | {"success": bool, "result": string} | Already standardized |

**Key insight:** The existing call_android_tool infrastructure already handles the complete flow from C++ to Java. New tools only need to be added to the enum and implement ToolExecutor.

## Common Pitfalls

### Pitfall 1: Missing tool in enum
**What goes wrong:** Tool registered in AndroidToolManager but not in tools.json enum - LLM cannot see it
**Why it happens:** tools.json defines the schema the LLM sees
**How to avoid:** Always update both AndroidToolManager.java (register executor) AND tools.json (add to enum)
**Warning signs:** LLM says "tool not found" even though Java code exists

### Pitfall 2: JSON argument parsing errors
**What goes wrong:** Tool receives malformed JSON, throws exception
**Why it happens:** ToolExecutor receives raw JSON from C++ nlohmann::json
**How to avoid:** Wrap in try-catch, return {"success": false, "error": "..."} on exception
**Warning signs:** "execution_failed" error in tool result

### Pitfall 3: Mock returns unexpected format
**What goes wrong:** Mock tool returns format different from what Skill expects, breaks workflow
**Why it happens:** No contract between Skill prompts and tool output formats
**How to avoid:** Document expected output format in SKILL.md or tools.json description
**Warning signs:** LLM cannot parse tool result, workflow fails at step 2+

## Code Examples

### Adding new tool to tools.json
```json
// Source: app/src/main/assets/tools.json
{
  "type": "function",
  "function": {
    "name": "call_android_tool",
    "description": "调用 Android 设备功能。可用功能:\n- display_notification: 显示通知\n- show_toast: 显示Toast\n- read_clipboard: 读取剪贴板\n- take_screenshot: 截屏\n- search_contacts: 搜索联系人\n- send_im_message: 发送IM消息",
    "parameters": {
      "type": "object",
      "properties": {
        "function": {
          "type": "string",
          "description": "要调用的功能名称",
          "enum": ["display_notification", "show_toast", "read_clipboard", "take_screenshot", "search_contacts", "send_im_message"]
        },
        "args": {
          "type": "object",
          "description": "功能参数，JSON 对象格式"
        }
      },
      "required": ["function", "args"]
    }
  }
}
```

### Registering new tool in AndroidToolManager
```java
// Source: AndroidToolManager.java initialize() method
public void initialize() {
    // ... existing tools ...

    // NEW: Register mock IM tools
    tools.put("search_contacts", new SearchContactsTool(getActivity()));
    tools.put("send_im_message", new SendImMessageTool(getActivity()));
    Log.i("AndroidToolManager", "Registered 2 new tools: search_contacts, send_im_message");
}
```

### Mock search_contacts implementation
```java
// Source: New file SearchContactsTool.java
public class SearchContactsTool implements ToolExecutor {
    private final Activity activity;

    public SearchContactsTool(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getName() {
        return "search_contacts";
    }

    @Override
    public String execute(JSONObject args) {
        try {
            String query = args.optString("query", "");

            // Mock data - in real implementation, query system contacts
            JSONArray results = new JSONArray();

            // Simulate search results
            if (query.contains("张三")) {
                results.put(new JSONObject()
                    .put("name", "张三")
                    .put("id", "001")
                    .put("department", "技术部"));
                results.put(new JSONObject()
                    .put("name", "张三")
                    .put("id", "002")
                    .put("department", "市场部"));
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("result", results);
            return result.toString();
        } catch (Exception e) {
            return errorJson("execution_failed", e.getMessage()).toString();
        }
    }

    private JSONObject errorJson(String error, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("success", false);
            obj.put("error", error);
            obj.put("message", message);
        } catch (Exception ignored) {}
        return obj;
    }
}
```

### Skill im_sender SKILL.md
```yaml
---
description: 发送即时通讯消息
emoji: "💬"
always: false
---
# IM Sender Skill

当用户请求"给XXX发送消息"时，执行以下步骤：

1. 使用 **search_contacts** 工具搜索联系人，参数 `{"query": "XXX"}`
2. 解析返回结果：
   - 如果返回多个联系人：列出所有选项，让用户确认要发送给哪一个（通过 id 区分）
   - 如果只返回一个：直接使用该联系人
   - 如果没有结果：告知用户未找到联系人
3. 确定目标联系人后，使用 **send_im_message** 工具发送消息，参数：
   - `{"contact_id": "xxx", "message": "用户想发送的消息内容"}`
4. 返回发送结果给用户

## 输出格式示例

搜索联系人返回：
```json
{"success": true, "result": [{"name": "张三", "id": "001", "department": "技术部"}]}
```

发送消息返回：
```json
{"success": true, "result": "消息已发送给 张三(001)"}
```
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single tool calls | Multi-step tool chain via Skill | This phase | Complex workflows possible |
| No IM tools | search_contacts + send_im_message | This phase | Enables IM use case |
| Hardcoded tools | tools.json enum-driven | v1.5 | Easy to add new tools |

**Deprecated/outdated:**
- None relevant to this phase

## Open Questions

1. **Should Skill execution be automated or LLM-driven?**
   - What we know: Current architecture has LLM read Skill content and decide tool calls
   - What's unclear: Should there be explicit Skill execution engine?
   - Recommendation: Keep LLM-driven for flexibility, SKILL.md is prompt guidance

2. **How to handle user confirmation in multi-step flows?**
   - What we know: Current architecture returns tool results to LLM as messages
   - What's unclear: Should confirmation be blocking or non-blocking?
   - Recommendation: Non-blocking - return results to LLM, let LLM prompt user

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Catch2 3.x (C++) + Android JUnit (Java) |
| Config file | cxxplatform/tests/CMakeLists.txt |
| Quick run command | `ctest -R tool_registry -V` |
| Full suite command | `ctest --output-on-failure` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CALL-01 | Skill can trigger Android Tool | integration | Manual test via chat | No - new |
| CALL-02 | Multi-step tool chain | integration | Manual test with im_sender | No - new |
| CALL-03 | Tool result returned to Agent | unit | Check tool result format | Yes - existing |

### Sampling Rate
- **Per task commit:** Manual test im_sender skill flow
- **Per wave merge:** Full C++ test suite (`ctest --output-on-failure`)
- **Phase gate:** im_sender skill works end-to-end

### Wave 0 Gaps
- `agent/src/main/java/com/hh/agent/library/tools/SearchContactsTool.java` - Mock contact search tool
- `agent/src/main/java/com/hh/agent/library/tools/SendImMessageTool.java` - Mock IM send tool
- `agent/src/main/assets/workspace/skills/im_sender/SKILL.md` - Test skill definition

## Sources

### Primary (HIGH confidence)
- `agent/src/main/cpp/src/tools/tool_registry.cpp` - Tool registration and call_android_tool routing
- `agent/src/main/java/com/hh/agent/library/AndroidToolManager.java` - Tool executor routing
- `agent/src/main/java/com/hh/agent/library/tools/ShowToastTool.java` - Example ToolExecutor implementation
- `app/src/main/assets/tools.json` - Tool schema definition

### Secondary (MEDIUM confidence)
- `agent/src/main/cpp/src/core/agent_loop.cpp` - Tool call handling and result processing

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH - all components already exist and work
- Architecture: HIGH - proven architecture from v1.4/v1.5
- Pitfalls: HIGH - common mistakes well understood from previous phases

**Research date:** 2026-03-06
**Valid until:** 90 days (stable implementation pattern)
