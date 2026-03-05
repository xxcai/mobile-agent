# Roadmap: Mobile Agent

## Milestones

- 📋 **v1.5 LLM → Android 调用管道** — Phases 1-5 (in progress)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

## v1.5 Roadmap

### Phase 1: 定义通用工具 schema

**Goal:** 设计并定义 `call_android_tool` 通用工具的 inputSchema

**Requirements:**
- PIPE-01

**Plans:**
- [x] v15-01-01-PLAN.md — 设计 call_android_tool schema，包含 function enum 和 args

---

### Phase 2: C++ 端工具改造

**Goal:** 修改 C++ Agent，让 LLM 只看到 call_android_tool 通用管道

**Requirements:**
- PIPE-02
- MODE-01

**Plans:**
- [ ] v15-02-01-PLAN.md — C++ 端只暴露 call_android_tool，Java 传递 tools schema

---

### Phase 3: Android 注册表实现

**Goal:** 实现 Android 端 function → Executor 注册表

**Requirements:**
- PIPE-03

---

### Phase 4: 内置功能实现

**Goal:** 实现若干内置 Android 功能

**Requirements:**
- SKILL-01

---

### Phase 5: 端到端验证

**Goal:** 验证完整管道工作正常

**Requirements:**
- SKILL-02
- SKILL-03

---

## Progress

| Milestone | Phase Range | Status |
|-----------|-------------|--------|
| v1.4 | 1-4 | ✓ Complete |
| v1.5 | 1-5 | ○ Not started |

---

*Roadmap updated: 2026-03-05*
