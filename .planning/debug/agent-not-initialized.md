# Debug: Agent not initialized

## Issue Summary

点击发送后，显示错误信息: "Error: Agent not initialized. Call nativeInitialize first."

## Root Cause

The issue was that native agent initialization was failing silently in the C++ code. When `MobileAgent::create_with_config(config)` throws an exception (due to various reasons like filesystem issues, missing dependencies, etc.), the exception is caught and logged in C++, but **g_agent remains null**. The Java code doesn't know about this failure because no exception is propagated to Java.

The flow was:
1. Java calls `nativeInitialize` via JNI
2. C++ code catches exception internally and logs it
3. Function returns normally (void return type)
4. Java thinks initialization succeeded (no exception thrown)
5. When `sendMessage` is called, `g_agent` is null, so error is returned

## Fix Applied

### 1. Modified native_agent.cpp

Changed the return type of `nativeInitialize` from `void` to `jint` (int) to properly report success/failure:

- Returns 0 on success
- Returns -1 on failure (when exception occurs)

File: `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/cpp/native_agent.cpp`

### 2. Modified NativeAgent.java

Updated the JNI method signature to return int:

```java
public static native int nativeInitialize(String configJson);
```

File: `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/java/com/hh/agent/library/NativeAgent.java`

### 3. Modified NativeNanobotApi.java

Updated to check the return value and throw an exception if initialization fails:

```java
public synchronized void initialize(String configPath) {
    if (!initialized) {
        try {
            int result = NativeAgent.nativeInitialize(configPath);
            if (result != 0) {
                throw new RuntimeException("Native agent initialization failed with code: " + result);
            }
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize native agent: " + e.getMessage(), e);
        }
    }
}
```

File: `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/java/com/hh/agent/library/api/NativeNanobotApi.java`

## Build Verification

Build completed successfully:
- Agent module: BUILD SUCCESSFUL
- Native code: BUILD SUCCESSFUL
- App module: BUILD SUCCESSFUL
- APK generated: `app/build/outputs/apk/debug/app-debug.apk`

## Next Steps

1. Install the APK on the device and test
2. Check logcat for any initialization errors
3. If still failing, investigate the specific error in MobileAgent construction
