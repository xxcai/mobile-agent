# Phase v16-02: Agent 调用 Tools - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

实现 Agent 通过 Skill 调用 Android 内置 Tools 的能力。创建测试 Skill "发送 IM 消息"，验证多步骤 Tool 调用链。

</domain>

<decisions>
## Implementation Decisions

### 测试 Skill 设计
- **Skill 名称**: im_sender (发送 IM 消息)
- **位置**: 内置 Skill (agent/src/main/assets/workspace/skills/im_sender/)
- **功能**: 根据用户描述的目标人名，搜索联系人并发送消息

### 用户流程
1. 用户说"给张三发消息说我到了"
2. Skill 解析得到目标名字"张三"
3. 调用搜索工具搜索联系人
4. 如果有多个匹配：返回列表让用户选择
5. 如果只有一个匹配：直接发送消息

### Android Tools 扩展
- 在 call_android_tool 的 enum 中添加:
  - `search_contacts`: 搜索联系人，返回 JSON 数组
  - `send_im_message`: 发送 IM 消息，返回成功/失败
- 实现方式: Mock 模拟（不依赖真实 APP）

### 确认逻辑
- 多个匹配项：同时返回所有结果，让用户选择
- 单个匹配项：直接发送，不询问

</decisions>

<specifics>
## Specific Ideas

**Skill 示例指令:**
```
当用户请求"给XXX发送消息"时：
1. 使用 search_contacts 搜索联系人 XXX
2. 如果返回多个结果，让用户选择具体是哪个人（通过工号区分）
3. 确定目标后，使用 send_im_message 发送消息
4. 返回发送结果给用户
```

**Mock 数据:**
- search_contacts 返回: `[{"name": "张三", "id": "001"}, {"name": "张三", "id": "002"}]`
- send_im_message 返回: `{"success": true, "result": "消息已发送给 张三(001)"}`

</specifics>

\code_context
## Existing Code Insights

### Reusable Assets
- `AndroidTools::call_tool()` - 现有 Android Tool 调用接口
- `tools.json` - Tool schema 定义
- SkillLoader - 现有 Skill 加载机制

### Integration Points
- `call_android_tool` function 需要扩展 enum
- Android 端需要新增 Tool 实现类
- Skill 放在 workspace/skills/ 目录

</code_context>

<deferred>
## Deferred Ideas

- 完整的联系人搜索实现 — 需要访问系统联系人数据库
- 真实的 IM 发送实现 — 需要集成即时通讯 SDK

</deferred>

---

*Phase: v16-02-agent-tools*
*Context gathered: 2026-03-06*
