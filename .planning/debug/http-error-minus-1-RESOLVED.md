# HTTP Error -1 Debug Report

## Investigation Summary

### Root Cause Analysis

The error "HTTP error -1:" is generated in the following code flow:

1. **`curl_http_client.cpp`** - When curl fails (line 95-98):
   ```cpp
   if (res != CURLE_OK) {
       error.code = -1;
       error.message = curl_easy_strerror(res);  // Actual error message is here!
       return false;
   }
   ```

2. **`llm_provider.cpp`** - Error response construction (line 390, 393):
   ```cpp
   response.content = "HTTP error " + std::to_string(error.code) + ": " + response_body;
   ```

The `-1` is a placeholder error code. The **actual curl error message** is stored in `error.message` (e.g., "SSL connect error", "Could not resolve host", etc.).

### Key Findings

1. **API Configuration** (config.json):
   - baseUrl: `https://api.minimaxi.com/anthropic`
   - model: `MiniMax-M2.5-highspeed`
   - Full URL: `https://api.minimaxi.com/anthropic/chat/completions`

2. **Root Cause Identified**:
   - **OpenSSL libraries not properly linked** in CMakeLists.txt - the code was using direct library path `${libcurl_PACKAGE_FOLDER_RELEASE}/lib/libcurl.so` instead of the CMake target `CURL::libcurl` which automatically brings in OpenSSL dependencies
   - **No SSL/TLS configuration** in curl_http_client.cpp (missing CURLOPT_SSL_VERIFYPEER, CURLOPT_SSL_VERIFYHOST)
   - **No error logging** for curl failures

### Fixes Applied

#### 1. Added SSL configuration to curl_http_client.cpp

Added SSL options in both `perform_request` and `perform_request_stream`:

```cpp
// Enable SSL/TLS
curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
```

#### 2. Added error logging to curl_http_client.cpp

Added logging to capture actual curl error:

```cpp
if (res != CURLE_OK) {
    error.code = -1;
    error.message = curl_easy_strerror(res);
    ICRAW_LOG_ERROR("[HTTP] Curl error: {} (code: {})", error.message, static_cast<int>(res));
    return false;
}
```

#### 3. Fixed CMakeLists.txt to properly link OpenSSL

Changed from:
```cmake
${libcurl_PACKAGE_FOLDER_RELEASE}/lib/libcurl.so
```

To:
```cmake
CURL::libcurl
```

This ensures OpenSSL is automatically linked as a dependency of libcurl.

Also added:
```cmake
find_package(OpenSSL CONFIG REQUIRED)
```

### Debugging Steps

#### Check adb logcat for native logs

```bash
adb logcat -s icraw:V *:W | grep -E "(HTTP|curl|STREAM|ChatCompletion)"
```

Look for lines containing:
- `[HTTP] Request URL:` - shows the URL being called
- `[HTTP] Curl error:` - shows the actual curl error message
- `[STREAM] perform_request_stream failed:` - shows the error

#### Verify network connectivity from device

```bash
adb shell ping -c 3 api.minimaxi.com
adb shell curl -v https://api.minimaxi.com/anthropic
```

### Expected Log Output After Fix

When the fix is applied, you should see logs like:
```
[HTTP] Request URL: https://api.minimaxi.com/anthropic/chat/completions
[STREAM] Starting curl_easy_perform for https://api.minimaxi.com/anthropic/chat/completions
[LLM_STREAM] perform_request_stream completed successfully
```

Or if there's still an error:
```
[HTTP] Request URL: https://api.minimaxi.com/anthropic/chat/completions
[HTTP] Curl error: Could not resolve host (code: 6)
```

### Related Files (Modified)

- `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/cpp/src/core/curl_http_client.cpp` - Added SSL config and error logging
- `/Users/caixiao/Workspace/projects/mobile-agent/agent/src/main/cpp/CMakeLists.txt` - Fixed OpenSSL linking
- `/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/config.json` - API configuration
