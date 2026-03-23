#include "icraw/core/http_client.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <curl/curl.h>
#include <algorithm>
#include <sstream>

namespace icraw {

// CurlHttpClient implementation

CurlHttpClient::CurlHttpClient() : curl_(curl_easy_init()) {
}

CurlHttpClient::~CurlHttpClient() {
    if (curl_) {
        curl_easy_cleanup(static_cast<CURL*>(curl_));
    }
}

// Non-streaming request
bool CurlHttpClient::perform_request(const std::string& url,
                                     const std::string& method,
                                     const std::string& request_body,
                                     std::string& response_body,
                                     std::map<std::string, std::string>& response_headers,
                                     HttpError& error,
                                     const HttpHeaders& headers) {
    auto start_time = std::chrono::steady_clock::now();

    CURL* curl = static_cast<CURL*>(curl_);
    if (!curl) {
        error.code = -1;
        error.message = "Curl not initialized";
        return false;
    }
    
    // Reset the curl handle
    curl_easy_reset(curl);
    
    // Set the URL
    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    
    // Set the HTTP method (GET or POST)
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method.c_str());
    
    // Set the request body for POST
    if (method == "POST" && !request_body.empty()) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, request_body.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, request_body.size());
    }
    
    // Build headers list
    struct curl_slist* curl_headers = nullptr;
    
    // Add Content-Type header for JSON requests
    if (method == "POST" && !request_body.empty()) {
        curl_headers = curl_slist_append(curl_headers, "Content-Type: application/json");
    }
    
    // Add custom headers
    for (const auto& [key, value] : headers) {
        std::string header = key + ": " + value;
        curl_headers = curl_slist_append(curl_headers, header.c_str());
    }
    
    // Set headers if any
    if (curl_headers) {
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, curl_headers);
    }
    
    // Set the write function for the response body
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, CurlHttpClient::WriteCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_body);
    
    // Set the header function for the response headers
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, CurlHttpClient::HeaderCallback);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, &response_headers);
    
    // Set up error buffer
    char errbuf[CURL_ERROR_SIZE];
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, errbuf);
    
    // Set a reasonable timeout
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 120L);
    
    // Follow redirects
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);

    // Enable SSL/TLS (disable for Android development)
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);

    // Perform the request
    ICRAW_LOG_INFO("[HttpClient][request_start] method={} url={} request_body_length={}",
            method, url, request_body.size());
    if (!request_body.empty()) {
        ICRAW_LOG_DEBUG("[HttpClient][request_debug] method={} body={}",
                method, log_utils::truncate_for_debug(request_body));
    }
    CURLcode res = curl_easy_perform(curl);
    
    // Free headers list
    if (curl_headers) {
        curl_slist_free_all(curl_headers);
    }
    
    if (res != CURLE_OK) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = -1;
        error.message = curl_easy_strerror(res);
        ICRAW_LOG_ERROR("[HttpClient][request_failed] method={} url={} duration_ms={} error_code={} message={}",
                method, url, elapsed, static_cast<int>(res), error.message);
        return false;
    }
    
    // Get the HTTP response code
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code >= 400) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = static_cast<int>(http_code);
        error.message = "HTTP error: " + std::to_string(http_code);
        ICRAW_LOG_WARN("[HttpClient][request_failed] method={} url={} duration_ms={} status_code={}",
                method, url, elapsed, http_code);
        if (!response_body.empty()) {
            ICRAW_LOG_DEBUG("[HttpClient][response_debug] method={} body={}",
                    method, log_utils::truncate_for_debug(response_body));
        }
        return false;
    }

    auto end_time = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[HttpClient][request_complete] method={} url={} status_code={} duration_ms={} response_length={}",
            method, url, http_code, elapsed, response_body.size());
    if (!response_body.empty()) {
        ICRAW_LOG_DEBUG("[HttpClient][response_debug] method={} body={}",
                method, log_utils::truncate_for_debug(response_body));
    }

    return true;
}

