#include <jni.h>
#include <string>
#include <memory>
#include <curl/curl.h>
#include "icraw/core/logger.hpp"
#include "icraw/mobile_agent.hpp"
#include "icraw/config.hpp"
#include <nlohmann/json.hpp>

// ICRAW_ANDROID is already defined by CMake, no need to redefine

// Global MobileAgent instance
static std::unique_ptr<icraw::MobileAgent> g_agent;

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
JNIEXPORT jstring JNICALL Java_com_hh_agent_library_NativeAgent_nativeGetVersion(
        JNIEnv* env,
        jclass /* clazz */) {
    std::string version = "1.0.0-native";
    return env->NewStringUTF(version.c_str());
}

/**
 * Initialize the native agent
 * Returns: 0 on success, -1 on failure
 */
JNIEXPORT jint JNICALL Java_com_hh_agent_library_NativeAgent_nativeInitialize(
        JNIEnv* env,
        jclass /* clazz */,
        jstring configJsonStr) {
    // Get config JSON from Java
    const char* config_json = nullptr;
    if (configJsonStr) {
        config_json = env->GetStringUTFChars(configJsonStr, nullptr);
    }

    icraw::Logger::get_instance().logger()->info("nativeInitialize: Starting initialization");

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
                // Ensure workspace path is set (load_default sets the default path)
        icraw::IcrawConfig default_config = icraw::IcrawConfig::load_default();
        if (config.workspace_path.empty()) {
            config.workspace_path = default_config.workspace_path;
        }

        icraw::Logger::get_instance().logger()->info("Loaded config from JSON: apiKey set={}, baseUrl={}, model={}, workspace={}",
                    !config.provider.api_key.empty(), config.provider.base_url, config.agent.model, config.workspace_path.string());
            } catch (const std::exception& e) {
                icraw::Logger::get_instance().logger()->warn("Failed to parse config JSON: {}", e.what());
                config = icraw::IcrawConfig::load_default();
            }
        } else {
            icraw::Logger::get_instance().logger()->info("No config JSON provided, using defaults");
            config = icraw::IcrawConfig::load_default();
        }

        icraw::Logger::get_instance().logger()->info("Creating MobileAgent with config: model={}, workspace={}",
            config.agent.model, config.workspace_path.string());

        g_agent = icraw::MobileAgent::create_with_config(config);

        icraw::Logger::get_instance().logger()->info(
            "NativeAgent initialized successfully");
    } catch (const std::exception& e) {
        icraw::Logger::get_instance().logger()->error(
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
JNIEXPORT jstring JNICALL Java_com_hh_agent_library_NativeAgent_nativeSendMessage(
        JNIEnv* env,
        jclass /* clazz */,
        jstring message) {
    const char* msg = env->GetStringUTFChars(message, nullptr);
    std::string response;

    if (!msg) {
        return env->NewStringUTF("");
    }

    icraw::Logger::get_instance().logger()->debug("Received message: {}", msg);

    // Check if agent is initialized
    if (!g_agent) {
        icraw::Logger::get_instance().logger()->warn("Agent not initialized, returning error");
        response = "Error: Agent not initialized. Call nativeInitialize first.";
    } else {
        try {
            // Call MobileAgent::chat()
            response = g_agent->chat(msg);
            icraw::Logger::get_instance().logger()->debug("Agent response: {}", response);
        } catch (const std::exception& e) {
            icraw::Logger::get_instance().logger()->error("Agent chat failed: {}", e.what());
            response = std::string("Error: ") + e.what();
        }
    }

    env->ReleaseStringUTFChars(message, msg);
    return env->NewStringUTF(response.c_str());
}

/**
 * Shutdown the native agent
 */
JNIEXPORT void JNICALL Java_com_hh_agent_library_NativeAgent_nativeShutdown(
        JNIEnv* env,
        jclass /* clazz */) {
    icraw::Logger::get_instance().logger()->info("Shutting down NativeAgent");

    // Clean up MobileAgent instance
    if (g_agent) {
        g_agent->stop();
        g_agent.reset();
        icraw::Logger::get_instance().logger()->info("MobileAgent destroyed");
    }
}

} // extern "C"
