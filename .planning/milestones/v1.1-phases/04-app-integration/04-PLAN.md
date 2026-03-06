# Phase 4: App Integration Plan

## Goal
将 NativeNanobotApi 集成到 MainActivity

## Background Analysis

### Current Architecture
- **MainActivity** 使用 `MainPresenter` 处理业务逻辑
- **MainPresenter** 依赖 `NanobotApi` 接口 (来自 `com.hh.agent.lib.api`)
- **MainPresenter** 目前支持两种 API 类型: `MOCK` 和 `HTTP`

### NativeNanobotApi 位置
- 位于: `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java`
- 实现接口: `com.hh.agent.library.api.NanobotApi` (不同于 lib 模块的接口)

### 问题
两个 `NanobotApi` 接口在不同包中:
- `com.hh.agent.lib.api.NanobotApi` - lib 模块
- `com.hh.agent.library.api.NanobotApi` - agent 模块

## Tasks

### Task 1: 分析 MainActivity 使用的 NanobotApi 接口
- [ ] 查看 MainPresenter 中 NanobotApi 的使用方式
- [ ] 确认需要修改的 API 方法:
  - `getSession(String sessionKey)` - 获取会话
  - `getHistory(String sessionKey, int maxMessages)` - 获取历史消息
  - `sendMessage(String content, String sessionKey)` - 发送消息

### Task 2: 修改 MainPresenter 使用 NativeNanobotApi
- [ ] 在 MainPresenter.ApiType 枚举中添加 `NATIVE` 类型
- [ ] 在 `createApi()` 方法中添加 NativeNanobotApi 的创建逻辑
- [ ] 注意事项:
  - NativeNanobotApi 是单例模式，需要调用 `initialize(configPath)` 初始化
  - 需要处理 agent 模块的依赖引入
  - 需要处理 Message 和 Session 模型的兼容性问题（来自不同包）

### Task 3: 测试编译
- [ ] 运行 `./gradlew assembleDebug` 验证编译
- [ ] 检查是否有接口不匹配的问题
- [ ] 检查运行时依赖是否正确加载

## 潜在问题与解决方案

### 问题 1: Message/Session 模型不兼容
- lib 模块: `com.hh.agent.lib.model.Message`
- agent 模块: `com.hh.agent.library.model.Message`

**解决方案**: 在 MainPresenter 中做模型转换，或者统一使用 lib 模块的模型

### 问题 2: Native 库未加载
- 需要确保 `NativeAgent` 库已正确加载
- 可能需要在 Application 或 Activity 中提前加载

## 实施步骤

1. 首先在 MainPresenter 中添加 NATIVE API 类型支持
2. 处理模型转换逻辑
3. 添加 NativeNanobotApi 的初始化代码
4. 编译测试
