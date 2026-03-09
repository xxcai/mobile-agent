# Phase 5: 接入文档 - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

设计 Agent 能力扩展机制，让接入方可以：
1. 拓展 Android Tools（可调用的工具）
2. 拓展 Skills（工作流模板）

</domain>

<decisions>
## Implementation Decisions

### 1. Android Tools 动态注册

**接口设计：**
- 复用现有 `ToolExecutor` 接口
- 构造函数传入 `Application Context`，Tool 自行处理 Activity 依赖

**ToolExecutor 扩展方法：**
```java
public interface ToolExecutor {
    String getName();
    String execute(org.json.JSONObject args);

    // 新增方法
    String getDescription();      // 返回工具功能描述，如 "显示Toast消息"
    String getArgsDescription();  // 返回参数描述，如 "message: 消息内容, duration: 时长"
    String getArgsSchema();       // 返回参数 JSON Schema
}
```

**tools.json 动态生成：**
- 生成时机：同步生成（在 AndroidToolManager 注册工具时）
- 传递方式：JSON 字符串，通过现有 API 传给 C++ Agent
- 生成逻辑：遍历 tools 注册表，收集每个 Tool 的信息拼接

**JSON 生成逻辑：**
```
1. 遍历 tools Map (name -> ToolExecutor)
2. 对每个 Tool 调用:
   - getName() → enum 列表
   - getDescription() + getArgsDescription() → description 字段
   - getArgsSchema() → parameters Schema
3. 拼接完整 JSON 字符串
4. 通过 NativeMobileAgentApi 传给 C++
```

**配置格式（tools.json）：**
```json
{
  "version": 2,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "call_android_tool",
        "description": "调用 Android 设备功能。可用功能:\n- display_notification: ...\n- show_toast: ...",
        "parameters": {
          "properties": {
            "function": {
              "enum": ["display_notification", "show_toast", ...]
            }
          }
        }
      }
    }
  ]
}
```

**加载逻辑：**
- 时机：启动时加载（`AndroidToolManager.initialize()`）
- 方式：反射创建实例
- 失败处理：跳过无法加载的 Tool，记录日志继续运行
- 兼容：渐进式迁移，保留现有 6 个硬编码 Tool

### 2. Skills 混合模式

**统一目录：**
```
/sdcard/Android/data/com.hh.agent/files/.icraw/workspace/skills/
├── im_sender/          # 内置
├── chinese_writer/    # 内置
└── my_custom_skill/   # 用户自定义
```

**初始化逻辑：**
1. 执行时机：`WorkspaceManager.initialize()` 时
2. 检测方式：遍历内置 skills，逐个检查目标目录是否存在
3. 复制规则：如果目标不存在则复制，已存在则跳过
4. 后续用户在此目录创建/编辑 skill 不会被覆盖

**代码逻辑：**
```java
// 伪代码
for (String skillName : builtInSkills) {
    File targetDir = new File(skillsDir, skillName);
    if (!targetDir.exists()) {
        copyFromAssets("workspace/skills/" + skillName, targetDir);
    }
}
```

**加载逻辑：**
- Agent 启动时扫描 `{外部存储}/.icraw/workspace/skills/` 目录
- 所有 skills 放在同一目录，无内置/用户区分
- 重名时保留用户版本（首次复制时跳过）

### 3. SOUL.md/USER.md（暂不讨论）

**暂时方案：**
- 保持现状：一次性复制，用户自行管理编辑内容
- 暂不实现版本检测功能

</decisions>

<specifics>
## Specific Ideas

### Android Tools
- ToolExecutor 接口扩展：添加 getDescription(), getArgsDescription(), getArgsSchema()
- tools.json 动态生成：遍历 tools Map，收集信息拼接 JSON
- 构造函数传入 Application Context

### Skills 混合模式
- 统一目录：`{外部存储}/.icraw/workspace/skills/`
- 初始化：首次启动从 assets 复制内置 skills，跳过已有文件
- 目录结构：文件夹形式（每个 skill 一个目录含 SKILL.md）
- 扫描范围：只扫描根目录，不递归子目录
- 更新机制：不自动更新，用户手动管理

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AndroidToolManager.java` - 工具管理核心类 (line 44-49 硬编码注册)
- `tools.json` - 工具描述配置
- `WorkspaceManager.java` - 工作空间管理 (line 88-89 复制 skills)

### Integration Points
- 工具注册：`AndroidToolManager.initialize()`
- Skills 加载：Agent 启动时扫描 skills 目录
- 文件复制：`WorkspaceManager.copyAssetsToWorkspace()`

</code_context>

<deferred>
## Deferred Ideas

- SOUL/USER 版本检测 — 暂不讨论
- 插件机制（AAR 动态加载）— 复杂度高，后续 phase 再讨论

</deferred>

---

*Phase: 05-api-key*
*Context gathered: 2026-03-09*
