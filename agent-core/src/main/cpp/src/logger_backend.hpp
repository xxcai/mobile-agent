#pragma once

#include <memory>
#include <string>
#include <string_view>

#include "icraw/core/logger.hpp"

namespace icraw {

class LoggerBackend {
public:
    virtual ~LoggerBackend() = default;

    virtual void set_level(LogLevel level) = 0;
    virtual void log(LogLevel level, std::string_view message) = 0;
};

std::unique_ptr<LoggerBackend> create_default_logger_backend(const std::string& directory);
LogLevel parse_log_level(const std::string& level);

} // namespace icraw
