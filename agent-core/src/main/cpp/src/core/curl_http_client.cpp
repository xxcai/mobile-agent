#include "icraw/core/http_client.hpp"
#include "icraw/log/logger.hpp"
#include "icraw/log/log_utils.hpp"
#include <curl/curl.h>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdlib>
#include <memory>
#include <sstream>

namespace icraw {

namespace {

using CurlHandlePtr = std::unique_ptr<CURL, decltype(&curl_easy_cleanup)>;
using CurlHeadersPtr = std::unique_ptr<curl_slist, decltype(&curl_slist_free_all)>;

CurlHandlePtr create_curl_handle() {
    return CurlHandlePtr(curl_easy_init(), &curl_easy_cleanup);
}

CurlHeadersPtr build_headers(const std::string& method,
                             const std::string& request_body,
                             const HttpHeaders& headers) {
    curl_slist* raw_headers = nullptr;

    if (method == "POST" && !request_body.empty()) {
        raw_headers = curl_slist_append(raw_headers, "Content-Type: application/json");
    }

    for (const auto& [key, value] : headers) {
        std::string header = key + ": " + value;
        raw_headers = curl_slist_append(raw_headers, header.c_str());
    }

    return CurlHeadersPtr(raw_headers, &curl_slist_free_all);
}

std::string resolve_curl_error(CURLcode code, const char* errbuf) {
    if (errbuf && errbuf[0] != '\0') {
        return errbuf;
    }
    return curl_easy_strerror(code);
}

long long seconds_to_millis(double seconds) {
    if (seconds < 0.0) {
        return -1;
    }
    return static_cast<long long>(seconds * 1000.0 + 0.5);
}

bool is_truthy_env_value(const char* value) {
    if (value == nullptr) {
        return false;
    }
    std::string normalized(value);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return normalized == "1"
            || normalized == "true"
            || normalized == "yes"
            || normalized == "on"
            || normalized == "debug"
            || normalized == "verbose";
}

bool verbose_http_debug_enabled() {
    return is_truthy_env_value(std::getenv("ICRAW_VERBOSE_HTTP_DEBUG"))
            || is_truthy_env_value(std::getenv("ICRAW_VERBOSE_STREAM_DEBUG"))
            || is_truthy_env_value(std::getenv("ICRAW_VERBOSE_PAYLOAD_LOGGING"));
}

NetworkTimingMetrics collect_network_timing_metrics(CURL* curl) {
    NetworkTimingMetrics metrics;
    if (!curl) {
        return metrics;
    }

    double name_lookup = -1.0;
    double connect = -1.0;
    double app_connect = -1.0;
    double start_transfer = -1.0;
    double total = -1.0;

    curl_easy_getinfo(curl, CURLINFO_NAMELOOKUP_TIME, &name_lookup);
    curl_easy_getinfo(curl, CURLINFO_CONNECT_TIME, &connect);
    curl_easy_getinfo(curl, CURLINFO_APPCONNECT_TIME, &app_connect);
    curl_easy_getinfo(curl, CURLINFO_STARTTRANSFER_TIME, &start_transfer);
    curl_easy_getinfo(curl, CURLINFO_TOTAL_TIME, &total);

    metrics.dns_ms = seconds_to_millis(name_lookup);
    const long long connect_total_ms = seconds_to_millis(connect);
    const long long tls_total_ms = seconds_to_millis(app_connect);
    const long long start_transfer_ms = seconds_to_millis(start_transfer);
    metrics.total_ms = seconds_to_millis(total);

    if (connect_total_ms >= 0 && metrics.dns_ms >= 0) {
        metrics.connect_ms = std::max(0LL, connect_total_ms - metrics.dns_ms);
    } else {
        metrics.connect_ms = connect_total_ms;
    }

    if (tls_total_ms >= 0) {
        const long long connect_phase_end_ms = connect_total_ms >= 0 ? connect_total_ms : 0;
        metrics.tls_ms = std::max(0LL, tls_total_ms - connect_phase_end_ms);
    }

    const long long request_ready_ms = tls_total_ms >= 0
            ? tls_total_ms
            : (connect_total_ms >= 0 ? connect_total_ms : 0);
    if (start_transfer_ms >= 0) {
        metrics.ttfb_ms = std::max(0LL, start_transfer_ms - request_ready_ms);
    }
    if (metrics.total_ms >= 0 && start_transfer_ms >= 0) {
        metrics.download_ms = std::max(0LL, metrics.total_ms - start_transfer_ms);
    }

    return metrics;
}

void apply_common_curl_options(CURL* curl, long timeout_seconds) {
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeout_seconds);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 15L);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
#ifdef CURLOPT_TCP_KEEPALIVE
    curl_easy_setopt(curl, CURLOPT_TCP_KEEPALIVE, 1L);
#endif
#ifdef CURLOPT_TCP_KEEPIDLE
    curl_easy_setopt(curl, CURLOPT_TCP_KEEPIDLE, 30L);
#endif
#ifdef CURLOPT_TCP_KEEPINTVL
    curl_easy_setopt(curl, CURLOPT_TCP_KEEPINTVL, 15L);
#endif
#ifdef CURLOPT_HTTP_VERSION
#ifdef CURL_HTTP_VERSION_2TLS
    curl_easy_setopt(curl, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_2TLS);
#endif
#endif
}

} // namespace

