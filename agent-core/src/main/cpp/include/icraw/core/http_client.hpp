#pragma once

#include <string>
#include <map>
#include <functional>

namespace icraw {

struct HttpError {
    int code = 0;
    std::string message;
};

// Callback type for streaming responses - called for each chunk
// Returns true to continue streaming, false to abort
using StreamCallback = std::function<bool(const std::string& chunk)>;

// HTTP headers type
using HttpHeaders = std::map<std::string, std::string>;

class HttpClient {
public:
    virtual ~HttpClient() = default;
    
    // Non-streaming request with optional headers
    virtual bool perform_request(const std::string& url,
                                 const std::string& method,
                                 const std::string& request_body,
                                 std::string& response_body,
                                 std::map<std::string, std::string>& response_headers,
                                 HttpError& error,
                                 const HttpHeaders& headers = {}) {
        return false;
    }
    
    // Streaming request - callback is called for each data chunk
    virtual bool perform_request_stream(const std::string& url,
                                        const std::string& method,
                                        const std::string& request_body,
                                        StreamCallback callback,
                                        HttpError& error,
                                        const HttpHeaders& headers = {}) {
        return false;
    }
};

class CurlHttpClient : public HttpClient {
public:
    CurlHttpClient();
    ~CurlHttpClient() override;
    
    // Non-streaming request with optional headers
    bool perform_request(const std::string& url,
                         const std::string& method,
                         const std::string& request_body,
                         std::string& response_body,
                         std::map<std::string, std::string>& response_headers,
                         HttpError& error,
                         const HttpHeaders& headers = {}) override;
    
    // Streaming request with optional headers
    bool perform_request_stream(const std::string& url,
                                const std::string& method,
                                const std::string& request_body,
                                StreamCallback callback,
                                HttpError& error,
                                const HttpHeaders& headers = {}) override;

private:
    void* curl_;  // CURL* 
    
    // Callback data structure for streaming
    struct StreamCallbackData {
        StreamCallback callback;
        std::string buffer;  // Buffer for incomplete lines
        bool aborted;
    };
    
    static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp);
    static size_t HeaderCallback(char* buffer, size_t size, size_t nitems, void* userdata);
    static size_t StreamWriteCallback(void* contents, size_t size, size_t nmemb, void* userp);
};

} // namespace icraw
