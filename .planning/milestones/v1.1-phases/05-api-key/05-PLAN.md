# Phase 5: API Key Configuration

## Goal
通过配置文件为 Agent 提供 LLM API Key

## Tasks

### Task 1: 创建配置文件示例
- [x] 在 `app/src/main/assets/` 创建 `config.json` 示例文件
- [x] 包含 apiKey 和 baseUrl 字段

### Task 2: 修改 NativeNanobotApiAdapter
- [x] 添加从 assets 读取配置文件的方法
- [x] 将配置传递给 C++ Agent

### Task 3: 修改 native_agent.cpp
- [x] 修改 nativeInitialize 接受配置 JSON 字符串
- [x] 解析配置并设置 API Key 和 Base URL

### Task 4: 测试编译
- [x] 运行 `./gradlew assembleDebug`
- [x] 验证 APK 构建成功
