# Requirements: Mobile Agent - 接入真实项目

**Defined:** 2026-03-06
**Core Value:** 让用户通过自然对话，指挥手机自动完成日常任务。

## v2.0 Requirements

### AAR 打包 (暂不处理)

- [ ] **AAR-01**: agent 模块打包为 AAR（C++ 核心 + Android 管道 + JNI 适配层）— 暂不处理
- [ ] **AAR-02**: app 模块打包为 AAR（Android 平台逻辑）— 暂不处理
- [ ] **AAR-03**: AAR 发布配置（输出路径、依赖声明）— 暂不处理

### 代码清理

- [ ] **CLEAN-01**: 删除 vue/ 目录（Vue 相关代码）
- [ ] **CLEAN-02**: 清理 lib 模块中 HttpNanobotApi（HTTP 连接旧代码）
- [ ] **CLEAN-03**: 清理 lib 模块中 MockNanobotApi（Mock 实现）
- [ ] **CLEAN-04**: 清理 NanobotConfig 配置类
- [ ] **CLEAN-05**: 清理相关测试代码

### 重命名

- [ ] **RENAME-01**: NanobotApi → MobileAgentApi
- [ ] **RENAME-02**: NativeNanobotApi → NativeMobileAgentApi
- [ ] **RENAME-03**: NativeNanobotApiAdapter → NativeMobileAgentApiAdapter
- [ ] **RENAME-04**: MainPresenter 中 nanobot 相关方法重命名
- [ ] **RENAME-05**: MainActivity 中 nanobot 相关引用更新
- [ ] **RENAME-06**: C++ 层 nanobot 相关命名更新

### 代码迁移

- [x] **MIGRATE-01**: 分析 agent 模块代码，识别可上移到 app 的平台逻辑
- [x] **MIGRATE-02**: 保留 Android 管道能力在 agent（AAR 需提供的能力）
- [x] **MIGRATE-03**: 将平台相关逻辑从 agent 移至 app 模块
- [x] **MIGRATE-04**: 确保重构后 build 正常

### 验证

- [ ] **VERIFY-01**: 打包 AAR 成功生成
- [x] **VERIFY-02**: 项目 assembleDebug 成功
- [x] **VERIFY-03**: 重命名后无编译错误
- [x] **VERIFY-04**: 原有功能（聊天、Tool 调用）正常工作

## v2.1 Requirements

Deferred to future release.

### 新功能方向

- **FEATURE-01**: 更多 Android Tools
- **FEATURE-02**: 语音交互
- **FEATURE-03**: MCP 集成

## Out of Scope

| Feature | Reason |
|---------|--------|
| AAR 打包发布 | 本里程碑只要求本地编译通过 |
| Vue 前端 | 已决定走原生路线 |
| HTTP 连接本地 nanobot | 已决定走远程大模型 |
| 旧架构兼容 | 重构目的是清理技术债务 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AAR-01 | — | Out of Scope |
| AAR-02 | — | Out of Scope |
| AAR-03 | — | Out of Scope |
| CLEAN-01 | Phase v20-01 | Pending |
| CLEAN-02 | Phase v20-01 | Pending |
| CLEAN-03 | Phase v20-01 | Pending |
| CLEAN-04 | Phase v20-01 | Pending |
| CLEAN-05 | Phase v20-01 | Pending |
| RENAME-01 | Phase v20-02 | Pending |
| RENAME-02 | Phase v20-02 | Pending |
| RENAME-03 | Phase v20-02 | Pending |
| RENAME-04 | Phase v20-02 | Pending |
| RENAME-05 | Phase v20-02 | Pending |
| RENAME-06 | Phase v20-02 | Pending |
| MIGRATE-01 | Phase v20-03 | Complete |
| MIGRATE-02 | Phase v20-03 | Complete |
| MIGRATE-03 | Phase v20-03 | Complete |
| MIGRATE-04 | Phase v20-04 | Complete |
| VERIFY-02 | Phase v20-04 | Complete |
| VERIFY-03 | Phase v20-04 | Complete |
| VERIFY-04 | Phase v20-04 | Complete |

**Coverage:**
- v2.0 requirements: 19 total (3 out of scope)
- Mapped to phases: 19
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-06*
*Last updated: 2026-03-06 after initial definition*
