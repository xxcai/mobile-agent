#ifndef ICRAW_ANDROID_LOG_SINK_HPP
#define ICRAW_ANDROID_LOG_SINK_HPP

#include <spdlog/sinks/base_sink.h>
#include <android/log.h>

namespace spdlog {
namespace sinks {

/**
 * Android log sink for spdlog
 * Redirects spdlog messages to Android logcat
 */
class android_log_sink : public base_sink<std::mutex> {
public:
    explicit android_log_sink(const std::string& tag = "icraw")
        : tag_(tag) {}

protected:
    void sink_it_(const spdlog::details::log_msg& msg) override {
        // Convert spdlog level to Android log level
        android_LogPriority priority = ANDROID_LOG_DEBUG;

        switch (msg.level) {
            case spdlog::level::trace:
            case spdlog::level::debug:
                priority = ANDROID_LOG_DEBUG;
                break;
            case spdlog::level::info:
                priority = ANDROID_LOG_INFO;
                break;
            case spdlog::level::warn:
                priority = ANDROID_LOG_WARN;
                break;
            case spdlog::level::err:
            case spdlog::level::critical:
                priority = ANDROID_LOG_ERROR;
                break;
            default:
                priority = ANDROID_LOG_DEBUG;
                break;
        }

        // Format the message
        std::string payload(msg.payload.data(), msg.payload.size());

        // Write to Android logcat
        __android_log_print(priority, tag_.c_str(), "%s", payload.c_str());
    }

    void flush_() override {
        // No-op for Android - logcat is automatically flushed
    }

private:
    std::string tag_;
};

} // namespace sinks
} // namespace spdlog

#endif // ICRAW_ANDROID_LOG_SINK_HPP
