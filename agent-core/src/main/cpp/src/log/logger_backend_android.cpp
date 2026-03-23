#include "logger_backend.hpp"

#ifdef ICRAW_ANDROID

#include <android/log.h>

namespace icraw {
namespace {

constexpr const char* kDefaultAndroidTag = "icraw";

android_LogPriority to_android_priority(LogLevel level) {
    switch (level) {
        case LogLevel::Trace:
        case LogLevel::Debug:
            return ANDROID_LOG_DEBUG;
        case LogLevel::Info:
            return ANDROID_LOG_INFO;
        case LogLevel::Warn:
            return ANDROID_LOG_WARN;
        case LogLevel::Error:
            return ANDROID_LOG_ERROR;
    }
    return ANDROID_LOG_DEBUG;
}

class AndroidLoggerBackend final : public LoggerBackend {
public:
    void set_level(LogLevel level) override {
        min_level_ = level;
    }

    void log(LogLevel level, std::string_view message) override {
        if (level < min_level_) {
            return;
        }
        __android_log_print(
                to_android_priority(level),
                kDefaultAndroidTag,
                "%.*s",
                static_cast<int>(message.size()),
                message.data());
    }

private:
    LogLevel min_level_ = LogLevel::Info;
};

} // namespace

std::unique_ptr<LoggerBackend> create_default_logger_backend(const std::string& directory) {
    (void) directory;
    return std::make_unique<AndroidLoggerBackend>();
}

} // namespace icraw

#endif
