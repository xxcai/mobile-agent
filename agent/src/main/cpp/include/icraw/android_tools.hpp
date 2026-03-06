#ifndef ICRAW_ANDROID_TOOLS_HPP
#define ICRAW_ANDROID_TOOLS_HPP

#include <string>
#include <memory>
#include <nlohmann/json.hpp>

namespace icraw {

/**
 * Android Tool Callback interface
 * Used by C++ agent to call Android platform features
 */
class AndroidToolCallback {
public:
    virtual ~AndroidToolCallback() = default;

    /**
     * Call an Android tool with the given name and arguments
     * @param tool_name The name of the tool to call
     * @param args JSON object containing tool arguments
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    virtual std::string call_tool(const std::string& tool_name, const nlohmann::json& args) = 0;
};

/**
 * Android tools manager
 * Provides call_android_tool function that delegates to registered callback
 */
class AndroidTools {
public:
    AndroidTools() = default;

    /**
     * Register an Android tool callback
     * @param callback Unique pointer to the callback implementation
     */
    void register_callback(std::unique_ptr<AndroidToolCallback> callback);

    /**
     * Call an Android tool
     * @param tool_name The name of the tool to call
     * @param args JSON object containing tool arguments
     * @return JSON string with result
     */
    std::string call_tool(const std::string& tool_name, const nlohmann::json& args);

    /**
     * Check if a callback is registered
     * @return true if callback is registered
     */
    bool is_available() const { return callback_ != nullptr; }

private:
    std::unique_ptr<AndroidToolCallback> callback_;
};

// Global AndroidTools instance
extern AndroidTools g_android_tools;

} // namespace icraw

#endif // ICRAW_ANDROID_TOOLS_HPP
