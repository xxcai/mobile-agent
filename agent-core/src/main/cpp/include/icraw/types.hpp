#pragma once

#include <string>
#include <vector>
#include <optional>
#include <unordered_map>
#include <filesystem>
#include <functional>
#include <nlohmann/json.hpp>

namespace icraw {

// --- Content Block ---

struct ContentBlock {
    std::string type;  // "text" | "tool_use" | "tool_result" | "thinking" | "image_url"
    // For text/thinking
    std::string text;
    // For tool_use
    std::string id;
    std::string name;
    nlohmann::json input;
    // For tool_result
    std::string tool_use_id;
    std::string content;
    // For image_url (OpenAI Vision API)
    std::string image_url;        // URL or base64 data URI
    std::string image_detail;     // "auto" | "low" | "high" (optional)

    nlohmann::json to_json() const {
        nlohmann::json j;
        j["type"] = type;
        
        if (type == "text" || type == "thinking") {
            j["text"] = text;
        } else if (type == "tool_use") {
            j["id"] = id;
            j["name"] = name;
            j["input"] = input;
        } else if (type == "tool_result") {
            j["tool_use_id"] = tool_use_id;
            j["content"] = content;
        } else if (type == "image_url") {
            nlohmann::json image_url_obj;
            image_url_obj["url"] = image_url;
            if (!image_detail.empty()) {
                image_url_obj["detail"] = image_detail;
            }
            j["image_url"] = image_url_obj;
        }
        
        return j;
    }
    
    static ContentBlock from_json(const nlohmann::json& j) {
        ContentBlock block;
        block.type = j.value("type", "");
        
        if (block.type == "text" || block.type == "thinking") {
            block.text = j.value("text", "");
        } else if (block.type == "tool_use") {
            block.id = j.value("id", "");
            block.name = j.value("name", "");
            block.input = j.value("input", nlohmann::json::object());
        } else if (block.type == "tool_result") {
            block.tool_use_id = j.value("tool_use_id", "");
            block.content = j.value("content", "");
        } else if (block.type == "image_url") {
            if (j.contains("image_url")) {
                const auto& img = j["image_url"];
                block.image_url = img.value("url", "");
                block.image_detail = img.value("detail", "auto");
            }
        }
        
        return block;
    }
    
    // Factory methods
    static ContentBlock make_text(const std::string& text) {
        ContentBlock block;
        block.type = "text";
        block.text = text;
        return block;
    }

    static ContentBlock make_think(const std::string& text) {
        ContentBlock block;
        block.type = "thinking";
        block.text = text;
        return block;
    }
    
    static ContentBlock make_tool_use(const std::string& id, 
                                       const std::string& name, 
                                       const nlohmann::json& input) {
        ContentBlock block;
        block.type = "tool_use";
        block.id = id;
        block.name = name;
        block.input = input;
        return block;
    }
    
    static ContentBlock make_tool_result(const std::string& tool_use_id, 
                                          const std::string& content) {
        ContentBlock block;
        block.type = "tool_result";
        block.tool_use_id = tool_use_id;
        block.content = content;
        return block;
    }
    
    // Image factory methods
    static ContentBlock make_image_url(const std::string& url, const std::string& detail = "auto") {
        ContentBlock block;
        block.type = "image_url";
        block.image_url = url;
        block.image_detail = detail;
        return block;
    }
    
    static ContentBlock make_image_base64(const std::string& base64_data, 
                                          const std::string& media_type = "image/png",
                                          const std::string& detail = "auto") {
        ContentBlock block;
        block.type = "image_url";
        block.image_url = "data:" + media_type + ";base64," + base64_data;
        block.image_detail = detail;
        return block;
    }
};

// --- Message ---

struct ToolCallForMessage {
    std::string id;
    std::string type = "function";
    std::string function_name;
    std::string function_arguments;  // JSON string
    
