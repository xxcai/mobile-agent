# Roadmap: 手机上的 AI Agent

## Milestones

- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v1.6 Roadmap

### Phase v16-01: 自定义 Skills 机制

**Goal:** 实现自定义 Skills 的定义和加载机制

**Requirements:**
- SKILL-01: 定义自定义 Skill 的配置文件格式 (JSON/YAML)
- SKILL-02: C++ 层加载自定义 Skills 的机制

**Success criteria:**
1. 可以在配置文件中定义 Skill
2. C++ 层能够加载 Skills

**Plans:**
1/1 plans complete

---

### Phase v16-02: Agent 调用 Tools

**Goal:** 实现 Agent 通过 Skill 调用 Android 内置 Tools 的能力

**Requirements:**
- CALL-01: Agent 能够解析 Skill 定义，调用对应的 Android Tools
- CALL-02: 支持多步骤的 Tool 调用链
- CALL-03: 处理 Tool 调用结果并返回给 Agent

**Success criteria:**
1. Agent 解析 Skill 并触发 Tool 调用
2. 多步骤 Tool 调用链正常工作
3. Tool 结果正确返回给 Agent

**Plans:**
1/1 plans complete

---

## Progress

| Milestone | Phase Range | Status |
|-----------|-------------|--------|
| v1.6 | v16-01 to v16-02 | ✓ Complete | 2026-03-06 |
| v1.5 | v15-01 to v15-03 | ✓ Complete | 2026-03-05 |
| v1.4 | 1-4 | ✓ Complete |

---

*Roadmap updated: 2026-03-06*
