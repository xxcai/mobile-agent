#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include <fmt/format.h>

namespace icraw {

enum class LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error
};

class LoggerBackend;

class Logger {
public:
    static Logger& get_instance();

    void init(const std::string& directory, const std::string& level = "info");
    bool is_initialized() const;

    void log(LogLevel level, std::string_view message);

    template <typename... Args>
    void trace(fmt::format_string<Args...> format, Args&&... args) {
        log_formatted(LogLevel::Trace, format, std::forward<Args>(args)...);
    }

    template <typename... Args>
    void debug(fmt::format_string<Args...> format, Args&&... args) {
        log_formatted(LogLevel::Debug, format, std::forward<Args>(args)...);
    }

    template <typename... Args>
    void info(fmt::format_string<Args...> format, Args&&... args) {
        log_formatted(LogLevel::Info, format, std::forward<Args>(args)...);
    }

    template <typename... Args>
    void warn(fmt::format_string<Args...> format, Args&&... args) {
        log_formatted(LogLevel::Warn, format, std::forward<Args>(args)...);
    }

    template <typename... Args>
    void error(fmt::format_string<Args...> format, Args&&... args) {
        log_formatted(LogLevel::Error, format, std::forward<Args>(args)...);
    }

private:
    Logger() = default;
    ~Logger();

    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    template <typename... Args>
    void log_formatted(LogLevel level, fmt::format_string<Args...> format, Args&&... args) {
        if (!initialized_) {
            return;
        }
        log(level, fmt::format(format, std::forward<Args>(args)...));
    }

    std::unique_ptr<LoggerBackend> backend_;
    bool initialized_ = false;
};

#define ICRAW_LOG_TRACE(...) do { icraw::Logger::get_instance().trace(__VA_ARGS__); } while (0)
#define ICRAW_LOG_DEBUG(...) do { icraw::Logger::get_instance().debug(__VA_ARGS__); } while (0)
#define ICRAW_LOG_INFO(...)  do { icraw::Logger::get_instance().info(__VA_ARGS__); } while (0)
#define ICRAW_LOG_WARN(...)  do { icraw::Logger::get_instance().warn(__VA_ARGS__); } while (0)
#define ICRAW_LOG_ERROR(...) do { icraw::Logger::get_instance().error(__VA_ARGS__); } while (0)

} // namespace icraw
