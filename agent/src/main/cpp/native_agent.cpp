#include <jni.h>
#include <string>
#include "icraw/core/logger.hpp"
#include "icraw/mobile_agent.hpp"

// Define ICRAW_ANDROID for Android build
#define ICRAW_ANDROID

extern "C" {

/**
 * JNI OnLoad - Called when the native library is loaded
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Initialize the logger
    icraw::Logger::get_instance().init("/sdcard/logs", "debug");

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
 */
JNIEXPORT void JNICALL Java_com_hh_agent_library_NativeAgent_nativeInitialize(
        JNIEnv* env,
        jclass /* clazz */,
        jstring configPath) {
    const char* path = env->GetStringUTFChars(configPath, nullptr);
    if (path) {
        icraw::Logger::get_instance().logger()->info("Initializing NativeAgent with config: {}", path);
        env->ReleaseStringUTFChars(configPath, path);
    }
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

    if (msg) {
        icraw::Logger::get_instance().logger()->debug("Received message: {}", msg);

        // Echo back for now - full agent implementation will come later
        response = std::string("Echo: ") + msg;

        env->ReleaseStringUTFChars(message, msg);
    }

    return env->NewStringUTF(response.c_str());
}

/**
 * Shutdown the native agent
 */
JNIEXPORT void JNICALL Java_com_hh_agent_library_NativeAgent_nativeShutdown(
        JNIEnv* env,
        jclass /* clazz */) {
    icraw::Logger::get_instance().logger()->info("Shutting down NativeAgent");
}

} // extern "C"
