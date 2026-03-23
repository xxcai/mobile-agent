#include "logger_backend.hpp"

#ifndef ICRAW_ANDROID

#include <filesystem>
#include <spdlog/logger.h>
#include <spdlog/sinks/rotating_file_sink.h>

namespace icraw {
namespace {

spdlog::level::level_enum to_spdlog_level(LogLevel level) {
    switch (level) {
        case LogLevel::Trace:
            return spdlog::level::trace;
        case LogLevel::Debug:
            return spdlog::level::debug;
        case LogLevel::Info:
            return spdlog::level::info;
        case LogLevel::Warn:
            return spdlog::level::warn;
        case LogLevel::Error:
            return spdlog::level::err;
    }
    return spdlog::level::info;
}

class SpdlogLoggerBackend final : public LoggerBackend {
public:
    explicit SpdlogLoggerBackend(const std::string& directory) {
        std::filesystem::path log_dir(directory);
        if (!std::filesystem::exists(log_dir)) {
            std::filesystem::create_directories(log_dir);
        }

        std::string log_file_path = (log_dir / "icraw.log").string();
        auto file_sink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(
                log_file_path,
                1024 * 1024 * 5,
                3);
        logger_ = std::make_shared<spdlog::logger>("icraw", file_sink);
        logger_->set_pattern("[%Y-%m-%d %H:%M:%S.%e] [%^%l%$] [%n] %v");
        logger_->flush_on(spdlog::level::debug);
    }

    void set_level(LogLevel level) override {
        logger_->set_level(to_spdlog_level(level));
    }

    void log(LogLevel level, std::string_view message) override {
        logger_->log(to_spdlog_level(level), std::string(message));
    }

private:
    std::shared_ptr<spdlog::logger> logger_;
};

} // namespace

std::unique_ptr<LoggerBackend> create_default_logger_backend(const std::string& directory) {
    return std::make_unique<SpdlogLoggerBackend>(directory);
}

} // namespace icraw

#endif
