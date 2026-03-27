# LLM Provider 扩展指南

本文档说明当前 `agent-core` 中如何扩展新的 OpenAI-like LLM provider。

适用范围：

- 新厂商整体遵循 OpenAI-compatible `chat/completions`
- 只需要少量 request / stream parser 差异
- 不修改 `AgentLoop`、Java bridge、Android UI

如果你要看的不是 provider 扩展，而是 Android tool / skill 扩展，请参考：

- [Android 工具扩展指南](./android-tool-extension.md)
- [Android Skill 扩展指南](./android-skill-extension.md)

## 当前架构

当前 LLM 请求层已经收敛为“factory + 共享基类 + provider 子类”：

- `LLMProvider`
  - 抽象接口
- `OpenAICompatibleProviderBase`
  - 通用 OpenAI-compatible request/response 主流程
- `GenericOpenAIProvider`
- `MinimaxProvider`
- `QwenProvider`
- `GlmProvider`
- `LLMProviderFactory`
  - 根据 `base_url` 选择具体 provider

关键代码位置：

- [llm_provider.hpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/include/icraw/core/llm_provider.hpp)
- [llm_provider.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/llm_provider.cpp)
- [openai_compatible_provider_base.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/openai_compatible_provider_base.cpp)
- [llm_provider_factory.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/llm_provider_factory.cpp)
- [providers/](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/include/icraw/core/providers)

运行时调用点在：

- [mobile_agent.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/mobile_agent.cpp)

## 扩展原则

新增 provider 时，遵循这三个原则：

1. `base_url` 识别只放在 factory 前
2. 厂商差异只放在 provider 子类
3. 不把厂商判断写回共享基类

也就是说，后续新增 provider，不应该再去共享主流程里堆：

- `if (base_url.find(...))`
- `if (vendor == ...)`

## 步骤 1：新增 vendor 识别

先在 vendor 枚举和识别函数里加新厂商。

代码位置：

- [llm_provider.hpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/include/icraw/core/llm_provider.hpp)
- [llm_provider.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/llm_provider.cpp)

需要改两处：

1. 在 `OpenAICompatibleVendor` 里新增枚举值
2. 在 `resolve_openai_compatible_vendor(const std::string& base_url)` 里识别该厂商

示意：

```cpp
enum class OpenAICompatibleVendor {
    GENERIC,
    MINIMAX,
    QWEN,
    GLM,
    FOO,
};
```

```cpp
if (base_url_contains(base_url, "foo.example.com")) {
    return OpenAICompatibleVendor::FOO;
}
```

约束：

- 这里负责“识别是谁”
- 不要在这里放 request/body/parser 差异

## 步骤 2：新增 provider 类

新增一对 provider 文件。

建议路径：

```text
agent-core/src/main/cpp/include/icraw/core/providers/foo_provider.hpp
agent-core/src/main/cpp/src/core/providers/foo_provider.cpp
```

头文件模板：

```cpp
#pragma once

#include "icraw/core/openai_compatible_provider_base.hpp"

namespace icraw {

class FooProvider : public OpenAICompatibleProviderBase {
public:
    FooProvider(const std::string& api_key,
                const std::string& base_url,
                const std::string& default_model);
    ~FooProvider() override = default;

    std::string get_provider_name() const override;

protected:
    void apply_request_quirks(const ChatCompletionRequest& request,
                              nlohmann::json& body) const override;
    std::unique_ptr<StreamParser> create_provider_stream_parser() const override;
};

} // namespace icraw
```

实现文件模板：

```cpp
#include "icraw/core/providers/foo_provider.hpp"

namespace icraw {

FooProvider::FooProvider(const std::string& api_key,
                         const std::string& base_url,
                         const std::string& default_model)
    : OpenAICompatibleProviderBase(api_key, base_url, default_model) {
    set_provider_name("foo");
    refresh_stream_parser();
}

std::string FooProvider::get_provider_name() const {
    return "FooProvider";
}

void FooProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                       nlohmann::json& body) const {
    (void) request;
    (void) body;
}

std::unique_ptr<StreamParser> FooProvider::create_provider_stream_parser() const {
    return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::AUTO);
}

} // namespace icraw
```

