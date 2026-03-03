package com.hh.agent.library;

/**
 * Native agent JNI wrapper class
 * Provides Java interface to the C++ agent engine
 */
public class NativeAgent {

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
     * @param configPath Path to configuration file
     */
    public static native void nativeInitialize(String configPath);

    /**
     * Send a message to the agent and get a response
     *
     * @param message The message to send
     * @return The agent's response
     */
    public static native String nativeSendMessage(String message);

    /**
     * Shutdown the native agent
     */
    public static native void nativeShutdown();
}
