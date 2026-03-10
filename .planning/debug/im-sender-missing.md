---
status: fixing
trigger: "im_sender skill没有复制到沙箱"
created: 2026-03-10T00:00:00Z
updated: 2026-03-10T00:00:00Z
---

## Current Focus

root_cause: im_sender在app和agent-core的assets中同时存在，导致Android构建时资源冲突
fix: 从agent-core中删除im_sender目录
next_action: 删除agent-core中的im_sender目录

## Symptoms

expected: im_sender应该出现在 /sdcard/Android/data/com.hh.agent/files/.icraw/workspace/skills/
actual: 只有chinese_writer存在
errors: []
reproduction: 把im_sender从agent-core移到app的assets后出现
started: 添加im_sender到app/assets后

## Eliminated

## Evidence

- timestamp: 2026-03-10T00:00:00Z
  checked: app/build.gradle配置
  found: 没有特殊assets合并配置，依赖agent-core通过implementation
  implication: 依赖默认的Android资源合并机制

- timestamp: 2026-03-10T00:00:00Z
  checked: WorkspaceManager代码
  found: 从assets/workspace/skills/目录动态读取skills列表并复制
  implication: 如果assets中有重复资源，可能导致冲突

- timestamp: 2026-03-10T00:00:00Z
  checked: agent-core和app的assets目录
  found: im_sender同时存在于app/src/main/assets/workspace/skills/和agent-core/src/main/assets/workspace/skills/
  implication: 重复的assets导致Android构建时资源冲突，im_sender无法正确打包

## Resolution

root_cause: im_sender在app和agent-core的assets中同时存在
fix: 删除了agent-core/src/main/assets/workspace/skills/im_sender目录
verification: 需要重新构建应用并在设备上测试验证
files_changed: [agent-core/src/main/assets/workspace/skills/im_sender/ (deleted)]
