# Codebase Concerns

**Analysis Date:** 2026-03-12

## Tech Debt

**过度宽泛的异常捕获:**
- Issue: 多处使用 `catch (Exception e)` 捕获所有异常,吞掉错误不进行适当处理
- Files: `agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java` (lines 72, 97, 149), `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallManager.java` (lines 122, 136), `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java` (lines 287, 327, 355), `app/src/main/java/com/hh/agent/tool/TakeScreenshotTool.java` (lines 58, 87, 111)
- Impact: 隐藏真实错误,难以调试,可能导致静默失败
- Fix approach: 根据具体异常类型处理,记录日志并返回有意义的错误信息

**不当的错误输出方式:**
- Issue: 使用 `e.printStackTrace()` 和 `System.out.println()` 进行日志输出
- Files: `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallManager.java` (lines 123, 137), `agent-android/src/main/java/com/hh/agent/android/presenter/NativeMobileAgentApiAdapter.java` (line 129), `agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` (lines 59, 85, 88, 91)
- Impact: 生产环境无法收集日志,难以追踪问题
- Fix approach: 使用 Android Log 类或统一的日志框架

**缺少内存泄漏检测:**
- Issue: 未集成 LeakCanary 进行运行时内存泄漏检测
- Files: N/A
- Impact: 内存泄漏难以被发现,可能导致应用 OOM
- Fix approach: 在 debug build 中添加 LeakCanary 依赖

**超大的类文件:**
- Issue: 多个类超过 200 行,职责过多
- Files: `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java` (372 lines), `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallManager.java` (269 lines), `agent-android/src/main/java/com/hh/agent/android/AgentActivity.java` (269 lines)
- Impact: 可维护性差,难以理解和测试
- Fix approach: 拆分职责,使用策略模式或命令模式重构

## Security Considerations

**明文流量允许:**
- Risk: `android:usesCleartextTraffic="true"` 允许 HTTP 明文通信
- Files: `app/src/main/AndroidManifest.xml` (line 14)
- Current mitigation: 有 networkSecurityConfig 配置
- Recommendations: 评估是否确实需要明文流量,尽量使用 HTTPS

**应用备份启用:**
- Risk: `android:allowBackup="true"` 允许应用数据备份
- Files: `app/src/main/AndroidManifest.xml` (line 8)
- Current mitigation: 无
- Recommendations: 如包含敏感数据应设为 false

**Activity 导出配置:**
- Risk: LauncherActivity 和部分 Activity 设置 `android:exported="true"`
- Files: `app/src/main/AndroidManifest.xml` (line 19), `agent-android/src/main/AndroidManifest.xml` (line 15)
- Current mitigation: LauncherActivity 需要作为入口
- Recommendations: 非必要 Activity 设置 exported="false",添加 permission 限制

## Performance Bottlenunks

**Handler 潜在泄漏:**
- Problem: MainPresenter 和 MockVoiceRecognizer 创建绑定到主线程 Looper 的 Handler
- Files: `agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java` (line 37), `app/src/main/java/com/hh/agent/voice/MockVoiceRecognizer.java` (line 26)
- Cause: Handler 持有外部类引用,如果外部类已销毁但 Handler 消息队列还有消息会导致泄漏
- Improvement path: 使用静态内部 Handler + WeakReference,或在 onDestroy 中清除消息

**缺少性能监控工具:**
- Problem: v2.4 虽然做了性能分析,但没有持续的性能监控
- Files: N/A
- Cause: 性能分析只在开发阶段进行
- Improvement path: 集成性能监控 SDK 或使用 Android Profiler 定期检查

## Fragile Areas

**悬浮球功能区:**
- Files: `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallManager.java`, `floating-ball/src/main/java/com/hh/agent/floating/FloatingBallView.java`, `floating-ball/src/main/java/com/hh/agent/floating/ContainerActivity.java`
- Why fragile: 最近多个 bug 修复与此模块相关(点击无反应、拖拽异常、点击外部无法关闭、标题栏不显示、悬浮球不恢复)
- Safe modification: 修改前需全面测试悬浮球显示/隐藏/拖拽/点击各种场景
- Test coverage: 无自动化测试

**Native Agent 交互层:**
- Files: `agent-core/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java`, `agent-android/src/main/java/com/hh/agent/android/presenter/NativeMobileAgentApiAdapter.java`
- Why fragile: JNI 交互边界容易出现崩溃,错误处理复杂
- Safe modification: 确保所有 native 调用都在try-catch中,添加回调结果校验
- Test coverage: 无

## Scaling Limits

**Tools 配置:**
- Current capacity: 工具注册通过 JSON 配置
- Limit: 动态注册工具需要手动调用 pushToolsJson,批量操作效率低
- Scaling path: 考虑使用 LiveData/Flow 响应式更新

**多模块通信:**
- Current capacity: 通过 AIDL 或直接依赖
- Limit: 模块间耦合较高,扩展性受限
- Scaling path: 定义清晰的模块接口,使用事件总线解耦

## Dependencies at Risk

**Markwon 库:**
- Risk: 4.6.2 版本较老 (最新 4.6.2,最后更新 2023 年)
- Impact: 可能存在安全漏洞,缺少新特性
- Migration plan: 评估迁移到更活跃的 Markdown 渲染库,如 commonmark

**AndroidX AppCompat:**
- Risk: 1.6.1 版本,虽然较新但非最新
- Impact: 兼容性风险
- Migration plan: 定期更新依赖版本

## Missing Critical Features

**自动化测试缺失:**
- Problem: 仅 app 模块有 3 个测试文件,其他核心模块(agent-android, agent-core, floating-ball)无测试
- Blocks: 重构风险高,难以保证代码质量

**错误恢复机制:**
- Problem: Native Agent 崩溃或超时没有重试机制
- Blocks: 生产环境稳定性

## Test Coverage Gaps

**悬浮球模块:**
- What's not tested: 悬浮球拖拽、点击、边界检测、窗口权限申请
- Files: `floating-ball/src/main/java/com/hh/agent/floating/*`
- Risk: UI 行为变更难以发现回归问题
- Priority: High

**Native 交互层:**
- What's not tested: JNI 调用、回调处理、工具注册/注销
- Files: `agent-core/src/main/java/com/hh/agent/library/api/*`
- Risk: Native 层崩溃无法被捕获
- Priority: High

**Android 工具执行:**
- What's not tested: 工具执行结果、权限检查、异常情况
- Files: `app/src/main/java/com/hh/agent/tool/*`, `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- Risk: 工具执行失败无法感知
- Priority: Medium

---

*Concerns audit: 2026-03-12*
