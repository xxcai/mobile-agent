# Phase 3: API Integration - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

创建 NativeNanobotApi 实现，替换现有 HTTP 实现。

**Requirements:** API-01, API-02, API-03

</domain>

<decisions>
## Implementation Decisions

### Prior Phases
- Phase 1: libicraw.so 生成 ✓
- Phase 2: JNI Bridge 完成 ✓

### This Phase
- 创建 NativeNanobotApi 实现 NanobotApi 接口
- 实现 getSession, getHistory, sendMessage 方法

### 架构设计
- agent模块定位是用来替代lib模块的
- app模块仅负责ui显示
- 需要设计接口层，同时兼容
  - agent在PC，通过adb http转发的形式交互
  - agent在agent模块，通过代码调用的形式交互

### 接口对比分析 (已完成)
| lib模块接口 | agent模块接口 | 状态 |
|------------|---------------|------|
| createSession | 已实现 | ✓ |
| getSession | 已实现 | ✓ |
| sendMessage | 已实现 (nativeSendMessage) | ✓ |
| getHistory | 已实现 | ✓ |

</decisions>

<specifics>
## Specific Ideas

需要创建 Java 类实现现有的 NanobotApi 接口，与现有 HttpNanobotApi 接口兼容。

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 03-api-integration*
*Context gathered: 2026-03-03*
