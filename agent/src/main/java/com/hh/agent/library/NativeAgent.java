package com.hh.agent.library;

/**
 * Native agent JNI wrapper class
 * Provides Java interface to the C++ agent engine
 */
public class NativeAgent {

    // Callback instance registered by Java layer
    private static AndroidToolCallback sCallback;

    static {
        System.loadLibrary("icraw");
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
     * Send a message to the agent and get a response
     *
     * @param message The message to send
     * @return The agent's response
     */
    public static native String nativeSendMessage(String message);

    /**
     * Call an Android tool
     *
     * @param toolName The name of the tool to call
     * @param argsJson JSON string containing tool arguments
     * @return JSON string with result
     */
    public static native String nativeCallAndroidTool(String toolName, String argsJson);

    /**
     * Shutdown the native agent
     */
    public static native void nativeShutdown();

    /**
     * Register an Android tool callback
     *
     * @param callback The callback implementation
     */
    public static void registerAndroidToolCallback(AndroidToolCallback callback) {
        sCallback = callback;
        // Call native method to register callback in C++ layer
        nativeRegisterAndroidToolCallback(callback);
    }

    /**
     * Native method to register Android tool callback in C++ layer
     */
    private static native void nativeRegisterAndroidToolCallback(AndroidToolCallback callback);

    /**
     * Get the registered Android tool callback
     *
     * @return The callback or null if not registered
     */
    public static AndroidToolCallback getAndroidToolCallback() {
        return sCallback;
    }
}
