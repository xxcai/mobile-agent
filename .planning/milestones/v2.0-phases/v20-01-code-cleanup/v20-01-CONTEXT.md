# Phase v20-01: 代码清理 - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

删除 Vue 相关代码和 lib 模块中的旧 HTTP 连接代码，为后续重命名和代码迁移做准备。

</domain>

<decisions>
## Implementation Decisions

### 清理范围
- vue/ 目录整个删除
- app 模块：简化 MainPresenter，删除 ApiType 枚举和 HTTP/MOCK 相关代码
- lib 模块：
  - 保留：model/Message.java, model/Session.java（被 app 使用）
  - 删除：NativeLib.java, api/NanobotApi.java, config/NanobotConfig.java, http/HttpNanobotApi.java, impl/MockNanobotApi.java, dto/*, 测试文件

### 执行顺序
1. 先修改 app 模块代码，移除对 lib 中待删除类的引用
2. 再删除 lib 模块中的文件
3. 最后删除 vue/ 目录

</decisions>

<specifics>
## Specific Ideas

无特定需求，标准清理操作。

</specifics>

<deferred>
## Deferred Ideas

- AAR 打包配置 — v2.0 里程碑的 out of scope 项
- lib 模块剩余代码的进一步清理 — 取决于 Phase v20-02 重命名后的状态

</deferred>

---

*Phase: v20-01-code-cleanup*
*Context gathered: 2026-03-06*