    nlohmann::json to_json() const {
        nlohmann::json j;
        j["id"] = id;
        j["type"] = type;
        j["function"]["name"] = function_name;
        j["function"]["arguments"] = function_arguments;
        return j;
    }
};

struct Message {
    std::string role;  // "user" | "assistant" | "system" | "tool"
    std::vector<ContentBlock> content;
    std::vector<ToolCallForMessage> tool_calls;  // For assistant messages with tool calls
    std::string tool_call_id;  // For tool response messages

    Message() = default;
    Message(std::string r, std::string text) : role(std::move(r)) {
        if (!text.empty()) {
            content.push_back(ContentBlock::make_text(std::move(text)));
        }
    }
    
    std::string text() const {
        std::string result;
        for (const auto& block : content) {
            if (block.type == "text" || block.type == "thinking") {
                result += block.text;
            }
        }
        return result;
    }
    
    nlohmann::json to_json() const {
        nlohmann::json j;
        j["role"] = role;
        
        // Handle tool response messages
        if (role == "tool" && !tool_call_id.empty()) {
            if (!content.empty()) {
                if (content[0].type == "tool_result") {
                    j["content"] = content[0].content;
                } else if (content[0].type == "text") {
                    j["content"] = content[0].text;
                } else {
                    j["content"] = "";
                }
            } else {
                j["content"] = "";
            }
            j["tool_call_id"] = tool_call_id;
            return j;
        }
        
        // Handle assistant messages with tool_calls (OpenAI format)
        if (role == "assistant" && !tool_calls.empty()) {
            // Content should be string or null
            if (!content.empty() && content[0].type == "text" && !content[0].text.empty()) {
                j["content"] = content[0].text;
            } else {
                j["content"] = nullptr;
            }
            // Add tool_calls as separate field
            nlohmann::json tc_array = nlohmann::json::array();
            for (const auto& tc : tool_calls) {
                tc_array.push_back(tc.to_json());
            }
            j["tool_calls"] = tc_array;
            return j;
        }
        
        // Regular messages: simple string format for compatibility
        if (content.size() == 1 && content[0].type == "text") {
            j["content"] = content[0].text;
        } else if (content.empty()) {
            j["content"] = "";
        } else {
            j["content"] = nlohmann::json::array();
            for (const auto& block : content) {
                j["content"].push_back(block.to_json());
            }
        }
        return j;
    }
    
    static Message from_json(const nlohmann::json& j) {
        Message msg;
        msg.role = j.value("role", "");
        
        if (j.contains("content")) {
            const auto& content = j["content"];
            if (content.is_array()) {
                for (const auto& block_json : content) {
                    msg.content.push_back(ContentBlock::from_json(block_json));
                }
            } else if (content.is_string()) {
                // Handle simple string content format
                msg.content.push_back(ContentBlock::make_text(content.get<std::string>()));
            }
        }
        
        return msg;
    }
};

// --- Tool Call ---

struct ToolCall {
    std::string id;
    std::string name;
    nlohmann::json arguments;
    int index = -1;  // For streaming: index to match chunks
};

// --- Agent Event (for streaming) ---

struct AgentEvent {
    std::string type;  // "text_delta" | "reasoning_delta" | "tool_use" | "tool_result" | "message_end"
    nlohmann::json data;
};

using AgentEventCallback = std::function<void(const AgentEvent&)>;

// --- Skill Metadata ---

struct SkillMetadata {
    std::string name;
    std::string description;
    std::vector<std::string> required_bins;
    std::vector<std::string> required_envs;
    std::vector<std::string> any_bins;
    std::vector<std::string> config_files;
    std::vector<std::string> os_restrict;
    bool always = false;
    std::string primary_env;
    std::string emoji;
    std::string content;
    nlohmann::json execution_hints = nullptr;
    nlohmann::json routing_hints = nullptr;
};

// --- Tool Schema ---

struct ToolSchema {
    std::string name;
    std::string description;
    nlohmann::json parameters;
};

} // namespace icraw
