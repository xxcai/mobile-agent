# Technology Stack: AI Agent Tool Design Patterns

**Project:** Mobile Agent - LLM -> Android Pipeline
**Researched:** 2026-03-05
**Confidence:** HIGH (based on existing implementation + MCP specification)

## Executive Summary

This research analyzes how AI coding agents (Claude Code, Cursor, GitHub Copilot) design and implement generic tools. Key findings:

1. **Tool Definition Schema**: AI agents use JSON Schema-based parameter definitions (compatible with OpenAI function calling)
2. **Tool Discovery**: Dynamic registration with capability negotiation (MCP pattern)
3. **Execution Model**: Tool name + JSON arguments pattern, returns structured JSON results
4. **Skills vs Tools Distinction**: Skills are behavioral guidance (text), tools are callable functions

For the Mobile Agent project, the existing implementation already follows these patterns. Recommendations focus on formalizing the schema and extending to support more Android-specific tools.

## Recommended Stack

### Core Tool Definition Format

| Component | Current | Recommended | Why |
|-----------|---------|-------------|-----|
| Tool Schema | Custom JSON | JSON Schema (OpenAI compatible) | Industry standard, LLM-native |
| Parameters | Custom format | inputSchema (MCP standard) | Enables LLM function calling |
| Registration | Hardcoded | Dynamic registry | Extensibility |
| Discovery | tools.json | tools/list + notifications | MCP-compatible |

### Tool Definition Schema

Based on MCP (Model Context Protocol) and OpenAI Function Calling:

```json
{
  "name": "tool_name",
  "description": "What this tool does and when to use it",
  "inputSchema": {
    "type": "object",
    "properties": {
      "param_name": {
        "type": "string|integer|boolean|object|array",
        "description": "What this parameter means",
        "default": "default value (optional)",
        "enum": ["option1", "option2"]  // for constrained values
      }
    },
    "required": ["param1", "param2"]  // mandatory parameters
  }
}
```

### Current Implementation Analysis

The existing `tools.json` in the project:

```json
{
  "version": 1,
  "tools": [
    {
      "name": "show_toast",
      "description": "显示 Toast 消息到屏幕",
      "params": {
        "message": {
          "type": "string",
          "required": true,
          "description": "要显示的消息内容"
        },
        "duration": {
          "type": "integer",
          "required": false,
          "default": 2000,
          "description": "显示时长(毫秒)"
        }
      }
    }
  ]
}
```

**Gap**: Current format is close but uses `params` instead of `inputSchema`. Should migrate to MCP standard for LLM compatibility.

### C++ Tool Schema (ToolRegistry)

Current implementation in `/agent/src/main/cpp/include/icraw/types.hpp`:

```cpp
struct ToolSchema {
    std::string name;
    std::string description;
    nlohmann::json parameters;  // JSON Schema object
};
```

Registration pattern in tool_registry.cpp:

```cpp
ToolSchema schema;
schema.name = "read_file";
schema.description = "Read the contents of a file from the filesystem";
schema.parameters = nlohmann::json{
    {"type", "object"},
    {"properties", {
        {"path", {
            {"type", "string"},
            {"description", "The path to the file to read"}
        }}
    }},
    {"required", {"path"}}
};

tools_[schema.name] = [this](const nlohmann::json& params) {
    return this->read_file_tool(params);
};
tool_schemas_.push_back(std::move(schema));
```

## Tool Registration Patterns

### Pattern 1: Registry with Callbacks (Current)

```cpp
class ToolRegistry {
    std::unordered_map<std::string, std::function<std::string(const nlohmann::json&)>> tools_;
    std::vector<ToolSchema> tool_schemas_;

public:
    void register_tool(const ToolSchema& schema, ToolExecutor executor);
    std::string execute_tool(const std::string& name, const nlohmann::json& args);
    std::vector<ToolSchema> get_tool_schemas() const;
};
```

### Pattern 2: MCP-style Dynamic Discovery

```
1. Client sends: tools/list request
2. Server responds: tools array with full schemas
3. Client caches: available tools for LLM context
4. Server notifies: tools/list_changed when tools update
```

### Pattern 3: Android-specific (Recommended for Mobile Agent)

```java
public interface ToolExecutor {
    String execute(JSONObject args) throws JSONException;
}

public class AndroidToolManager {
    private Map<String, ToolExecutor> tools = new HashMap<>();

    public void registerTool(String name, ToolExecutor executor);
    public String callTool(String toolName, String argsJson);
    public List<ToolSchema> getToolSchemas();
}
```

## JSON Parameter Schema Best Practices

### Required vs Optional Parameters

```json
{
  "type": "object",
  "properties": {
    "required_param": {
      "type": "string",
      "description": "Must be provided"
    },
    "optional_param": {
      "type": "integer",
      "description": "Has a default, optional",
      "default": 100
    }
  },
  "required": ["required_param"]
}
```

### Enum Constraints

```json
{
  "properties": {
    "mode": {
      "type": "string",
      "enum": ["read", "write", "append"],
      "default": "read"
    }
  }
}
```

