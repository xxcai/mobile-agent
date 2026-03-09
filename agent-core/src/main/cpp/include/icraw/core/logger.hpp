#pragma once

#include <string>
#include <memory>
#include <spdlog/spdlog.h>
#include <spdlog/sinks/rotating_file_sink.h>

namespace icraw {

class Logger {
public:
    static Logger& get_instance();
    
    // Initialize logger with directory
    void init(const std::string& directory, const std::string& level = "info");
    
    // Check if logger is initialized
    bool is_initialized() const;
    
    // Get logger
    std::shared_ptr<spdlog::logger> logger();
    
private:
    Logger() = default;
    ~Logger() = default;
    
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;
    
    std::shared_ptr<spdlog::logger> logger_;
    bool initialized_ = false;
};

// Convenience macros - use logger directly to support runtime level control
#define ICRAW_LOG_TRACE(...) if (icraw::Logger::get_instance().is_initialized()) { icraw::Logger::get_instance().logger()->trace(__VA_ARGS__); }
#define ICRAW_LOG_DEBUG(...) if (icraw::Logger::get_instance().is_initialized()) { icraw::Logger::get_instance().logger()->debug(__VA_ARGS__); }
#define ICRAW_LOG_INFO(...)  if (icraw::Logger::get_instance().is_initialized()) { icraw::Logger::get_instance().logger()->info(__VA_ARGS__); }
#define ICRAW_LOG_WARN(...)  if (icraw::Logger::get_instance().is_initialized()) { icraw::Logger::get_instance().logger()->warn(__VA_ARGS__); }
#define ICRAW_LOG_ERROR(...) if (icraw::Logger::get_instance().is_initialized()) { icraw::Logger::get_instance().logger()->error(__VA_ARGS__); }

} // namespace icraw
