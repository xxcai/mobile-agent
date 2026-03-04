# Debug: Native initialization failed (-1) - FIXED

## Summary

Native agent initialization was failing with return code -1. The issue has been identified and fixed.

## Root Cause

The workspace path was not being set in the IcrawConfig. When parsing the JSON config in `native_agent.cpp`, the code was setting `api_key`, `base_url`, and `model`, but NOT calling `load_default()` to get the default workspace path.

As a result, `config.workspace_path` was empty (`""`), causing `std::filesystem::create_directories("")` to fail with "No such file or directory".

## Files Modified

1. `/agent/src/main/cpp/native_agent.cpp`
   - Added `#include <curl/curl.h>` header
   - Added `curl_global_init()` call in `JNI_OnLoad()`
   - Added call to `load_default()` to set the workspace path when parsing JSON config

2. `/agent/src/main/cpp/src/mobile_agent.cpp`
   - Added detailed debug logging to track initialization progress

## Fix Implementation

### Fix 1: Added curl_global_init() to JNI_OnLoad

```cpp
// In JNI_OnLoad:
curl_global_init(CURL_GLOBAL_DEFAULT);
```

### Fix 2: Added workspace path initialization

```cpp
// Ensure workspace path is set (load_default sets the default path)
icraw::IcrawConfig default_config = icraw::IcrawConfig::load_default();
if (config.workspace_path.empty()) {
    config.workspace_path = default_config.workspace_path;
}
```

## Verification

After applying the fix, the logs show successful initialization:

```
icraw   : Loaded config from JSON: apiKey set=true, baseUrl=https://api.minimaxi.com/anthropic, model=MiniMax-M2.5-highspeed, workspace=/data/data/com.hh.agent/files/.icraw/workspace
icraw   : Creating MobileAgent with config: model=MiniMax-M2.5-highspeed, workspace=/data/data/com.hh.agent/files/.icraw/workspace
icraw   : MobileAgent: Creating workspace at /data/data/com.hh.agent/files/.icraw/workspace
icraw   : MobileAgent: Creating MemoryManager
...
icraw   : NativeAgent initialized successfully
```

## Test Commands

```bash
# Build and install
./gradlew :agent:assembleDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check logs
adb logcat -d | grep -iE "icraw|NativeAgent"
```

## Debugging Tips

For future issues, check these keywords in logcat:
- `icraw` - Native library logs
- `NativeAgent` - JNI layer logs
- `MobileAgent` - C++ agent logs
- `MainPresenter` - Java layer error logs

The most common failure points:
1. Workspace directory creation failure (check path is valid)
2. curl_global_init not called (check JNI_OnLoad)
3. Missing config fields (check load_default() is called)
