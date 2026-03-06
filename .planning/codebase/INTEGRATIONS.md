# External Integrations

**Analysis Date:** 2026-03-06

## APIs & External Services

**LLM API:**
- Primary external API for AI chat functionality
- 调用方式: C++ 引擎直接调用 LLM 提供商的 HTTP API
- 配置: 通过 local.properties 或代码配置 API Key
- 支持提供商: 可扩展（当前支持 minimax 等）

**API Request Format:**
```json
// HTTP 请求到 LLM 提供商
POST https://api.minimaxi.com/v1/chat/completions
Headers:
  - Authorization: Bearer {api_key}
  - Content-Type: application/json
Body:
{
  "model": "...",
  "messages": [...],
  "tools": [...]
}
```

## Data Storage

**In-Memory:**
- Session and message storage: In-memory in C++ Agent 引擎
- No persistent database
- Session 数据在 App 生命周期内保留

**No External Database:**
- 所有数据存储在手机本地
- 无需远程服务器

## Authentication & Identity

**API Key 认证:**
- LLM API Key 配置在 local.properties 或代码中
- 无用户身份管理系统

## Monitoring & Observability

**None:**
- No error tracking service (e.g., Crashlytics, Sentry)
- No analytics or monitoring
- Native Android logging only (Logcat)

**Logging Approach:**
- Android `Log` class for debug logging
- Native C++ logging via Android log library

## CI/CD & Deployment

**Build System:**
- Gradle 8.12.1 with AGP 8.3.2

**Hosting:**
- Not applicable (Android app distributed via APK)

**CI Pipeline:**
- None detected

## Environment Configuration

**Required Configuration:**
- LLM API Key: 在 local.properties 中配置 `apiKey=your-key`
- 网络权限: INTERNET permission in AndroidManifest.xml

**可选配置:**
- LLM 提供商选择
- API 端点自定义

## Architecture Evolution

**旧架构 (已废弃):**
- App 通过 HTTP 连接到 PC 上的 Nanobot 服务
- 需要 adb reverse 端口转发
- 依赖 PC 端服务运行

**新架构 (当前):**
- Agent 引擎编译进 APK，直接运行在手机本地
- C++ 层直接调用 LLM API
- 无需 PC 端服务，无需 adb
- 保护隐私，离线可用

## Key Integration Points

**C++ Agent 引擎:**
- `agent/src/main/cpp/` - C++ 源代码
- JNI 层: `agent/src/main/java/`

**配置:**
- `local.properties` - API Key 配置
- `lib/src/main/java/com/hh/agent/lib/config/` - 运行时配置

---

*Integration audit: 2026-03-06*
*Updated: 架构从 PC 端服务改为本地运行*