## 步骤 3：只在子类里放差异

共享基类已经负责这些通用能力：

- 组装 `/chat/completions` 请求
- 发送 non-stream / stream HTTP 请求
- 统一解析 OpenAI-compatible response
- 处理通用 `reasoning_content` / `reasoning_details`

新增 provider 时，通常只需要改两个扩展点：

### `apply_request_quirks(...)`

适合放这些差异：

- `reasoning_split=true`
- `thinking.type=enabled/disabled`
- 某个厂商要求的额外 body 字段

例子：

```cpp
void FooProvider::apply_request_quirks(const ChatCompletionRequest& request,
                                       nlohmann::json& body) const {
    if (request.enable_thinking.has_value()) {
        body["thinking"]["type"] = *request.enable_thinking ? "enabled" : "disabled";
    }
}
```

### `create_provider_stream_parser()`

适合放这些差异：

- `BY_INDEX`
- 特定 stream parser 模式

例子：

```cpp
std::unique_ptr<StreamParser> FooProvider::create_provider_stream_parser() const {
    return std::make_unique<OpenAIStreamParser>(OpenAIStreamParser::ToolCallMatchMode::BY_INDEX);
}
```

如果新厂商完全兼容通用 OpenAI-compatible：

- `apply_request_quirks(...)` 可以为空
- `create_provider_stream_parser()` 可以直接复用默认 `AUTO`

## 步骤 4：注册到 factory

代码位置：

- [llm_provider_factory.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/llm_provider_factory.cpp)

需要新增：

1. 头文件 include
2. `switch` 分支

示意：

```cpp
#include "icraw/core/providers/foo_provider.hpp"
```

```cpp
case OpenAICompatibleVendor::FOO:
    return std::make_shared<FooProvider>(api_key, base_url, default_model);
```

## 步骤 5：补 CMake

别忘了把新增文件接到构建里。

代码位置：

- [agent-core/src/main/cpp/CMakeLists.txt](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/CMakeLists.txt)

需要补两处：

- `ICRAW_SOURCES`
- `ICRAW_HEADERS`

## 步骤 6：补单测

代码位置：

- [llm_provider_tests.cpp](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/test/cpp/llm_provider_tests.cpp)

至少补三类测试：

1. vendor 识别测试
2. factory 返回正确 provider 类
3. 该 provider 的 request/parser 差异测试

推荐最小覆盖：

- `base_url -> FOO`
- `factory -> FooProvider`
- request body 是否带了特殊字段
- stream parser 是否按预期聚合 tool call / reasoning

## 验证命令

新增 provider 后，至少跑这两类验证：

```bash
cmake --build agent-core/build/native-tests/cmake-debug --target icraw_llm_provider_tests -j8
./agent-core/build/native-tests/cmake-debug/tests/icraw_llm_provider_tests
./gradlew :agent-core:externalNativeBuildDebug
```

如果有真实账号和 endpoint，再补人工回归：

- 正常问答
- stream 模式
- reasoning / thinking 开关
- tool call 聚合

## 当前已实现的 provider 参考

- [MinimaxProvider](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/providers/minimax_provider.cpp)
  - request quirk: `reasoning_split`
- [QwenProvider](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/providers/qwen_provider.cpp)
  - stream parser: `BY_INDEX`
- [GlmProvider](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/providers/glm_provider.cpp)
  - request quirk: `thinking.type`
- [GenericOpenAIProvider](/Users/caixiao/Workspace/projects/mobile-agent-disable-thinking/agent-core/src/main/cpp/src/core/providers/generic_openai_provider.cpp)
  - 无额外 quirk

## 一句话约束

新增 provider 的正确方式是：

- 在 factory 前识别 vendor
- 新增 provider 子类
- 差异只写在子类
- 补测试

不应该再回到共享主流程里继续堆厂商分支。
