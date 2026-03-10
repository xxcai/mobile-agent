# Requirements: Mobile Agent

**Defined:** 2026-03-10

**Core Value:** 让用户通过自然对话，指挥手机自动完成日常任务。

## v1 Requirements

App 层动态注入 Android 工具

### Tool 注册接口

- [ ] **INJT-01**: App 层可以通过接口注册自定义 Tool 到 AndroidToolManager
- [ ] **INJT-02**: Tool 注册支持运行时动态添加（应用运行期间）
- [ ] **INJT-03**: 注册时需要提供 Tool 名称、描述和执行器

### Tool 生命周期管理

- [ ] **INJT-04**: 支持查询已注册的 Tool 列表
- [ ] **INJT-05**: 支持注销已注册的 Tool
- [ ] **INJT-06**: Tool 注册信息可以在 tools.json 中声明（静态）

### 动态 Tool 调用

- [ ] **INJT-07**: Agent 可以调用通过 App 层注册的 Tool
- [ ] **INJT-08**: Tool 执行结果可以返回给 Agent (LLM)
- [ ] **INJT-09**: 自定义 Tool 与内置 Tool 使用相同的调用通道

### 示例验证

- [ ] **INJT-10**: 提供 CustomToastTool 示例（App 层注册）
- [ ] **INJT-11**: 验证 CustomToastTool 可以被 Agent 正常调用

---

## v2 Requirements

暂无

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tool 版本管理 | 首次发布不需要版本控制 |
| Tool 权限控制 | 信任 App 层注册的所有 Tool |
| 远程 Tool 注册 | 仅支持本地注册 |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| INJT-01 | Phase 1 | Pending |
| INJT-02 | Phase 1 | Pending |
| INJT-03 | Phase 1 | Pending |
| INJT-04 | Phase 2 | Pending |
| INJT-05 | Phase 2 | Pending |
| INJT-06 | Phase 2 | Pending |
| INJT-07 | Phase 3 | Pending |
| INJT-08 | Phase 3 | Pending |
| INJT-09 | Phase 3 | Pending |
| INJT-10 | Phase 3 | Pending |
| INJT-11 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 11 total
- Mapped to phases: 11 ✓
- Unmapped: 0

---

*Requirements defined: 2026-03-10*
*Last updated: 2026-03-10 after v2.2 roadmap created*