### Nested Objects

```json
{
  "properties": {
    "config": {
      "type": "object",
      "properties": {
        "timeout": {"type": "integer"},
        "retry": {"type": "integer"}
      }
    }
  }
}
```

### Array Parameters

```json
{
  "properties": {
    "files": {
      "type": "array",
      "items": {"type": "string"},
      "description": "List of file paths"
    }
  }
}
```

## Skills vs Tools Orchestration

### Skills: Behavioral Guidance

Skills are text-based instructions that define agent behavior:

```markdown
---
description: Chinese letter writing assistant
emoji: "✍️"
---

# 中文书信写作助手

You are a professional Chinese letter writing assistant.

## Capabilities
- Write formal business letters
- Draft invitations, thank you letters
```

Key characteristics:
- Stored as SKILL.md files with YAML frontmatter
- Loaded into LLM context as guidance
- NOT callable functions
- Used for persona, domain expertise, workflow guidance

### Tools: Executable Functions

Tools are callable functions with defined schemas:

```json
{
  "name": "show_toast",
  "description": "Display toast message on screen",
  "inputSchema": {...}
}
```

Key characteristics:
- Have explicit parameter schemas
- Executable by LLM function calling
- Return structured JSON results
- Registered in ToolRegistry

### Workflow Orchestration

Current project uses Skills for agent identity (SOUL.md) and Tools for capabilities. For Android pipeline:

1. **Skill**: Defines when to use Android tools (e.g., "When user asks to display something, use show_toast")
2. **Tool**: The actual executable function
3. **Pipeline**: LLM decides which tool to call based on skill guidance

## Tool Execution Flow

```
LLM Request → Tool Call (name + args) → ToolRegistry → Executor → Result → LLM Response
```

### Tool Call Format (OpenAI Compatible)

```json
{
  "tool_calls": [
    {
      "id": "call_123",
      "type": "function",
      "function": {
        "name": "show_toast",
        "arguments": "{\"message\": \"Hello\", \"duration\": 2000}"
      }
    }
  ]
}
```

### Tool Result Format

```json
{
  "role": "tool",
  "tool_call_id": "call_123",
  "content": "{\"success\": true}"
}
```

## Recommended Tool Schema Migration

### Current (needs update)

```json
{
  "name": "show_toast",
  "params": {...}
}
```

### Recommended (MCP/OpenAI compatible)

```json
{
  "name": "show_toast",
  "description": "显示 Toast 消息到屏幕",
  "inputSchema": {
    "type": "object",
    "properties": {
      "message": {
        "type": "string",
        "description": "要显示的消息内容"
      },
      "duration": {
        "type": "integer",
        "description": "显示时长(毫秒)",
        "default": 2000
      }
    },
    "required": ["message"]
  }
}
```

## Android Tool Examples

### show_toast

```json
{
  "name": "show_toast",
  "description": "Display a short message on Android screen",
  "inputSchema": {
    "type": "object",
    "properties": {
      "message": {
        "type": "string",
        "description": "Message text to display"
      },
      "duration": {
        "type": "integer",
        "description": "Duration in milliseconds",
        "default": 2000
      }
    },
    "required": ["message"]
  }
}
```

### start_activity

```json
{
  "name": "start_activity",
  "description": "Start an Android Activity by component name",
  "inputSchema": {
    "type": "object",
    "properties": {
      "package": {
        "type": "string",
        "description": "Package name (e.g., com.hh.agent)"
      },
      "class": {
        "type": "string",
        "description": "Activity class name (e.g., .MainActivity)"
      },
      "extra": {
        "type": "object",
        "description": "Intent extras as key-value pairs",
        "additionalProperties": {
          "type": "string"
        }
      }
    },
    "required": ["package", "class"]
  }
}
```

### Vibrate

```json
{
  "name": "vibrate",
  "description": "Trigger device vibration",
  "inputSchema": {
    "type": "object",
    "properties": {
      "pattern": {
        "type": "array",
        "items": {"type": "integer"},
        "description": "Vibration pattern: [delay, vibrate, pause, ...]"
      },
      "repeat": {
        "type": "integer",
        "description": "Repeat index, -1 for no repeat",
        "default": -1
      }
    },
    "required": ["pattern"]
  }
}
```

## Installation / Dependencies

### Required for Tool System

```bash
# No additional dependencies needed - using existing nlohmann/json
# Already included in project via agent/conanfile.py
```

### Key Dependencies

| Library | Purpose | Version |
|---------|---------|---------|
| nlohmann/json | JSON parsing/schemas | 3.10+ (conan) |
| Android SDK | Android platform tools | API 24+ |

## Sources

- MCP (Model Context Protocol) Specification - https://modelcontextprotocol.io/docs/learn/architecture
- OpenAI Function Calling - https://platform.openai.com/docs/guides/function-calling
- Existing implementation in: `/agent/src/main/cpp/src/tools/tool_registry.cpp`
- Existing tools.json: `/app/src/main/assets/tools.json`
- Skill definitions: `/cxxplatform/workspace/skills/*/SKILL.md`
