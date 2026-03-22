#include <jni.h>
#include <string>
#include <memory>
#include <curl/curl.h>
#include "icraw/core/logger.hpp"
#include "icraw/mobile_agent.hpp"
#include "icraw/config.hpp"
#include "icraw/android_tools.hpp"
#include "icraw/tools/tool_registry.hpp"
#include <nlohmann/json.hpp>

// ICRAW_ANDROID is already defined by CMake, no need to redefine

// Global MobileAgent instance
static std::unique_ptr<icraw::MobileAgent> g_agent;

// Global callback object reference for Android tool invocations
static jobject g_callback_object = nullptr;

extern "C" {

/**
 * JNI OnLoad - Called when the native library is loaded
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Initialize curl globally (required before using curl_easy_init)
    CURLcode curl_res = curl_global_init(CURL_GLOBAL_DEFAULT);
    if (curl_res != CURLE_OK) {
        // Cannot use logger yet, but continue anyway
    }

    // Initialize the logger (Android uses logcat, directory is ignored)
    icraw::Logger::get_instance().init("", "debug");

    return JNI_VERSION_1_6;
}

/**
 * Get the native agent version
 */
JNIEXPORT jstring JNICALL Java_com_hh_agent_core_NativeAgent_nativeGetVersion(
        JNIEnv* env,
        jclass /* clazz */) {
    std::string version = "1.0.0-native";
    return env->NewStringUTF(version.c_str());
}

/**
 * Initialize the native agent
 * Returns: 0 on success, -1 on failure
 */
JNIEXPORT jint JNICALL Java_com_hh_agent_core_NativeAgent_nativeInitialize(
        JNIEnv* env,
        jclass /* clazz */,
        jstring configJsonStr) {
    // Get config JSON from Java
    const char* config_json = nullptr;
    if (configJsonStr) {
        config_json = env->GetStringUTFChars(configJsonStr, nullptr);
    }

    icraw::Logger::get_instance().info("nativeInitialize: Starting initialization");

    // Create MobileAgent with config
    try {
        icraw::IcrawConfig config;

        // Parse JSON config if provided
        if (config_json && strlen(config_json) > 0) {
            try {
                auto json = nlohmann::json::parse(config_json);
                if (json.contains("provider")) {
                    auto& provider = json["provider"];
                    if (provider.contains("apiKey")) {
                        config.provider.api_key = provider["apiKey"].get<std::string>();
                    }
                    if (provider.contains("baseUrl")) {
                        config.provider.base_url = provider["baseUrl"].get<std::string>();
                    }
                }
                if (json.contains("agent") && json["agent"].contains("model")) {
                    config.agent.model = json["agent"]["model"].get<std::string>();
                }
                // Parse workspace path from JSON
                if (json.contains("workspacePath")) {
                    config.workspace_path = json["workspacePath"].get<std::string>();
                }
                // Ensure workspace path is set (load_default sets the default path)
        icraw::IcrawConfig default_config = icraw::IcrawConfig::load_default();
        if (config.workspace_path.empty()) {
            config.workspace_path = default_config.workspace_path;
        }

        icraw::Logger::get_instance().info("Loaded config from JSON: apiKey set={}, baseUrl={}, model={}, workspace={}",
                    !config.provider.api_key.empty(), config.provider.base_url, config.agent.model, config.workspace_path.string());
            } catch (const std::exception& e) {
                icraw::Logger::get_instance().warn("Failed to parse config JSON: {}", e.what());
                config = icraw::IcrawConfig::load_default();
            }
        } else {
            icraw::Logger::get_instance().info("No config JSON provided, using defaults");
            config = icraw::IcrawConfig::load_default();
        }

        icraw::Logger::get_instance().info("Creating MobileAgent with config: model={}, workspace={}",
            config.agent.model, config.workspace_path.string());

        g_agent = icraw::MobileAgent::create_with_config(config);

        icraw::Logger::get_instance().info(
            "NativeAgent initialized successfully");
    } catch (const std::exception& e) {
        icraw::Logger::get_instance().error(
            "Failed to initialize NativeAgent: {}", e.what());
        // Return error to Java instead of silently failing
        if (config_json) {
            env->ReleaseStringUTFChars(configJsonStr, config_json);
        }
        return -1;  // Return error code
    }

    if (config_json) {
        env->ReleaseStringUTFChars(configJsonStr, config_json);
    }
    return 0;  // Success
}

/**
 * Send a message to the agent and get a response
 */
JNIEXPORT jstring JNICALL Java_com_hh_agent_core_NativeAgent_nativeSendMessage(
        JNIEnv* env,
        jclass /* clazz */,
        jstring message) {
    const char* msg = env->GetStringUTFChars(message, nullptr);
    std::string response;

    if (!msg) {
        return env->NewStringUTF("");
    }

    icraw::Logger::get_instance().debug("Received message: {}", msg);

    // Check if agent is initialized
    if (!g_agent) {
        icraw::Logger::get_instance().warn("Agent not initialized, returning error");
        response = "Error: Agent not initialized. Call nativeInitialize first.";
    } else {
        try {
            // Call MobileAgent::chat()
            response = g_agent->chat(msg);
            icraw::Logger::get_instance().debug("Agent response: {}", response);
        } catch (const std::exception& e) {
            icraw::Logger::get_instance().error("Agent chat failed: {}", e.what());
            response = std::string("Error: ") + e.what();
        }
    }

    env->ReleaseStringUTFChars(message, msg);
    return env->NewStringUTF(response.c_str());
}

/**
 * Shutdown the native agent
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeShutdown(
        JNIEnv* env,
        jclass /* clazz */) {
    icraw::Logger::get_instance().info("Shutting down NativeAgent");

    // Clean up MobileAgent instance
    if (g_agent) {
        g_agent->stop();
        g_agent.reset();
        icraw::Logger::get_instance().info("MobileAgent destroyed");
    }
}

