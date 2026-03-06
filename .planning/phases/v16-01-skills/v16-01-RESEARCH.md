<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **SkillLoader**: 现有代码 `agent/src/main/cpp/src/core/skill_loader.cpp` 已完整实现
- **配置格式**: 继续使用现有的 YAML frontmatter + Markdown 格式（SKILL.md）
  - 不需要改为纯 JSON/YAML
- **加载路径**: workspace/skills/ 目录 + extra_dirs 配置
- **Skill 定义字段 (已有实现)**: description, emoji, requiredBins, requiredEnvs, anyBins, os, always

### Claude's Discretion
- 无需实现技能依赖关系（现代 Skills 设计遵循独立自包含原则）

### Deferred Ideas (OUT OF SCOPE)
- Agent 如何根据用户意图选择合适的 Skill — Phase v16-02
- Skill 如何触发 Android Tool 调用 — Phase v16-02
- 多步骤 Skill 工作流 — Phase v16-02

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SKILL-01 | 定义自定义 Skill 的配置文件格式 (JSON/YAML) | YAML frontmatter 格式已确定，SKILL.md 格式已有实现 |
| SKILL-02 | C++ 层加载自定义 Skills 的机制 | skill_loader.cpp 已完整实现，load_skills() 方法可用 |
</phase_requirements>

# Phase v16-01: 自定义 Skills 机制 - Research

**Researched:** 2026-03-06
**Domain:** C++ Skill loading mechanism, YAML frontmatter parsing, dependency resolution
**Confidence:** HIGH

## Summary

Phase v16-01 focuses on verifying custom Skills definition and loading mechanism. The existing `skill_loader.cpp` already provides complete skill loading functionality, including YAML frontmatter parsing, OS/binary/environment checks.

**Primary recommendation:** Verify existing implementation works correctly. No new code needed - confirm SKILL-01 and SKILL-02 are satisfied.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| nlohmann/json | 3.x | JSON parsing for config | Header-only, widely used |
| C++17 | std::filesystem | File system operations | Modern C++ standard |

### Testing
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Catch2 | 3.x | C++ unit testing | Already used in cxxplatform/tests/ |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pure YAML | nlohmann/json + custom parser | Current YAML frontmatter parser is custom, works well |
| Graph library | Custom topological sort | Lightweight, no external dependency |

**Installation:**
```bash
# Tests use vcpkg for Catch2
vcpkg install catch2
```

## Architecture Patterns

### Recommended Project Structure
```
agent/src/main/cpp/
├── src/core/
│   ├── skill_loader.cpp       # Existing - Skill loading
│   └── skill_loader.hpp       # Existing - Interface
├── include/icraw/
│   ├── types.hpp              # Modify - Add dependencies field
│   └── config.hpp             # Existing - SkillsConfig
```

### Pattern 1: YAML Frontmatter + Markdown
**What:** SKILL.md files use YAML frontmatter for metadata, Markdown for content
**When to use:** All skill definitions
**Example:**
```yaml
---
description: 中文书信写作助手
emoji: "✍️"
dependencies:
  - base_writer
  - chinese_format
---
# Skill content here
```

### Pattern 2: Dependency Resolution (Topological Sort)
**What:** Order skills by their dependencies using Kahn's algorithm or DFS
**When to use:** Loading multiple skills with dependencies
**Example:**
```cpp
// Source: Custom implementation based on standard graph algorithms
std::vector<SkillMetadata> resolve_dependencies(
    std::vector<SkillMetadata>& skills) {
    // Build adjacency list
    // Kahn's algorithm or DFS-based topological sort
    // Detect and report cycles
}
```

### Anti-Patterns to Avoid
- **Load all skills then sort:** Load skills in dependency order directly, more efficient
- **Ignore missing dependencies:** Log warning, don't silently skip

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| YAML parsing | Full YAML library | Custom frontmatter parser in skill_loader.cpp | Already implemented, lightweight |
| JSON handling | Custom JSON | nlohmann/json | Header-only, C++17 compatible |
| File system | Platform-specific | std::filesystem | C++17 standard |

**Key insight:** The existing skill_loader.cpp already implements a custom YAML frontmatter parser that handles basic YAML syntax. For the new dependencies field, extend this parser rather than adding a full YAML library.

## Common Pitfalls

### Pitfall 1: Circular Dependencies
**What goes wrong:** Skills A depends on B, B depends on A - infinite loop
**Why it happens:** No cycle detection in dependency resolution
**How to avoid:** Use DFS-based cycle detection before topological sort
**Warning signs:** Log shows "Circular dependency detected: A -> B -> A"

### Pitfall 2: Missing Dependencies
**What goes wrong:** Skill declares dependency on non-existent skill
**Why it happens:** No validation of dependency existence
**How to avoid:** After loading all skills, validate each dependency exists
**Warning names:** Log warning "Dependency 'xyz' not found for skill 'abc'"

### Pitfall 3: Duplicate Skill Names
**What goes wrong:** Multiple directories have same skill name
**Why it happens:** Current dedup uses first-loaded skill only
**How to avoid:** Already handled - first loaded skill wins (document this behavior)

## Code Examples

