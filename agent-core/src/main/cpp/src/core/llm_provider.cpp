#include "icraw/core/llm_provider.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <algorithm>
#include <sstream>
#include <chrono>

namespace icraw {

namespace {

bool base_url_contains(const std::string& base_url, const std::string& needle) {
    return base_url.find(needle) != std::string::npos;
}

const char* describe_thinking_type(const ChatCompletionRequest& request,
                                   const nlohmann::json& body) {
    if (body.contains("thinking")
            && body["thinking"].is_object()
            && body["thinking"].contains("type")
            && body["thinking"]["type"].is_string()) {
        return body["thinking"]["type"].get_ref<const std::string&>().c_str();
    }
    if (request.enable_thinking.has_value()) {
        return *request.enable_thinking ? "enabled_not_emitted" : "disabled_not_emitted";
    }
    return "absent";
}

std::string extract_reasoning_text(const nlohmann::json& node) {
    if (node.is_null()) {
        return "";
    }
    if (node.is_string()) {
        return node.get<std::string>();
    }
    if (node.is_array()) {
        std::string combined;
        for (const auto& item : node) {
            combined += extract_reasoning_text(item);
        }
        return combined;
    }
    if (node.is_object()) {
        if (node.contains("text") && node["text"].is_string()) {
            return node["text"].get<std::string>();
        }
        if (node.contains("reasoning") && node["reasoning"].is_string()) {
            return node["reasoning"].get<std::string>();
        }
        if (node.contains("content")) {
            return extract_reasoning_text(node["content"]);
        }
    }
    return "";
}

std::string normalize_snapshot_or_delta(const std::string& current, std::string& snapshot) {
    if (current.empty()) {
        return "";
    }
    if (!snapshot.empty() && current.rfind(snapshot, 0) == 0) {
        std::string delta = current.substr(snapshot.size());
        snapshot = current;
        return delta;
    }

    snapshot += current;
    return current;
}

std::string json_string_or_empty(const nlohmann::json& node, const char* key) {
    if (!node.contains(key) || node[key].is_null()) {
        return "";
    }
    if (!node[key].is_string()) {
        return "";
    }
    return node[key].get<std::string>();
}

} // namespace

// ============================================================================
// Stream Parser Factory
// ============================================================================

OpenAICompatibleVendor resolve_openai_compatible_vendor(const std::string& base_url) {
    if (base_url_contains(base_url, "minimax")) {
        return OpenAICompatibleVendor::MINIMAX;
    }

    if (base_url_contains(base_url, "dashscope.aliyuncs.com")) {
        return OpenAICompatibleVendor::QWEN;
    }

    if (base_url_contains(base_url, "bigmodel.cn") || base_url_contains(base_url, "zhipu")) {
        return OpenAICompatibleVendor::GLM;
    }

    return OpenAICompatibleVendor::GENERIC;
}

// ============================================================================
// OpenAI Stream Parser Implementation
// ============================================================================

OpenAIStreamParser::OpenAIStreamParser(ToolCallMatchMode mode)
    : initial_match_mode_(mode)
    , match_mode_(mode) {
}

void OpenAIStreamParser::reset_accumulators() {
    accumulators_by_index_.clear();
    accumulators_by_id_.clear();
    mode_detected_ = false;
    match_mode_ = initial_match_mode_;
    content_snapshot_.clear();
    reasoning_snapshot_.clear();
}

OpenAIStreamParser::ToolCallAccumulator* OpenAIStreamParser::find_or_create_accumulator(
    const std::string& id, int index) {
    
    // Auto-detect mode from first chunk
    if (!mode_detected_ && (match_mode_ == ToolCallMatchMode::AUTO)) {
        // If we see index >= 0, prefer index-based matching
        if (index >= 0) {
            match_mode_ = ToolCallMatchMode::BY_INDEX;
            mode_detected_ = true;
        } else if (!id.empty()) {
            match_mode_ = ToolCallMatchMode::BY_ID;
            mode_detected_ = true;
        }
    }
    
    // Use appropriate lookup based on mode
    if (match_mode_ == ToolCallMatchMode::BY_INDEX && index >= 0) {
        return &accumulators_by_index_[index];
    } else if (!id.empty()) {
        return &accumulators_by_id_[id];
    } else if (index >= 0) {
        // Fallback to index
        return &accumulators_by_index_[index];
    }
    
    return nullptr;
}

void OpenAIStreamParser::accumulate_tool_call(const nlohmann::json& tc_json) {
    std::string id = json_string_or_empty(tc_json, "id");
    int index = tc_json.value("index", -1);
    
    ToolCallAccumulator* acc = find_or_create_accumulator(id, index);
    if (!acc) return;
    
    // Update id if provided
    if (!id.empty()) {
        acc->id = id;
    }
    acc->index = index;
    
    // Update function info if present
    if (tc_json.contains("function")) {
        const auto& func = tc_json["function"];

        std::string name = json_string_or_empty(func, "name");
        if (!name.empty()) {
            acc->name = name;
        }

        std::string args = json_string_or_empty(func, "arguments");
        if (!args.empty()) {
            acc->arguments += args;
        }
    }
}

std::vector<ToolCall> OpenAIStreamParser::get_accumulated_tool_calls() {
    std::vector<ToolCall> result;
    
    // Collect from index-based accumulators first
    for (auto& [index, acc] : accumulators_by_index_) {
        if (!acc.name.empty() && !acc.arguments.empty()) {
            ToolCall tc;
            tc.id = acc.id.empty() ? "tool_" + std::to_string(index) : acc.id;
            tc.name = acc.name;
            tc.index = index;
            
            // Try to parse arguments as JSON
            try {
                tc.arguments = nlohmann::json::parse(acc.arguments);
            } catch (...) {
                // Keep as string if not valid JSON
                tc.arguments = acc.arguments;
            }
            
            result.push_back(std::move(tc));
        }
    }
    
    // Collect from id-based accumulators (if not already in index-based)
    for (auto& [id, acc] : accumulators_by_id_) {
        bool found = false;
        for (const auto& tc : result) {
            if (tc.id == id) {
                found = true;
                break;
            }
        }
        
        if (!found && !acc.name.empty() && !acc.arguments.empty()) {
            ToolCall tc;
            tc.id = id;
            tc.name = acc.name;
            tc.index = acc.index;
            
            try {
                tc.arguments = nlohmann::json::parse(acc.arguments);
            } catch (...) {
                tc.arguments = acc.arguments;
            }
            
            result.push_back(std::move(tc));
        }
    }
    
    return result;
}

bool OpenAIStreamParser::is_stream_end(const std::string& sse_event) const {
    // Check for explicit [DONE] marker
    if (sse_event.find("data: [DONE]") != std::string::npos) {
        return true;
    }
    // Check for finish_reason in the JSON - indicates stream is ending
    if (sse_event.find("finish_reason") != std::string::npos) {
        return true;
    }
    return false;
}

bool OpenAIStreamParser::parse_chunk(const std::string& sse_event,
                                     ChatCompletionResponse& response) {
    if (sse_event.empty()) {
        return false;
    }

    // Don't return early on stream end - we need to parse finish_reason from JSON
    // The is_stream_end check is done after JSON parsing below

    if (sse_event.find("data:") != 0) {
        return false;
    }
    
    std::string json_str = sse_event.substr(5);
    
    size_t start = json_str.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return false;
    size_t end = json_str.find_last_not_of(" \t\r\n");
    json_str = json_str.substr(start, end - start + 1);
    
    if (json_str == "[DONE]") {
        response.is_stream_end = true;
        response.tool_calls = get_accumulated_tool_calls();
        return true;
    }
    
    try {
        nlohmann::json chunk_json = nlohmann::json::parse(json_str);

        if (!chunk_json.contains("choices") || chunk_json["choices"].empty()) {
            return false;
        }

        const auto& choice = chunk_json["choices"][0];
        response.finish_reason = json_string_or_empty(choice, "finish_reason");

        if (choice.contains("delta")) {
            const auto& delta = choice["delta"];

            if (delta.contains("content") && !delta["content"].is_null()) {
                const std::string raw_content = delta["content"].get<std::string>();
                response.content = normalize_snapshot_or_delta(raw_content, content_snapshot_);
            }

            if (delta.contains("reasoning_content") && !delta["reasoning_content"].is_null()) {
                const std::string raw_reasoning = delta["reasoning_content"].get<std::string>();
                response.reasoning_content =
                    normalize_snapshot_or_delta(raw_reasoning, reasoning_snapshot_);
            }

            if (delta.contains("reasoning_details") && !delta["reasoning_details"].is_null()) {
                const std::string raw_reasoning = extract_reasoning_text(delta["reasoning_details"]);
                response.reasoning_content =
                    normalize_snapshot_or_delta(raw_reasoning, reasoning_snapshot_);
            }
            
            if (delta.contains("tool_calls") && delta["tool_calls"].is_array()) {
                for (const auto& tc : delta["tool_calls"]) {
                    accumulate_tool_call(tc);
                }
            }
        }
        
        if (!response.finish_reason.empty()) {
            response.tool_calls = get_accumulated_tool_calls();
            // Stream ends when we receive a finish_reason (stop, tool_calls, etc.)
            response.is_stream_end = true;
        }
        
        return true;
        
    } catch (const nlohmann::json::parse_error&) {
        return false;
    }
}

// ============================================================================
// LLMProvider Base Implementation
// ============================================================================

nlohmann::json LLMProvider::build_request_body(const ChatCompletionRequest& request) const {
    nlohmann::json body;
    
    body["model"] = request.model;
    body["temperature"] = request.temperature;
    body["max_tokens"] = request.max_tokens;
    
    nlohmann::json messages = nlohmann::json::array();
    for (const auto& msg : request.messages) {
        messages.push_back(msg.to_json());
    }
    body["messages"] = messages;
    
    if (!request.tools.empty()) {
        body["tools"] = request.tools;
        body["tool_choice"] = request.tool_choice_auto ? "auto" : "required";
    }
    
    if (request.stream) {
        body["stream"] = true;
    }
    
    return body;
}

    
ChatCompletionResponse LLMProvider::parse_response(const nlohmann::json& response) const {
    ChatCompletionResponse result;

    if (!response.contains("choices") || response["choices"].empty()) {
        result.finish_reason = "error";
        return result;
    }

    const auto& choice = response["choices"][0];
    result.finish_reason = json_string_or_empty(choice, "finish_reason");

    if (choice.contains("message")) {
        const auto& message = choice["message"];

        // Check for reasoning_content (o1 model)
        if (message.contains("reasoning_content") && !message["reasoning_content"].is_null()) {
            result.reasoning_content = message["reasoning_content"].get<std::string>();
        }

        if (message.contains("reasoning_details") && !message["reasoning_details"].is_null()) {
            result.reasoning_content = extract_reasoning_text(message["reasoning_details"]);
        }

        if (message.contains("content") && !message["content"].is_null()) {
            result.content = message["content"].get<std::string>();
        }
        
        if (message.contains("tool_calls") && message["tool_calls"].is_array()) {
            for (const auto& tc : message["tool_calls"]) {
                ToolCall call;
                call.id = json_string_or_empty(tc, "id");
                
                if (tc.contains("function")) {
                    const auto& func = tc["function"];
                    call.name = json_string_or_empty(func, "name");
                    
                    std::string args_str = json_string_or_empty(func, "arguments");
                    if (args_str.empty()) {
                        args_str = "{}";
                    }
                    try {
                        call.arguments = nlohmann::json::parse(args_str);
                    } catch (const nlohmann::json::parse_error&) {
                        call.arguments = nlohmann::json::object();
                    }
                }
                
                result.tool_calls.push_back(std::move(call));
            }
        }
    }
    
    return result;
}

} // namespace icraw
