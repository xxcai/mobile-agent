# Phase v20-02: 重命名 - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

将所有 Nanobot 相关名称统一重命名为 MobileAgent，为后续代码迁移和 AAR 打包做准备。

</domain>

<decisions>
## Implementation Decisions

### 重命名范围
- **Java 层 (agent 模块):**
  - `NanobotApi.java` → `MobileAgentApi.java`
  - `NativeNanobotApi.java` → `NativeMobileAgentApi.java`

- **Java 层 (app 模块):**
  - `NativeNanobotApiAdapter.java` → `NativeMobileAgentApiAdapter.java`
  - `MainPresenter.java` 中 nanobot 相关方法重命名
  - `MainActivity.java` 中 nanobot 相关引用更新
  - 测试文件中相应更新

- **C++ 层:**
  - 仅注释中的 "nanobot" 引用（代码中实际无 Nanobot 命名）

### 执行顺序
1. 先重命名 agent 模块中的接口和类
2. 再更新 app 模块中的引用
3. 最后更新测试文件

### 需要更新的文件（当前 Nanobot 引用）
- `agent/src/main/java/com/hh/agent/library/api/NanobotApi.java`
- `agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java`
- `app/src/main/java/com/hh/agent/presenter/NativeNanobotApiAdapter.java`
- `app/src/main/java/com/hh/agent/presenter/MainPresenter.java`
- `app/src/main/java/com/hh/agent/MainActivity.java`
- `app/src/test/java/com/hh/agent/presenter/MainPresenterTest.java`

### Claude's Discretion
- 具体的文件名大小写规范（保持 camelCase）
- 测试文件更新的具体方式

</decisions>

<specifics>
## Specific Ideas

无特定需求，标准重命名操作。

</specifics>

<deferred>
## Deferred Ideas

- AAR 打包配置 — v2.0 里程碑的 out of scope 项
- 代码迁移到新模块 — v20-03

</deferred>

---

*Phase: v20-02-rename*
*Context gathered: 2026-03-06*