/**
 * Register Android tool callback from Java
 * This creates a JNI callback that delegates to the Java AndroidToolCallback interface
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeRegisterAndroidToolCallback(
        JNIEnv* env,
        jclass /* clazz */,
        jobject callback) {

    // Delete previous global reference if exists
    if (g_callback_object) {
        env->DeleteGlobalRef(g_callback_object);
        g_callback_object = nullptr;
    }

    if (!callback) {
        icraw::Logger::get_instance().info("AndroidToolCallback unregistered");
        return;
    }

    // Create global reference to keep the callback object alive
    g_callback_object = env->NewGlobalRef(callback);

    // Get JavaVM pointer for multi-threaded access
    JavaVM* java_vm = nullptr;
    env->GetJavaVM(&java_vm);

    // Get method ID (can be cached, it's static)
    jclass cls = env->GetObjectClass(callback);
    jmethodID method_id = env->GetMethodID(cls, "callTool",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    env->DeleteLocalRef(cls);

    // Create a C++ callback that delegates to Java
    class JniCallback : public icraw::AndroidToolCallback {
    public:
        JniCallback(JavaVM* jvm, jobject o, jmethodID mid)
            : java_vm_(jvm), callback_(o), method_id_(mid) {}

        std::string call_tool(const std::string& tool_name, const nlohmann::json& args) override {
            if (!callback_ || !method_id_) {
                nlohmann::json error;
                error["success"] = false;
                error["error"] = "callback_not_available";
                return error.dump();
            }

            // Attach current thread to JVM if needed
            JNIEnv* env = nullptr;
            bool attached = false;
            int getEnvResult = java_vm_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (getEnvResult == JNI_EDETACHED) {
                if (java_vm_->AttachCurrentThread(&env, nullptr) == 0) {
                    attached = true;
                } else {
                    nlohmann::json error;
                    error["success"] = false;
                    error["error"] = "failed_to_attach_thread";
                    return error.dump();
                }
            } else if (getEnvResult != JNI_OK) {
                nlohmann::json error;
                error["success"] = false;
                error["error"] = "failed_to_get_env";
                return error.dump();
            }

            jstring j_tool_name = env->NewStringUTF(tool_name.c_str());
            jstring j_args = env->NewStringUTF(args.dump().c_str());

            jstring j_result = (jstring)env->CallObjectMethod(callback_, method_id_, j_tool_name, j_args);

            std::string result;
            if (j_result) {
                const char* result_str = env->GetStringUTFChars(j_result, nullptr);
                result = result_str;
                env->ReleaseStringUTFChars(j_result, result_str);
            } else {
                nlohmann::json error;
                error["success"] = false;
                error["error"] = "callback_failed";
                result = error.dump();
            }

            env->DeleteLocalRef(j_tool_name);
            env->DeleteLocalRef(j_args);
            if (j_result) env->DeleteLocalRef(j_result);

            // Detach thread if we attached it
            if (attached) {
                java_vm_->DetachCurrentThread();
            }

            return result;
        }

    private:
        JavaVM* java_vm_;
        jobject callback_;
        jmethodID method_id_;
    };

    // Register the JNI callback
    icraw::g_android_tools.register_callback(std::make_unique<JniCallback>(java_vm, g_callback_object, method_id));

    icraw::Logger::get_instance().info("AndroidToolCallback registered via JNI");
}

/**
 * Set tools schema from Java (JSON format)
 * This allows Java to pass tools.json content to C++ for tool registration
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeSetToolsSchema(
        JNIEnv* env,
        jclass /* clazz */,
        jstring schemaJson) {

    if (!g_agent) {
        icraw::Logger::get_instance().warn("nativeSetToolsSchema: Agent not initialized");
        return;
    }

    const char* schema_json = nullptr;
    if (schemaJson) {
        schema_json = env->GetStringUTFChars(schemaJson, nullptr);
    }

    if (!schema_json || strlen(schema_json) == 0) {
        icraw::Logger::get_instance().warn("nativeSetToolsSchema: Empty schema JSON");
        if (schema_json) {
            env->ReleaseStringUTFChars(schemaJson, schema_json);
        }
        return;
    }

    try {
        auto schema = nlohmann::json::parse(schema_json);
        auto registry = g_agent->get_tool_registry();

        // Call the new method to register tools from external schema
        registry->register_tools_from_schema(schema);

        icraw::Logger::get_instance().info("nativeSetToolsSchema: Successfully registered tools from schema");
    } catch (const std::exception& e) {
        icraw::Logger::get_instance().error("nativeSetToolsSchema: Failed to parse schema: {}", e.what());
    }

    env->ReleaseStringUTFChars(schemaJson, schema_json);
}