CurlHttpClient::CurlHttpClient() = default;

CurlHttpClient::~CurlHttpClient() = default;

std::optional<NetworkTimingMetrics> CurlHttpClient::get_last_timing_metrics() const {
    return last_timing_metrics_;
}

bool CurlHttpClient::perform_request(const std::string& url,
                                     const std::string& method,
                                     const std::string& request_body,
                                     std::string& response_body,
                                     std::map<std::string, std::string>& response_headers,
                                     HttpError& error,
                                     const HttpHeaders& headers) {
    auto start_time = std::chrono::steady_clock::now();
    last_timing_metrics_.reset();

    auto curl = create_curl_handle();
    if (!curl) {
        error.code = -1;
        error.message = "Curl initialization failed";
        return false;
    }

    curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl.get(), CURLOPT_CUSTOMREQUEST, method.c_str());

    if (method == "POST" && !request_body.empty()) {
        curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDS, request_body.c_str());
        curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDSIZE, request_body.size());
    }

    auto curl_headers = build_headers(method, request_body, headers);
    if (curl_headers) {
        curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, curl_headers.get());
    }

    curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, CurlHttpClient::WriteCallback);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &response_body);
    curl_easy_setopt(curl.get(), CURLOPT_HEADERFUNCTION, CurlHttpClient::HeaderCallback);
    curl_easy_setopt(curl.get(), CURLOPT_HEADERDATA, &response_headers);

    char errbuf[CURL_ERROR_SIZE] = {0};
    curl_easy_setopt(curl.get(), CURLOPT_ERRORBUFFER, errbuf);
    apply_common_curl_options(curl.get(), 120L);

    ICRAW_LOG_INFO("[HttpClient][request_start] method={} url={} request_body_length={}",
            method, url, request_body.size());
    if (!request_body.empty() && verbose_http_debug_enabled()) {
        ICRAW_LOG_DEBUG("[HttpClient][request_debug] method={} body={}",
                method, log_utils::truncate_for_debug(request_body));
    }

    CURLcode res = curl_easy_perform(curl.get());
    last_timing_metrics_ = collect_network_timing_metrics(curl.get());

    if (res != CURLE_OK) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = -1;
        error.message = resolve_curl_error(res, errbuf);
        ICRAW_LOG_ERROR("[HttpClient][request_failed] method={} url={} duration_ms={} error_code={} message={}",
                method, url, elapsed, static_cast<int>(res), error.message);
        return false;
    }

    long http_code = 0;
    curl_easy_getinfo(curl.get(), CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code >= 400) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = static_cast<int>(http_code);
        error.message = "HTTP error: " + std::to_string(http_code);
        ICRAW_LOG_WARN("[HttpClient][request_failed] method={} url={} duration_ms={} status_code={}",
                method, url, elapsed, http_code);
        if (!response_body.empty() && verbose_http_debug_enabled()) {
            ICRAW_LOG_DEBUG("[HttpClient][response_debug] method={} body={}",
                    method, log_utils::truncate_for_debug(response_body));
        }
        return false;
    }

    auto end_time = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[HttpClient][request_complete] method={} url={} status_code={} duration_ms={} response_length={}",
            method, url, http_code, elapsed, response_body.size());
    if (!response_body.empty() && verbose_http_debug_enabled()) {
        ICRAW_LOG_DEBUG("[HttpClient][response_debug] method={} body={}",
                method, log_utils::truncate_for_debug(response_body));
    }

    return true;
}

