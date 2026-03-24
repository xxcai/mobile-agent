#include <jni.h>
#include <curl/curl.h>
#include <memory>
#include <string>
#include "icraw/log/logger.hpp"
#include "icraw/mobile_agent.hpp"
#include "icraw/config.hpp"
#include "icraw/platform/android/android_tools.hpp"
#include "icraw/tools/tool_registry.hpp"
#include "../../log/logger_backend.hpp"
#include <nlohmann/json.hpp>

// ICRAW_ANDROID is already defined by CMake, no need to redefine

// Global MobileAgent instance
static std::unique_ptr<icraw::MobileAgent> g_agent;

// Global callback object reference for Android tool invocations
static jobject g_callback_object = nullptr;
static JavaVM* g_java_vm = nullptr;

namespace {

constexpr const char* kNativeJavaLoggerTag = "icraw";
icraw::LogLevel g_native_log_level = icraw::parse_log_level("debug");

class JavaLoggerBackend final : public icraw::LoggerBackend {
public:
    JavaLoggerBackend(JavaVM* java_vm, jobject logger, jmethodID debug_method,
            jmethodID info_method, jmethodID warn_method, jmethodID error_method)
        : java_vm_(java_vm)
        , logger_(logger)
        , debug_method_(debug_method)
        , info_method_(info_method)
        , warn_method_(warn_method)
        , error_method_(error_method) {}

    ~JavaLoggerBackend() override {
        if (!java_vm_ || !logger_) {
            return;
        }

        JNIEnv* env = nullptr;
        bool attached = false;
        if (!get_env(&env, &attached)) {
            return;
        }

        env->DeleteGlobalRef(logger_);
        if (attached) {
            java_vm_->DetachCurrentThread();
        }
    }

    void set_level(icraw::LogLevel level) override {
        min_level_ = level;
    }

    void log(icraw::LogLevel level, std::string_view message) override {
        if (level < min_level_ || !java_vm_ || !logger_) {
            return;
        }

        jmethodID method = select_method(level);
        if (!method) {
            return;
        }

        JNIEnv* env = nullptr;
        bool attached = false;
        if (!get_env(&env, &attached)) {
            return;
        }

        jstring tag = env->NewStringUTF(kNativeJavaLoggerTag);
        jstring text = env->NewStringUTF(std::string(message).c_str());
        env->CallVoidMethod(logger_, method, tag, text);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteLocalRef(text);
        env->DeleteLocalRef(tag);

        if (attached) {
            java_vm_->DetachCurrentThread();
        }
    }

private:
    bool get_env(JNIEnv** env, bool* attached) const {
        *attached = false;
        const jint get_env_result = java_vm_->GetEnv(reinterpret_cast<void**>(env), JNI_VERSION_1_6);
        if (get_env_result == JNI_OK) {
            return true;
        }
        if (get_env_result != JNI_EDETACHED) {
            return false;
        }

        if (java_vm_->AttachCurrentThread(env, nullptr) != 0) {
            return false;
        }
        *attached = true;
        return true;
    }

    jmethodID select_method(icraw::LogLevel level) const {
        switch (level) {
            case icraw::LogLevel::Trace:
            case icraw::LogLevel::Debug:
                return debug_method_;
            case icraw::LogLevel::Info:
                return info_method_;
            case icraw::LogLevel::Warn:
                return warn_method_;
            case icraw::LogLevel::Error:
                return error_method_;
        }
        return debug_method_;
    }

    JavaVM* java_vm_;
    jobject logger_;
    jmethodID debug_method_;
    jmethodID info_method_;
    jmethodID warn_method_;
    jmethodID error_method_;
    icraw::LogLevel min_level_ = icraw::LogLevel::Info;
};

void reset_native_logger_backend() {
    icraw::Logger::get_instance().set_backend(
            icraw::create_default_logger_backend(""),
            g_native_log_level);
}

} // namespace

