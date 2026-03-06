---
phase: v16-01
slug: skills
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase v16-01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Catch2 3.x |
| **Config file** | CMakeLists.txt (existing) |
| **Quick run command** | `ctest -R "skill" -V` |
| **Full suite command** | `ctest --output-on-failure` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `ctest -R "skill" -V`
- **After every plan wave:** Run `ctest --output-on-failure`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| v16-01-01-01 | 01 | 1 | SKILL-01 | unit | `ctest -R "skill_metadata" -V` | ⬜ pending |
| v16-01-01-02 | 01 | 1 | SKILL-02 | unit | `ctest -R "skill_loader" -V` | ⬜ pending |
| v16-01-01-03 | 01 | 1 | SKILL-03 | unit | `ctest -R "skill_dependencies" -V` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `cxxplatform/tests/skill_dependencies.test.cpp` — tests for dependency parsing, topological sort, cycle detection
- [ ] CMakeLists.txt update — add new test target if needed

*Existing Catch2 infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| YAML frontmatter parsing | SKILL-01 | Need real SKILL.md files | Create test SKILL.md with dependencies field, verify parse |
| OS/environment filtering | SKILL-02 | Platform-specific | Run on target platform |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