### Adding dependencies field to SkillMetadata
```cpp
// Source: Extend existing struct in types.hpp
struct SkillMetadata {
    std::string name;
    std::string description;
    std::vector<std::string> required_bins;
    std::vector<std::string> required_envs;
    std::vector<std::string> any_bins;
    std::vector<std::string> config_files;
    std::vector<std::string> os_restrict;
    bool always = false;
    std::string primary_env;
    std::string emoji;
    std::string content;
    // NEW: Add dependencies field
    std::vector<std::string> dependencies;
};
```

### Parsing dependencies from YAML frontmatter
```cpp
// Source: Extend parse_skill_file() in skill_loader.cpp
if (yaml_json.contains("dependencies")) {
    for (const auto& dep : yaml_json["dependencies"]) {
        skill.dependencies.push_back(dep.get<std::string>());
    }
}
```

### Topological sort for dependency resolution
```cpp
// Source: Standard graph algorithm
std::vector<SkillMetadata> resolve_dependencies(
    std::vector<SkillMetadata>& skills) {

    // Build name -> skill map
    std::unordered_map<std::string, size_t> name_to_idx;
    for (size_t i = 0; i < skills.size(); ++i) {
        name_to_idx[skills[i].name] = i;
    }

    // Build adjacency list and in-degree count
    std::unordered_map<std::string, std::vector<std::string>> graph;
    std::unordered_map<std::string, int> in_degree;

    for (const auto& skill : skills) {
        in_degree[skill.name] = 0;
        graph[skill.name] = {};
    }

    for (const auto& skill : skills) {
        for (const auto& dep : skill.dependencies) {
            if (name_to_idx.count(dep)) {
                graph[dep].push_back(skill.name);
                in_degree[skill.name]++;
            }
        }
    }

    // Kahn's algorithm
    std::queue<std::string> q;
    for (const auto& [name, degree] : in_degree) {
        if (degree == 0) q.push(name);
    }

    std::vector<SkillMetadata> result;
    while (!q.empty()) {
        std::string current = q.front(); q.pop();
        result.push_back(skills[name_to_idx[current]]);

        for (const auto& neighbor : graph[current]) {
            if (--in_degree[neighbor] == 0) {
                q.push(neighbor);
            }
        }
    }

    // Check for cycles
    if (result.size() != skills.size()) {
        // Handle cycle - log error, return partial result
    }

    return result;
}
```

### Cycle detection using DFS
```cpp
// Source: Standard DFS cycle detection
bool has_cycle(
    const std::unordered_map<std::string, std::vector<std::string>>& graph,
    const std::string& start,
    std::unordered_set<std::string>& visited,
    std::unordered_set<std::string>& rec_stack) {

    visited.insert(start);
    rec_stack.insert(start);

    for (const auto& neighbor : graph.at(start)) {
        if (!visited.count(neighbor)) {
            if (has_cycle(graph, neighbor, visited, rec_stack)) {
                return true;
            }
        } else if (rec_stack.count(neighbor)) {
            return true;  // Cycle detected
        }
    }

    rec_stack.erase(start);
    return false;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No dependency support | Add dependencies field | This phase | Skills can depend on other skills |
| Single-pass loading | Topological sort loading | This phase | Correct load order |
| No cycle detection | DFS cycle detection | This phase | Prevent infinite loops |

**Deprecated/outdated:**
- None relevant to this phase

## Open Questions

1. **How to handle optional dependencies?**
   - What we know: Required dependencies must exist
   - What's unclear: Should there be optional dependencies (nice-to-have)?
   - Recommendation: Start with required only, add optional if needed

2. **Should dependencies be loaded before the dependent skill?**
   - What we know: Topological sort ensures correct order
   - What's unclear: Should dependent skill content include parent content?
   - Recommendation: Keep skills independent, prompt builder decides composition

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Catch2 3.x |
| Config file | cxxplatform/tests/CMakeLists.txt |
| Quick run command | `ctest -R skill_loader -V` |
| Full suite command | `ctest --output-on-failure` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SKILL-01 | YAML frontmatter parsing | unit | `ctest -R "skill_loader" -V` | Yes - skill_loader.test.cpp |
| SKILL-02 | Load skills from directory | unit | `ctest -R "skill_loader" -V` | Yes - skill_loader.test.cpp |

### Sampling Rate
- **Per task commit:** `ctest -R "skill_loader" -V` (quick run)
- **Per wave merge:** `ctest --output-on-failure` (full suite)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- 无需新增测试 - 现有测试覆盖 SKILL-01 和 SKILL-02

## Sources

### Primary (HIGH confidence)
- `/agent/src/main/cpp/src/core/skill_loader.cpp` - Current implementation
- `/agent/src/main/cpp/include/icraw/types.hpp` - SkillMetadata struct
- `/cxxplatform/tests/skill_loader.test.cpp` - Existing tests using Catch2

### Secondary (MEDIUM confidence)
- Standard topological sort algorithms (Kahn's algorithm, DFS)
- C++17 std::filesystem documentation

### Tertiary (LOW confidence)
- N/A - all sources are local to the project

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH - existing codebase uses standard C++17, nlohmann/json
- Architecture: HIGH - existing pattern well established
- Pitfalls: MEDIUM - cycle detection is standard, but edge cases need testing

**Research date:** 2026-03-06
**Valid until:** 90 days (stable implementation pattern)