extern "C" {

/**
 * JNI OnLoad - Called when the native library is loaded
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_java_vm = vm;
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Initialize curl globally (required before using curl_easy_init)
    CURLcode curl_res = curl_global_init(CURL_GLOBAL_DEFAULT);
    if (curl_res != CURLE_OK) {
        ICRAW_LOG_WARN("[NativeAgentJni][curl_initialize_failed] curl_code={}", static_cast<int>(curl_res));
    }

    // Initialize the logger (Android uses logcat, directory is ignored)
    reset_native_logger_backend();
    ICRAW_LOG_INFO("[NativeAgentJni][jni_load_complete]");

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

    ICRAW_LOG_INFO("[NativeAgentJni][native_initialize_start] config_length={}",
            config_json ? std::strlen(config_json) : 0);

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

                icraw::Logger::get_instance().set_level(g_native_log_level);

                ICRAW_LOG_INFO("[NativeAgentJni][config_loaded] api_key_set={} base_url={} model={} workspace={} log_level_source=java",
                        !config.provider.api_key.empty(),
                        config.provider.base_url,
                        config.agent.model,
                        config.workspace_path.string());
            } catch (const std::exception& e) {
                ICRAW_LOG_WARN("[NativeAgentJni][config_parse_failed] message={}", e.what());
                config = icraw::IcrawConfig::load_default();
                icraw::Logger::get_instance().set_level(g_native_log_level);
            }
        } else {
            ICRAW_LOG_INFO("[NativeAgentJni][config_default_used]");
            config = icraw::IcrawConfig::load_default();
            icraw::Logger::get_instance().set_level(g_native_log_level);
        }

        ICRAW_LOG_INFO("[NativeAgentJni][mobile_agent_create_start] model={} workspace={}",
            config.agent.model, config.workspace_path.string());

        g_agent = icraw::MobileAgent::create_with_config(config);

        ICRAW_LOG_INFO("[NativeAgentJni][native_initialize_complete]");
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[NativeAgentJni][native_initialize_failed] message={}", e.what());
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

JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeSetLogger(
        JNIEnv* env,
        jclass /* clazz */,
        jobject logger) {
    if (!logger || !g_java_vm) {
        reset_native_logger_backend();
        ICRAW_LOG_INFO("[NativeAgentJni][logger_bridge_reset]");
        return;
    }

    jclass logger_class = env->GetObjectClass(logger);
    if (!logger_class) {
        reset_native_logger_backend();
        ICRAW_LOG_WARN("[NativeAgentJni][logger_bridge_failed] reason=logger_class_missing");
        return;
    }

    jmethodID debug_method = env->GetMethodID(logger_class, "d",
            "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID info_method = env->GetMethodID(logger_class, "i",
            "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID warn_method = env->GetMethodID(logger_class, "w",
            "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID error_method = env->GetMethodID(logger_class, "e",
            "(Ljava/lang/String;Ljava/lang/String;)V");
    env->DeleteLocalRef(logger_class);

    if (!debug_method || !info_method || !warn_method || !error_method) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        reset_native_logger_backend();
        ICRAW_LOG_WARN("[NativeAgentJni][logger_bridge_failed] reason=logger_method_missing");
        return;
    }

    jobject global_logger = env->NewGlobalRef(logger);
    if (!global_logger) {
        reset_native_logger_backend();
        ICRAW_LOG_WARN("[NativeAgentJni][logger_bridge_failed] reason=global_ref_create_failed");
        return;
    }

    auto backend = std::make_unique<JavaLoggerBackend>(
            g_java_vm,
            global_logger,
            debug_method,
            info_method,
            warn_method,
            error_method);
    icraw::Logger::get_instance().set_backend(std::move(backend), g_native_log_level);
    ICRAW_LOG_INFO("[NativeAgentJni][logger_bridge_enabled]");
}

JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeSetLogLevel(
        JNIEnv* env,
        jclass /* clazz */,
        jstring levelStr) {
    (void) env;
    std::string level = "debug";
    if (levelStr != nullptr) {
        const char* raw_level = env->GetStringUTFChars(levelStr, nullptr);
        if (raw_level != nullptr && std::strlen(raw_level) > 0) {
            level = raw_level;
        }
        if (raw_level != nullptr) {
            env->ReleaseStringUTFChars(levelStr, raw_level);
        }
    }

    g_native_log_level = icraw::parse_log_level(level);
    icraw::Logger::get_instance().set_level(g_native_log_level);
    ICRAW_LOG_INFO("[NativeAgentJni][log_level_updated] level={}", level);
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

    ICRAW_LOG_INFO("[NativeAgentJni][send_message_start] input_length={}", std::strlen(msg));
    ICRAW_LOG_DEBUG("[NativeAgentJni][send_message_debug] input={}", msg);

    // Check if agent is initialized
    if (!g_agent) {
        ICRAW_LOG_WARN("[NativeAgentJni][send_message_skipped] reason=agent_not_initialized");
        response = "Error: Agent not initialized. Call nativeInitialize first.";
    } else {
        try {
            // Call MobileAgent::chat()
            response = g_agent->chat(msg);
            ICRAW_LOG_INFO("[NativeAgentJni][send_message_complete] output_length={}", response.size());
            ICRAW_LOG_DEBUG("[NativeAgentJni][send_message_debug] response={}", response);
        } catch (const std::exception& e) {
            ICRAW_LOG_ERROR("[NativeAgentJni][send_message_failed] message={}", e.what());
            response = std::string("Error: ") + e.what();
        }
    }

    env->ReleaseStringUTFChars(message, msg);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL Java_com_hh_agent_core_NativeAgent_nativeRunStateless(
        JNIEnv* env,
        jclass /* clazz */,
        jstring systemPrompt,
        jstring message) {
    const char* system_prompt = env->GetStringUTFChars(systemPrompt, nullptr);
    const char* msg = env->GetStringUTFChars(message, nullptr);
    std::string response;

    if (!system_prompt || !msg) {
        if (system_prompt) {
            env->ReleaseStringUTFChars(systemPrompt, system_prompt);
        }
        if (msg) {
            env->ReleaseStringUTFChars(message, msg);
        }
        return env->NewStringUTF("");
    }

    ICRAW_LOG_INFO("[NativeAgentJni][stateless_start] system_prompt_length={} input_length={}",
            std::strlen(system_prompt), std::strlen(msg));

    if (!g_agent) {
        ICRAW_LOG_WARN("[NativeAgentJni][stateless_skipped] reason=agent_not_initialized");
        response.clear();
    } else {
        try {
            response = g_agent->chat_stateless(system_prompt, msg);
            ICRAW_LOG_INFO("[NativeAgentJni][stateless_complete] output_length={}", response.size());
        } catch (const std::exception& e) {
            ICRAW_LOG_ERROR("[NativeAgentJni][stateless_failed] message={}", e.what());
            response.clear();
        }
    }

    env->ReleaseStringUTFChars(systemPrompt, system_prompt);
    env->ReleaseStringUTFChars(message, msg);
    return env->NewStringUTF(response.c_str());
}

/**
 * Shutdown the native agent
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeShutdown(
        JNIEnv* env,
        jclass /* clazz */) {
    ICRAW_LOG_INFO("[NativeAgentJni][shutdown_start]");

    // Clean up MobileAgent instance
    if (g_agent) {
        g_agent->stop();
        g_agent.reset();
        ICRAW_LOG_INFO("[NativeAgentJni][shutdown_complete]");
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
        ICRAW_LOG_INFO("[NativeAgentJni][tool_callback_unregistered]");
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

    ICRAW_LOG_INFO("[NativeAgentJni][tool_callback_registered]");
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
        ICRAW_LOG_WARN("[NativeAgentJni][tools_schema_register_skipped] reason=agent_not_initialized");
        return;
    }

    const char* schema_json = nullptr;
    if (schemaJson) {
        schema_json = env->GetStringUTFChars(schemaJson, nullptr);
    }

    if (!schema_json || strlen(schema_json) == 0) {
        ICRAW_LOG_WARN("[NativeAgentJni][tools_schema_register_skipped] reason=empty_schema_json");
        if (schema_json) {
            env->ReleaseStringUTFChars(schemaJson, schema_json);
        }
        return;
    }

    try {
        ICRAW_LOG_INFO("[NativeAgentJni][tools_schema_register_start] schema_length={}", std::strlen(schema_json));
        auto schema = nlohmann::json::parse(schema_json);
        auto registry = g_agent->get_tool_registry();

        // Call the new method to register tools from external schema
        registry->register_tools_from_schema(schema);

        ICRAW_LOG_INFO("[NativeAgentJni][tools_schema_register_complete]");
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("[NativeAgentJni][tools_schema_register_failed] message={}", e.what());
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
        jstring sessionId,
        jstring message,
        jobject listener) {

    const char* session_id = env->GetStringUTFChars(sessionId, nullptr);
    const char* msg = env->GetStringUTFChars(message, nullptr);
    if (!session_id || !msg) {
        ICRAW_LOG_WARN("[NativeAgentJni][stream_start_skipped] reason=empty_message");
        if (session_id) {
            env->ReleaseStringUTFChars(sessionId, session_id);
        }
        return;
    }

    if (!g_agent) {
        ICRAW_LOG_WARN("[NativeAgentJni][stream_start_skipped] reason=agent_not_initialized");
        env->ReleaseStringUTFChars(sessionId, session_id);
        env->ReleaseStringUTFChars(message, msg);
        return;
    }

    if (!listener) {
        ICRAW_LOG_WARN("[NativeAgentJni][stream_start_skipped] reason=listener_null");
        env->ReleaseStringUTFChars(sessionId, session_id);
        env->ReleaseStringUTFChars(message, msg);
        return;
    }
    ICRAW_LOG_INFO("[NativeAgentJni][stream_start] session_id={} input_length={}",
            session_id, std::strlen(msg));
    ICRAW_LOG_DEBUG("[NativeAgentJni][stream_start_debug] session_id={} input={}",
            session_id, msg);

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
                    ICRAW_LOG_ERROR("[NativeAgentJni][stream_failed] reason=attach_thread_failed");
                    return;
                }
            } else if (getEnvResult != JNI_OK) {
                ICRAW_LOG_ERROR("[NativeAgentJni][stream_failed] reason=get_jni_env_failed");
                return;
            }

            try {
                if (event.type == "text_delta") {
                    std::string text = event.data.value("delta", "");
                    ICRAW_LOG_DEBUG("[NativeAgentJni][stream_event_debug] event_type=text_delta delta_length={} text={}",
                            text.size(), text);
                    jstring j_text = env->NewStringUTF(text.c_str());
                    env->CallVoidMethod(listener_, method_onTextDelta_, j_text);
                    env->DeleteLocalRef(j_text);

                } else if (event.type == "tool_use") {
                    std::string id = event.data.value("id", "");
                    std::string name = event.data.value("name", "");
                    std::string arguments = event.data.value("input", nlohmann::json::object()).dump();
                    ICRAW_LOG_INFO("[NativeAgentJni][stream_event] event_type=tool_use tool_name={} tool_id={}",
                            name, id);
                    ICRAW_LOG_DEBUG("[NativeAgentJni][stream_event_debug] event_type=tool_use tool_name={} tool_id={} args={}",
                            name, id, arguments);

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
                    ICRAW_LOG_INFO("[NativeAgentJni][stream_event] event_type=tool_result tool_id={} content_length={}",
                            tool_use_id, content.size());
                    ICRAW_LOG_DEBUG("[NativeAgentJni][stream_event_debug] event_type=tool_result tool_id={} content={}",
                            tool_use_id, content);

                    jstring j_id = env->NewStringUTF(tool_use_id.c_str());
                    jstring j_result = env->NewStringUTF(content.c_str());

                    env->CallVoidMethod(listener_, method_onToolResult_, j_id, j_result);

                    env->DeleteLocalRef(j_id);
                    env->DeleteLocalRef(j_result);

                } else if (event.type == "message_end") {
                    std::string finish_reason = event.data.value("finish_reason", "unknown");
                    ICRAW_LOG_INFO("[NativeAgentJni][stream_complete] finish_reason={}", finish_reason);
                    jstring j_reason = env->NewStringUTF(finish_reason.c_str());
                    env->CallVoidMethod(listener_, method_onMessageEnd_, j_reason);
                    env->DeleteLocalRef(j_reason);
                }
            } catch (...) {
                ICRAW_LOG_ERROR("[NativeAgentJni][stream_failed] reason=callback_exception");
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
        g_agent->chat_stream(session_id, msg, *callback);
        ICRAW_LOG_DEBUG("[NativeAgentJni][stream_complete_debug] state=chat_stream_returned");
    } catch (const std::exception& e) {
        // Report error to Java via callback
        callback_ptr->reportError("cpp_exception", e.what());
        ICRAW_LOG_ERROR("[NativeAgentJni][stream_failed] error_code=cpp_exception message={}", e.what());
    } catch (...) {
        callback_ptr->reportError("unknown_error", "Unknown exception in chat_stream");
        ICRAW_LOG_ERROR("[NativeAgentJni][stream_failed] error_code=unknown_error");
    }

    // Clean up global reference
    env->DeleteGlobalRef(listener_global_ref);

    env->ReleaseStringUTFChars(sessionId, session_id);
    env->ReleaseStringUTFChars(message, msg);
}