/**
 * Send a message with streaming callback to Java layer
 * This implements the stream event channel from C++ to Java
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeSendMessageStream(
        JNIEnv* env,
        jclass /* clazz */,
        jstring message,
        jobject listener) {

    const char* msg = env->GetStringUTFChars(message, nullptr);
    if (!msg) {
        icraw::Logger::get_instance().warn("nativeSendMessageStream: Empty message");
        return;
    }

    if (!g_agent) {
        icraw::Logger::get_instance().warn("nativeSendMessageStream: Agent not initialized");
        env->ReleaseStringUTFChars(message, msg);
        return;
    }

    if (!listener) {
        icraw::Logger::get_instance().warn("nativeSendMessageStream: Listener is null");
        env->ReleaseStringUTFChars(message, msg);
        return;
    }

    // Create global reference to keep the listener object alive
    jobject listener_global_ref = env->NewGlobalRef(listener);

    // Get JavaVM pointer for multi-threaded access
    JavaVM* java_vm = nullptr;
    env->GetJavaVM(&java_vm);

    // Get method IDs for all callback methods
    jclass listener_cls = env->GetObjectClass(listener);

    jmethodID method_onTextDelta = env->GetMethodID(listener_cls, "onTextDelta", "(Ljava/lang/String;)V");
    jmethodID method_onToolUse = env->GetMethodID(listener_cls, "onToolUse",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID method_onToolResult = env->GetMethodID(listener_cls, "onToolResult",
        "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID method_onMessageEnd = env->GetMethodID(listener_cls, "onMessageEnd", "(Ljava/lang/String;)V");
    jmethodID method_onError = env->GetMethodID(listener_cls, "onError",
        "(Ljava/lang/String;Ljava/lang/String;)V");

    env->DeleteLocalRef(listener_cls);

    // Create JNI callback that delegates to Java
    class JniStreamCallback : public icraw::AgentEventCallback {
    public:
        JniStreamCallback(JavaVM* jvm, jobject listener,
                          jmethodID onTextDelta, jmethodID onToolUse,
                          jmethodID onToolResult, jmethodID onMessageEnd,
                          jmethodID onError)
            : java_vm_(jvm), listener_(listener),
              method_onTextDelta_(onTextDelta), method_onToolUse_(onToolUse),
              method_onToolResult_(onToolResult), method_onMessageEnd_(onMessageEnd),
              method_onError_(onError) {}

        void operator()(const icraw::AgentEvent& event) {
            // Attach current thread to JVM if needed
            JNIEnv* env = nullptr;
            bool attached = false;
            int getEnvResult = java_vm_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (getEnvResult == JNI_EDETACHED) {
                if (java_vm_->AttachCurrentThread(&env, nullptr) == 0) {
                    attached = true;
                } else {
                    icraw::Logger::get_instance().error("nativeSendMessageStream: Failed to attach thread");
                    return;
                }
            } else if (getEnvResult != JNI_OK) {
                icraw::Logger::get_instance().error("nativeSendMessageStream: Failed to get JNI env");
                return;
            }

            try {
                icraw::Logger::get_instance().info("nativeSendMessageStream: event type='{}'", event.type);
                if (event.type == "text_delta") {
                    std::string text = event.data.value("delta", "");
                    icraw::Logger::get_instance().info("nativeSendMessageStream: text_delta event, text='{}'", text);
                    jstring j_text = env->NewStringUTF(text.c_str());
                    env->CallVoidMethod(listener_, method_onTextDelta_, j_text);
                    env->DeleteLocalRef(j_text);

                } else if (event.type == "tool_use") {
                    std::string id = event.data.value("id", "");
                    std::string name = event.data.value("name", "");
                    std::string arguments = event.data.value("input", nlohmann::json::object()).dump();
                    icraw::Logger::get_instance().info("nativeSendMessageStream: tool_use event, name='{}'", name);

                    jstring j_id = env->NewStringUTF(id.c_str());
                    jstring j_name = env->NewStringUTF(name.c_str());
                    jstring j_args = env->NewStringUTF(arguments.c_str());

                    env->CallVoidMethod(listener_, method_onToolUse_, j_id, j_name, j_args);

                    env->DeleteLocalRef(j_id);
                    env->DeleteLocalRef(j_name);
                    env->DeleteLocalRef(j_args);

                } else if (event.type == "tool_result") {
                    std::string tool_use_id = event.data.value("tool_use_id", "");
                    std::string content = event.data.value("content", "");

                    jstring j_id = env->NewStringUTF(tool_use_id.c_str());
                    jstring j_result = env->NewStringUTF(content.c_str());

                    env->CallVoidMethod(listener_, method_onToolResult_, j_id, j_result);

                    env->DeleteLocalRef(j_id);
                    env->DeleteLocalRef(j_result);

                } else if (event.type == "message_end") {
                    std::string finish_reason = event.data.value("finish_reason", "unknown");
                    icraw::Logger::get_instance().info("nativeSendMessageStream: message_end event, finish_reason='{}'", finish_reason);
                    jstring j_reason = env->NewStringUTF(finish_reason.c_str());
                    env->CallVoidMethod(listener_, method_onMessageEnd_, j_reason);
                    env->DeleteLocalRef(j_reason);
                }
            } catch (...) {
                icraw::Logger::get_instance().error("nativeSendMessageStream: Exception in callback");
            }

            // Detach thread if we attached it
            if (attached) {
                java_vm_->DetachCurrentThread();
            }
        }

        // Method to report error to Java
        void reportError(const std::string& errorCode, const std::string& errorMessage) {
            JNIEnv* env = nullptr;
            bool attached = false;
            int getEnvResult = java_vm_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (getEnvResult == JNI_EDETACHED) {
                if (java_vm_->AttachCurrentThread(&env, nullptr) == 0) {
                    attached = true;
                } else {
                    return;
                }
            } else if (getEnvResult != JNI_OK) {
                return;
            }

            jstring j_code = env->NewStringUTF(errorCode.c_str());
            jstring j_message = env->NewStringUTF(errorMessage.c_str());

            env->CallVoidMethod(listener_, method_onError_, j_code, j_message);

            env->DeleteLocalRef(j_code);
            env->DeleteLocalRef(j_message);

            if (attached) {
                java_vm_->DetachCurrentThread();
            }
        }

    private:
        JavaVM* java_vm_;
        jobject listener_;
        jmethodID method_onTextDelta_;
        jmethodID method_onToolUse_;
        jmethodID method_onToolResult_;
        jmethodID method_onMessageEnd_;
        jmethodID method_onError_;
    };

    // Create callback instance
    auto callback = std::make_unique<JniStreamCallback>(
        java_vm, listener_global_ref,
        method_onTextDelta, method_onToolUse,
        method_onToolResult, method_onMessageEnd,
        method_onError);

    JniStreamCallback* callback_ptr = callback.get();

    try {
        // Call chat_stream with the callback
        g_agent->chat_stream(msg, *callback);
        icraw::Logger::get_instance().debug("nativeSendMessageStream: chat_stream completed");
    } catch (const std::exception& e) {
        // Report error to Java via callback
        callback_ptr->reportError("cpp_exception", e.what());
        icraw::Logger::get_instance().error("nativeSendMessageStream: Exception: {}", e.what());
    } catch (...) {
        callback_ptr->reportError("unknown_error", "Unknown exception in chat_stream");
        icraw::Logger::get_instance().error("nativeSendMessageStream: Unknown exception");
    }

    // Clean up global reference
    env->DeleteGlobalRef(listener_global_ref);

    env->ReleaseStringUTFChars(message, msg);
}

/**
 * Cancel the current streaming request
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeCancelStream(
        JNIEnv* env,
        jclass /* clazz */) {

    icraw::Logger::get_instance().info("nativeCancelStream: Cancelling streaming request");

    if (!g_agent) {
        icraw::Logger::get_instance().warn("nativeCancelStream: Agent not initialized");
        return;
    }

    // Call stop on the agent to cancel the streaming request
    g_agent->stop();
    icraw::Logger::get_instance().info("nativeCancelStream: Streaming request cancelled");
}

/**
 * Get recent messages from SQLite database filtered by roles
 * Returns JSON array of messages: [{"role": "user", "content": "...", "timestamp": "..."}, ...]
 */
JNIEXPORT jstring JNICALL Java_com_hh_agent_core_NativeAgent_nativeGetHistory(
        JNIEnv* env,
        jclass /* clazz */,
        jstring sessionId,
        jint limit) {

    const char* session_id = env->GetStringUTFChars(sessionId, nullptr);
    if (!session_id) {
        return env->NewStringUTF("[]");
    }

    icraw::Logger::get_instance().debug("nativeGetHistory: session_id={}, limit={}", session_id, limit);

    std::vector<std::string> roles = {"user", "assistant"};

    std::vector<icraw::MemoryEntry> entries;

    if (g_agent) {
        try {
            auto memory_manager = g_agent->get_memory_manager();
            if (memory_manager) {
                entries = memory_manager->get_recent_messages_by_roles(static_cast<int>(limit), roles, session_id);
            }
        } catch (const std::exception& e) {
            icraw::Logger::get_instance().error("nativeGetHistory: {}", e.what());
        }
    }

    // Convert to JSON array
    nlohmann::json messages = nlohmann::json::array();
    for (const auto& entry : entries) {
        nlohmann::json msg;
        msg["role"] = entry.role;
        msg["content"] = entry.content;
        msg["timestamp"] = entry.timestamp;
        messages.push_back(msg);
    }

    std::string result = messages.dump();

    env->ReleaseStringUTFChars(sessionId, session_id);

    icraw::Logger::get_instance().debug("nativeGetHistory: returning {} messages", entries.size());

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