bool CurlHttpClient::perform_request_stream(const std::string& url,
                                            const std::string& method,
                                            const std::string& request_body,
                                            StreamCallback callback,
                                            HttpError& error,
                                            const HttpHeaders& headers) {
    auto start_time = std::chrono::steady_clock::now();
    last_timing_metrics_.reset();

    auto curl = create_curl_handle();
    if (!curl) {
        error.code = -1;
        error.message = "Curl initialization failed";
        return false;
    }

    if (!callback) {
        error.code = -1;
        error.message = "No callback provided";
        return false;
    }

    curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl.get(), CURLOPT_CUSTOMREQUEST, method.c_str());

    if (method == "POST" && !request_body.empty()) {
        curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDS, request_body.c_str());
        curl_easy_setopt(curl.get(), CURLOPT_POSTFIELDSIZE, request_body.size());
    }

    auto curl_headers = build_headers(method, request_body, headers);
    if (curl_headers) {
        curl_easy_setopt(curl.get(), CURLOPT_HTTPHEADER, curl_headers.get());
    }

    StreamCallbackData callback_data;
    callback_data.callback = callback;
    callback_data.aborted = false;

    curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, CurlHttpClient::StreamWriteCallback);
    curl_easy_setopt(curl.get(), CURLOPT_WRITEDATA, &callback_data);

    char errbuf[CURL_ERROR_SIZE] = {0};
    curl_easy_setopt(curl.get(), CURLOPT_ERRORBUFFER, errbuf);
    apply_common_curl_options(curl.get(), 300L);
    curl_easy_setopt(curl.get(), CURLOPT_BUFFERSIZE, 1024L);

    ICRAW_LOG_INFO("[HttpClient][stream_request_start] method={} url={} request_body_length={}",
            method, url, request_body.size());
    if (!request_body.empty() && verbose_http_debug_enabled()) {
        ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] method={} body={}",
                method, log_utils::truncate_for_debug(request_body));
    }

    CURLcode res = curl_easy_perform(curl.get());
    last_timing_metrics_ = collect_network_timing_metrics(curl.get());
    if (verbose_http_debug_enabled()) {
        ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] curl_result={} aborted={}",
                static_cast<int>(res), callback_data.aborted);
    }

    if (res != CURLE_OK && res != CURLE_WRITE_ERROR) {
        error.code = -1;
        error.message = resolve_curl_error(res, errbuf);
        ICRAW_LOG_ERROR("[HttpClient][stream_request_failed] method={} url={} error_code={} message={}",
                method, url, static_cast<int>(res), error.message);
        if (!callback_data.raw_response.empty() && verbose_http_debug_enabled()) {
            ICRAW_LOG_WARN("[HttpClient][stream_response_body] method={} body={}",
                    method, log_utils::truncate_for_debug(callback_data.raw_response));
        }
        return false;
    }

    if (res == CURLE_WRITE_ERROR && callback_data.aborted) {
        if (verbose_http_debug_enabled()) {
            ICRAW_LOG_DEBUG("[HttpClient][stream_request_debug] state=aborted_intentionally");
        }
        return true;
    }

    long http_code = 0;
    curl_easy_getinfo(curl.get(), CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code >= 400) {
        auto end_time = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        error.code = static_cast<int>(http_code);
        error.message = "HTTP error: " + std::to_string(http_code);
        ICRAW_LOG_WARN("[HttpClient][stream_request_failed] method={} url={} duration_ms={} status_code={}",
                method, url, elapsed, http_code);
        if (!callback_data.raw_response.empty() && verbose_http_debug_enabled()) {
            ICRAW_LOG_WARN("[HttpClient][stream_response_body] method={} body={}",
                    method, log_utils::truncate_for_debug(callback_data.raw_response));
        }
        return false;
    }

    auto end_time = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    ICRAW_LOG_INFO("[HttpClient][stream_request_complete] method={} url={} duration_ms={}",
            method, url, elapsed);

    return true;
}

size_t CurlHttpClient::WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    std::string* str = static_cast<std::string*>(userp);
    size_t total_size = size * nmemb;
    str->append(static_cast<char*>(contents), total_size);
    return total_size;
}

size_t CurlHttpClient::HeaderCallback(char* buffer, size_t size, size_t nitems, void* userdata) {
    size_t total_size = size * nitems;
    std::map<std::string, std::string>* headers = static_cast<std::map<std::string, std::string>*>(userdata);

    std::string header(buffer, total_size);
    size_t colon_pos = header.find(':');
    if (colon_pos != std::string::npos) {
        std::string key = header.substr(0, colon_pos);
        std::string value = header.substr(colon_pos + 2);

        while (!value.empty() && (value.back() == '\r' || value.back() == '\n')) {
            value.pop_back();
        }
        while (!key.empty() && (key.back() == ' ' || key.back() == '\t')) {
            key.pop_back();
        }

        (*headers)[key] = value;
    }

    return total_size;
}

size_t CurlHttpClient::StreamWriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    StreamCallbackData* data = static_cast<StreamCallbackData*>(userp);
    size_t total_size = size * nmemb;

    if (data->aborted) {
        if (verbose_http_debug_enabled()) {
            ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] state=already_aborted");
        }
        return 0;
    }

    data->buffer.append(static_cast<char*>(contents), total_size);
    data->raw_response.append(static_cast<char*>(contents), total_size);

    size_t pos = 0;
    while ((pos = data->buffer.find("\n\n")) != std::string::npos
           || (pos = data->buffer.find("\r\n\r\n")) != std::string::npos) {
        size_t event_end = pos;
        if (data->buffer[pos] == '\r') {
            event_end = pos + 4;
        } else {
            event_end = pos + 2;
        }

        std::string event = data->buffer.substr(0, pos);
        data->buffer = data->buffer.substr(event_end);

        if (event.empty() || event == "\r") {
            continue;
        }

        if (verbose_http_debug_enabled()) {
            ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] event_length={} preview={}",
                    event.length(), log_utils::truncate_for_debug(event));
        }

        if (data->callback) {
            bool continue_stream = data->callback(event);
            if (!continue_stream) {
                if (verbose_http_debug_enabled()) {
                    ICRAW_LOG_DEBUG("[HttpClient][stream_callback_debug] state=callback_requested_abort");
                }
                data->aborted = true;
                return 0;
            }
        }
    }

    return total_size;
}

} // namespace icraw
