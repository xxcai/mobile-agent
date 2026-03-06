# Quick Task 4: 删除Vue前端实现

**Description:** 删除Vue前端实现，修改GSD相关上下文

**Status:** Ready for execution

## 删除清单

### 1. Vue前端目录
- `vue/` - 整个Vue前端项目目录

### 2. Git Worktree
- `.git/worktrees/mobile-agent-vue/` - Vue worktree 目录

### 3. Android相关文件
- `app/src/main/java/com/hh/agent/VueActivity.java` - Vue WebView Activity
- `app/src/main/java/com/hh/agent/LauncherActivity.java` - 启动Activity
- `app/src/main/res/layout/activity_vue.xml` - Vue布局
- `app/src/main/res/layout/activity_launcher.xml` - 启动页布局
- `app/src/main/assets/dist/` - Vue构建产物

### 4. AndroidManifest更新
- 移除 `VueActivity` 和 `LauncherActivity` 的声明

### 5. GSD上下文文件更新
- `.planning/codebase/STRUCTURE.md` - 移除Vue相关结构
- `.planning/codebase/CONCERNS.md` - 移除Vue相关问题
- `.planning/codebase/ARCHITECTURE.md` - 移除Vue相关架构

---

## 执行计划

### Task 1: 删除Vue目录和构建产物

**Action:**
```bash
rm -rf vue/
rm -rf app/src/main/assets/dist/
rm -rf .git/worktrees/mobile-agent-vue/
```

### Task 2: 删除Android相关Java文件

**Action:**
```bash
rm -f app/src/main/java/com/hh/agent/VueActivity.java
rm -f app/src/main/java/com/hh/agent/LauncherActivity.java
rm -f app/src/main/res/layout/activity_vue.xml
rm -f app/src/main/res/layout/activity_launcher.xml
```

### Task 3: 更新AndroidManifest.xml

**Action:**
移除:
- VueActivity 声明
- LauncherActivity 声明
- 相关的intent-filter

### Task 4: 更新GSD上下文文件

**Action:**
从以下文件移除Vue相关引用:
- `.planning/codebase/STRUCTURE.md`
- `.planning/codebase/CONCERNS.md`
- `.planning/codebase/ARCHITECTURE.md`

---

## 验证

1. 确认 `vue/` 目录已删除
2. 确认 AndroidManifest.xml 中无Vue相关Activity
3. 确认 GSD 上下文文件中无Vue引用
4. 验证项目可正常构建: `./gradlew assembleDebug`
