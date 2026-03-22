#include "icraw/android_tools.hpp"
#include "icraw/core/logger.hpp"

namespace icraw {

void AndroidTools::register_callback(std::unique_ptr<AndroidToolCallback> callback) {
    callback_ = std::move(callback);
    ICRAW_LOG_INFO("AndroidTools callback registered");
}

std::string AndroidTools::call_tool(const std::string& tool_name, const nlohmann::json& args) {
    if (!callback_) {
        ICRAW_LOG_WARN("AndroidTools callback not registered");
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "android_tools_not_available";
        return result.dump();
    }

    try {
        ICRAW_LOG_DEBUG("Calling Android tool channel: {} with params: {}", tool_name, args.dump());
        std::string result = callback_->call_tool(tool_name, args);
        ICRAW_LOG_DEBUG("Android tool channel result: {}", result);
        return result;
    } catch (const std::exception& e) {
        ICRAW_LOG_ERROR("Android tool channel {} failed: {}", tool_name, e.what());
        nlohmann::json result;
        result["success"] = false;
        result["error"] = "execution_failed";
        result["message"] = e.what();
        return result.dump();
    }
}

// Global instance
AndroidTools g_android_tools;

} // namespace icraw