/**
 * Cancel the current streaming request
 */
JNIEXPORT void JNICALL Java_com_hh_agent_core_NativeAgent_nativeCancelStream(
        JNIEnv* env,
        jclass /* clazz */) {

    ICRAW_LOG_INFO("[NativeAgentJni][stream_cancel_start]");

    if (!g_agent) {
        ICRAW_LOG_WARN("[NativeAgentJni][stream_cancel_skipped] reason=agent_not_initialized");
        return;
    }

    // Call stop on the agent to cancel the streaming request
    g_agent->stop();
    ICRAW_LOG_INFO("[NativeAgentJni][stream_cancel_complete]");
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

    ICRAW_LOG_INFO("[NativeAgentJni][history_query_start] session_id={} limit={}", session_id, limit);

    std::vector<std::string> roles = {"user", "assistant"};

    std::vector<icraw::MemoryEntry> entries;

    if (g_agent) {
        try {
            auto memory_manager = g_agent->get_memory_manager();
            if (memory_manager) {
                entries = memory_manager->get_recent_messages_by_roles(static_cast<int>(limit), roles, session_id);
            }
        } catch (const std::exception& e) {
            ICRAW_LOG_ERROR("[NativeAgentJni][history_query_failed] message={}", e.what());
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

    ICRAW_LOG_INFO("[NativeAgentJni][history_query_complete] message_count={}", entries.size());

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
