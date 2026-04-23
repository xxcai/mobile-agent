# 提交治理与产物边界规范

本文档只约束提交过程和提交边界，不讨论运行时策略设计。

## 背景

当前仓库已经出现过几类会显著增加 review 和回退成本的问题：

- 同一笔提交同时包含源码、AAR、临时日志和分析产物
- 热路径源码改动和文档、构建产物混在一起
- 本地分析目录 `tmp/` 中的日志、截图、解包结果、验证工程被顺手带入版本控制

这类问题不会直接改变功能正确性，但会明显降低代码审查效率，并放大回归定位成本。

## 目标

后续所有提交都按“职责单一、边界清晰、可独立 review”的原则组织：

- 源码提交：只包含源码、Skill、文档
- 构建提交：只包含需要同步的 AAR 产物
- 临时分析资料：只保留在本地，不进入仓库

## 入库规则

### 允许进入仓库

- `agent-core/`、`agent-android/`、`app/src/main/assets/workspace/skills/`
- `docs/`
- 已经被仓库正式跟踪的 SDK AAR
  - [agent-android-debug.aar](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/app/libs/agent-android-debug.aar)
  - [agent-core-debug.aar](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/app/libs/agent-core-debug.aar)
  - [agent-screen-vision-debug.aar](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/app/libs/agent-screen-vision-debug.aar)

### 不允许进入仓库

- `tmp/` 下的所有内容
- 本地 logcat 输出、截图、事件导出、分析脚本临时产物
- AAR 解包目录、临时 consumer 工程、临时验证工程
- 与当前功能实现无关的本地调试文件

## 提交拆分规则

### 1. 源码提交

源码提交只允许包含：

- `agent-core`
- `agent-android`
- `skills`
- `docs`

源码提交中不应包含：

- `app/libs/*.aar`
- `tmp/`
- 本地日志、截图、验证目录

### 2. 构建产物提交

只有在源码已经稳定、且确实需要同步 SDK 产物时，才单独提交 AAR。

构建产物提交只允许包含：

- `app/libs/agent-android-debug.aar`
- `app/libs/agent-core-debug.aar`
- `app/libs/agent-screen-vision-debug.aar`

构建产物提交中不应再混入源码或 Skill 改动。

说明：

- 仓库当前对 `app/libs/*.aar` 使用了 ignore 规则
- 这些 AAR 之所以仍可更新，是因为它们已经被 Git 跟踪
- 如果需要提交新的 AAR 更新，应显式 add 已跟踪文件，不要把它和源码改动混在同一笔提交里

## 热路径文件特殊约束

以下文件属于高风险热路径，修改时必须保持提交粒度更小：

- [agent_loop.cpp](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/agent-core/src/main/cpp/src/core/agent_loop.cpp)
- [mobile_agent.cpp](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/agent-core/src/main/cpp/src/mobile_agent.cpp)
- [agent_candidate_matcher.hpp](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/agent-core/src/main/cpp/src/core/agent_candidate_matcher.hpp)
- [agent_observation.hpp](/D:/Work/AI/WeLink/mobile-agent/mobile-agent/agent-core/src/main/cpp/src/core/agent_observation.hpp)

约束如下：

- 不与 AAR 混提
- 不与 `tmp/` 分析资料混提
- 不和无关文档大改混提
- 如果同时改 runtime 和 Skill，优先拆成两笔：先 runtime，再 Skill，或反之，但要保证每笔提交都自洽

## 建议提交流程

推荐统一使用以下顺序：

1. 完成功能修改
2. 检查 `git status --short`
3. 先提交源码、Skill、文档
4. 需要同步 SDK 产物时，再单独提交 AAR

推荐在提交前至少做一次人工检查：

```bash
git status --short
git diff --stat --cached
```

如果发现以下内容，应先移出提交范围：

- `tmp/`
- `*.log`
- 截图、分析导出
- 本地验证工程

## 审计结论摘要

当前仓库在历史上主要暴露过三类治理问题：

- 源码与二进制产物混提
- `tmp/` 临时产物入库
- 热路径改动提交过大，导致 review 和回退困难

本规范的目标不是改变历史，而是从当前分支开始，稳定建立新的提交边界。
