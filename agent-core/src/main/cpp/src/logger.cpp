#include "icraw/core/logger.hpp"
#include "icraw/tools/android_log_sink.hpp"
#include <filesystem>
#include <iostream>

#ifdef ICRAW_ANDROID
#include <android/log.h>
#endif

namespace icraw {

Logger& Logger::get_instance() {
    static Logger instance;
    return instance;
}

void Logger::init(const std::string& directory, const std::string& level) {
    if (initialized_) {
        return;
    }

    try {
#ifdef ICRAW_ANDROID
        // On Android, use Android log sink
        auto android_sink = std::make_shared<spdlog::sinks::android_log_sink>("icraw");
        logger_ = std::make_shared<spdlog::logger>("icraw", android_sink);

        // Use spdlog namespace directly for android_log_sink
        __android_log_print(ANDROID_LOG_INFO, "icraw", "Using Android log sink");
#else
        // Create log directory if it doesn't exist (desktop only)
        std::filesystem::path log_dir(directory);
        if (!std::filesystem::exists(log_dir)) {
            std::filesystem::create_directories(log_dir);
        }

        // Create rotating file sink (max 5MB, keep 3 files)
        std::string log_file_path = (log_dir / "icraw.log").string();
        std::cout << "Creating log file at: " << log_file_path << std::endl;
        auto file_sink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(
            log_file_path,
            1024 * 1024 * 5,  // 5MB
            3                 // 3 files
        );

        // Create logger
        logger_ = std::make_shared<spdlog::logger>("icraw", file_sink);

        // Enable auto flush for immediate file writing
        logger_->flush_on(spdlog::level::debug);
#endif

        // Set level
        spdlog::level::level_enum log_level = spdlog::level::info;
        if (level == "trace") {
            log_level = spdlog::level::trace;
        } else if (level == "debug") {
            log_level = spdlog::level::debug;
        } else if (level == "info") {
            log_level = spdlog::level::info;
        } else if (level == "warn") {
            log_level = spdlog::level::warn;
        } else if (level == "error") {
            log_level = spdlog::level::err;
        }

        std::cout << "Setting log level to: " << level << " (enum: " << static_cast<int>(log_level) << ")" << std::endl;
        logger_->set_level(log_level);

        // Set pattern: [timestamp] [level] [logger] message
        logger_->set_pattern("[%Y-%m-%d %H:%M:%S.%e] [%^%l%$] [%n] %v");

        // Set as default logger so macros work
        spdlog::set_default_logger(logger_);

        initialized_ = true;

        // Use direct logger call instead of macro to avoid potential issues
        logger_->debug("Logger initialized. Log directory: {}, level: {}", directory, level);
    } catch (const spdlog::spdlog_ex& ex) {
        std::cerr << "Logger initialization failed: " << ex.what() << std::endl;
    }
}

bool Logger::is_initialized() const {
    return initialized_;
}

std::shared_ptr<spdlog::logger> Logger::logger() {
    return logger_;
}

} // namespace icraw
