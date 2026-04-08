package com.hh.agent.core;

import com.hh.agent.core.event.AgentEventListener;
import com.hh.agent.core.log.AgentLogger;
import com.hh.agent.core.log.AgentLogs;
import com.hh.agent.core.tool.AndroidToolCallback;

/**
 * Native agent JNI wrapper class
 * Provides Java interface to the C++ agent engine
 */
public class NativeAgent {

    static {
        System.loadLibrary("icraw");
    }

    public static void setLogger(AgentLogger logger) {
        AgentLogs.setLogger(logger);
        nativeSetLogger(logger);
    }

    public static AgentLogger getLogger() {
        return AgentLogs.getLogger();
    }

    public static void resetLogger() {
        AgentLogs.resetLogger();
        nativeSetLogger(null);
    }

    /**
     * Get the native agent version
     *
     * @return Version string
     */
    public static native String nativeGetVersion();

    /**
     * Initialize the native agent
     *
     * @param configJson JSON configuration string
     * @return 0 on success, -1 on failure
     */
    public static native int nativeInitialize(String configJson);

    /**
     * Shutdown the native agent
     */
    public static native void nativeShutdown();

    /**
     * Get recent messages from SQLite database
     *
     * @param sessionId The session identifier
     * @param limit Maximum number of messages to return
     * @return JSON array string of messages: [{"role": "user", "content": "...", "timestamp": "..."}, ...]
     */
    public static native String nativeGetHistory(String sessionId, int limit);

    /**
     * Clear persisted chat history for a session.
     */
    public static native boolean nativeClearHistory(String sessionId);

    /**
     * Clear persisted long-term memory for a session.
     */
    public static native boolean nativeClearLongTermMemory(String sessionId);

    /**
     * Clear persisted daily memory logs globally.
     */
    public static native boolean nativeClearDailyMemory();

    /**
     * Register an Android tool callback
     *
     * @param callback The callback implementation
     */
    public static void registerAndroidToolCallback(AndroidToolCallback callback) {
        // Call native method to register callback in C++ layer
        nativeRegisterAndroidToolCallback(callback);
    }

    /**
     * Native method to register Android tool callback in C++ layer
     */
    private static native void nativeRegisterAndroidToolCallback(AndroidToolCallback callback);

    /**
     * Set tools schema from JSON string
     * This allows Java to pass tools.json content to C++ for tool registration
     *
     * @param schemaJson JSON string containing tools schema
     */
    public static native void nativeSetToolsSchema(String schemaJson);

    /**
     * Bridge Java logger injection to native logging backend.
     * Passing null resets native logging to its default backend.
     */
    private static native void nativeSetLogger(AgentLogger logger);

    /**
     * Set native log level explicitly from Java.
     */
    public static void setNativeLogLevel(String level) {
        nativeSetLogLevel(level);
    }

    private static native void nativeSetLogLevel(String level);

    /**
     * Send a message with streaming event callback
     *
     * @param message The message to send
     * @param listener The event listener to receive stream events
     */
    public static void sendMessageStream(String sessionId, String message, AgentEventListener listener) {
        nativeSendMessageStream(sessionId, message, listener);
    }

    /**
     * Native method to send message with streaming callback
     */
    private static native void nativeSendMessageStream(String sessionId,
                                                       String message,
                                                       AgentEventListener listener);

    /**
     * Cancel the current streaming request
     */
    public static void cancelStream() {
        nativeCancelStream();
    }

    /**
     * Native method to cancel streaming request
     */
    private static native void nativeCancelStream();
}
