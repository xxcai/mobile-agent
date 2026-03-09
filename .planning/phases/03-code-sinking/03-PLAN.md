---
phase: "03"
plan: "01"
subsystem: android-architecture
tags: [android, cleanup, resource]
dependency_graph:
  requires: []
  provides:
    - "清理 app 模块冗余资源"
  affects:
    - "app/src/main/res/layout/"
    - "app/src/main/res/drawable/"
    - "app/src/main/assets/dist/"
tech_stack:
  patterns:
    - "资源清理"
key_files:
  deleted:
    - "app/src/main/res/layout/activity_main.xml"
    - "app/src/main/res/layout/item_message.xml"
    - "app/src/main/res/layout/item_message_user.xml"
    - "app/src/main/res/layout/item_thinking.xml"
    - "app/src/main/res/drawable/bg_edit_text.xml"
    - "app/src/main/res/drawable/bg_send_button.xml"
    - "app/src/main/assets/dist/"
  kept:
    - "app/src/main/res/values/strings.xml"
    - "app/src/main/res/values/colors.xml"
    - "app/src/main/res/values/themes.xml"
    - "app/src/main/res/drawable/ic_launcher.xml"
    - "app/src/main/res/xml/network_security_config.xml"
    - "app/src/main/assets/tools.json (已移至 agent-android)"
decisions:
  - "layout 文件不再使用，可删除"
  - "部分 drawable 文件不再使用，可删除"
  - "dist 目录无引用，可删除"
  - "必要的资源文件保留在 app 模块"
requirements_completed: []

---

# Phase 3 Plan 01: 清理 app 模块冗余资源

## Objective

清理 app 模块中不再使用的资源文件，删除冗余代码，保持模块整洁。

## Tasks

### Task 1: 删除冗余 layout 文件

```bash
# 删除不再使用的 layout 文件
rm app/src/main/res/layout/activity_main.xml
rm app/src/main/res/layout/item_message.xml
rm app/src/main/res/layout/item_message_user.xml
rm app/src/main/res/layout/item_thinking.xml
```

**Verification:**
- [ ] 文件已删除
- [ ] app 模块仍能编译

### Task 2: 删除冗余 drawable 文件

```bash
# 删除不再使用的 drawable 文件
rm app/src/main/res/drawable/bg_edit_text.xml
rm app/src/main/res/drawable/bg_send_button.xml
```

**Verification:**
- [ ] 文件已删除
- [ ] 无其他文件引用这些 drawable

### Task 3: 删除 dist 目录

```bash
# 删除无用的 dist 目录
rm -rf app/src/main/assets/dist/
```

**Verification:**
- [ ] 目录已删除
- [ ] app 模块仍能编译

### Task 4: 验证最终状态

```bash
# 验证编译通过
./gradlew :app:assembleDebug
```

**Verification:**
- [ ] 编译成功
- [ ] APK 正常生成

## Must Haves

- [ ] app 模块编译通过
- [ ] 无冗余资源文件残留
- [ ] 必要的资源文件保留完整

## Summary

删除 7 个冗余资源文件/目录：
- 4 个 layout 文件
- 2 个 drawable 文件
- 1 个 dist 目录

保留必要的 AndroidManifest 引用资源。

---
*Plan: 03-01*