// Streaming request
bool CurlHttpClient::perform_request_stream(const std::string& url,
                                            const std::string& method,
                                            const std::string& request_body,
                                            StreamCallback callback,
                                            HttpError& error,
                                            const HttpHeaders& headers) {
    auto start_time = std::chrono::steady_clock::now();

    CURL* curl = static_cast<CURL*>(curl_);
    if (!curl) {
        error.code = -1;
        error.message = "Curl not initialized";
        return false;
    }
    
    if (!callback) {
        error.code = -1;
        error.message = "No callback provided";
        return false;
    }
    
    // Reset the curl handle
    curl_easy_reset(curl);
    
    // Set the URL
    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    
    // Set the HTTP method (GET or POST)
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method.c_str());
    
    // Set the request body for POST
    if (method == "POST" && !request_body.empty()) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, request_body.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, request_body.size());
    }
    
    // Build headers list
    struct curl_slist* curl_headers = nullptr;
    
    // Add Content-Type header for JSON requests
    if (method == "POST" && !request_body.empty()) {
        curl_headers = curl_slist_append(curl_headers, "Content-Type: application/json");
    }
    
    // Add custom headers
    for (const auto& [key, value] : headers) {
        std::string header = key + ": " + value;
        curl_headers = curl_slist_append(curl_headers, header.c_str());
    }
    
    // Set headers if any
    if (curl_headers) {
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, curl_headers);
    }
    
    // Set up callback data
    StreamCallbackData callback_data;
    callback_data.callback = callback;
    callback_data.aborted = false;
    
    // Set the write function for streaming
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, CurlHttpClient::StreamWriteCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &callback_data);
    
    // Set up error buffer
    char errbuf[CURL_ERROR_SIZE];
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, errbuf);
    
    // Set a reasonable timeout (longer for streaming)
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 300L);
    
    // Follow redirects
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);

    // Enable SSL/TLS (disable for Android development)
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);

    // Disable buffering for real-time streaming
    curl_easy_setopt(curl, CURLOPT_BUFFERSIZE, 1L);
    
    // Perform the request
    ICRAW_LOG_INFO("[HttpClient][stream_request_start] method={} url={} request_body_length={}",
            method, url, request_body.size());
    if (!request_body.empty()) {
        ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] method={} body={}",
                method, log_utils::truncate_for_debug(request_body));
    }
    CURLcode res = curl_easy_perform(curl);
    ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] curl_result={} aborted={}", 
        static_cast<int>(res), callback_data.aborted);
    
    // Free headers list
    if (curl_headers) {
        curl_slist_free_all(curl_headers);
    }
    
    if (res != CURLE_OK && res != CURLE_WRITE_ERROR) {
        error.code = -1;
        error.message = curl_easy_strerror(res);
        ICRAW_LOG_ERROR("[HttpClient][stream_request_failed] method={} url={} error_code={} message={}",
                method, url, static_cast<int>(res), error.message);
        return false;
    }
    
    // CURLE_WRITE_ERROR is expected when we abort the stream intentionally
    if (res == CURLE_WRITE_ERROR && callback_data.aborted) {
        ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] state=aborted_intentionally");
        // This is a normal stream end, not an error
        return true;
    }
    
    // Get the HTTP response code
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    if (http_code >= 400) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = static_cast<int>(http_code);
        error.message = "HTTP error: " + std::to_string(http_code);
        ICRAW_LOG_WARN("[HttpClient][stream_request_failed] method={} url={} duration_ms={} status_code={}",
                method, url, elapsed, http_code);
        return false;
    }

    auto end_time = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[HttpClient][stream_request_complete] method={} url={} duration_ms={}",
            method, url, elapsed);

    return true;
}

// Callback function for writing the response body (non-streaming)
size_t CurlHttpClient::WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    std::string* str = static_cast<std::string*>(userp);
    size_t total_size = size * nmemb;
    str->append(static_cast<char*>(contents), total_size);
    return total_size;
}

// Callback function for writing the response headers
size_t CurlHttpClient::HeaderCallback(char* buffer, size_t size, size_t nitems, void* userdata) {
    size_t total_size = size * nitems;
    std::map<std::string, std::string>* headers = static_cast<std::map<std::string, std::string>*>(userdata);
    
    std::string header(buffer, total_size);
    
    // Find the colon that separates the key and value
    size_t colon_pos = header.find(':');
    if (colon_pos != std::string::npos) {
        std::string key = header.substr(0, colon_pos);
        std::string value = header.substr(colon_pos + 2);
        
        // Trim the trailing CRLF from the value
        while (!value.empty() && (value.back() == '\r' || value.back() == '\n')) {
            value.pop_back();
        }
        
        // Trim trailing whitespace from the key
        while (!key.empty() && (key.back() == ' ' || key.back() == '\t')) {
            key.pop_back();
        }
        
        (*headers)[key] = value;
    }
    
    return total_size;
}

// Callback function for streaming responses (SSE format)
size_t CurlHttpClient::StreamWriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    StreamCallbackData* data = static_cast<StreamCallbackData*>(userp);
    size_t total_size = size * nmemb;
    
    if (data->aborted) {
        ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] state=already_aborted");
        return 0;  // Abort the transfer
    }
    
    // Append new data to buffer
    data->buffer.append(static_cast<char*>(contents), total_size);
    
    // Process complete lines (SSE format: lines ending with \n\n or \r\n\r\n)
    size_t pos = 0;
    while ((pos = data->buffer.find("\n\n")) != std::string::npos ||
           (pos = data->buffer.find("\r\n\r\n")) != std::string::npos) {
        // Adjust for \r\n\r\n case
        size_t event_end = pos;
        if (data->buffer[pos] == '\r') {
            event_end = pos + 4;  // Skip \r\n\r\n
        } else {
            event_end = pos + 2;  // Skip \n\n
        }
        
        // Extract the event
        std::string event = data->buffer.substr(0, pos);
        
        // Remove processed data from buffer
        data->buffer = data->buffer.substr(event_end);
        
        // Skip empty events
        if (event.empty() || event == "\r") {
            continue;
        }
        
        ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] event_length={} preview={}",
            event.length(), log_utils::truncate_for_debug(event));
        
        // Call the callback with the event
        if (data->callback) {
            bool continue_stream = data->callback(event);
            if (!continue_stream) {
                ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] state=callback_requested_abort");
                data->aborted = true;
                return 0;  // Abort the transfer
            }
        }
    }
    
    return total_size;
}

} // namespace icraw
         
