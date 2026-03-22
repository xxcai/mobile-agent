#include "icraw/core/logger.hpp"
#include "logger_backend.hpp"

namespace icraw {

Logger& Logger::get_instance() {
    static Logger instance;
    return instance;
}

Logger::~Logger() = default;

void Logger::init(const std::string& directory, const std::string& level) {
    if (initialized_) {
        return;
    }

    try {
        backend_ = create_default_logger_backend(directory);
        backend_->set_level(parse_log_level(level));
        initialized_ = true;
        debug("Logger initialized. Log directory: {}, level: {}", directory, level);
    } catch (const std::exception&) {
        initialized_ = false;
    }
}

void Logger::set_backend(std::unique_ptr<LoggerBackend> backend, LogLevel level) {
    if (!backend) {
        initialized_ = false;
        backend_.reset();
        return;
    }

    backend_ = std::move(backend);
    backend_->set_level(level);
    initialized_ = true;
}

bool Logger::is_initialized() const {
    return initialized_;
}

void Logger::log(LogLevel level, std::string_view message) {
    if (!initialized_ || !backend_) {
        return;
    }
    backend_->log(level, message);
}

LogLevel parse_log_level(const std::string& level) {
    if (level == "trace") {
        return LogLevel::Trace;
    }
    if (level == "debug") {
        return LogLevel::Debug;
    }
    if (level == "warn") {
        return LogLevel::Warn;
    }
    if (level == "error") {
        return LogLevel::Error;
    }
    return LogLevel::Info;
}

} // namespace icraw
