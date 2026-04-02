#ifndef ICRAW_ANDROID_TOOLS_HPP
#define ICRAW_ANDROID_TOOLS_HPP

#include <string>
#include <memory>
#include <nlohmann/json.hpp>

namespace icraw {

/**
 * Android Tool Callback interface
 * Used by C++ agent to call Java-side Android tool channels
 */
class AndroidToolCallback {
public:
    virtual ~AndroidToolCallback() = default;

    /**
     * Call an Android tool channel with the given name and raw parameters
     * @param tool_name The outer tool channel name
     * @param args JSON object containing the original tool parameters
     * @param session_key Current agent session id
     * @return JSON string with result: {"success": true, "result": ...} or {"success": false, "error": "..."}
     */
    virtual std::string call_tool(const std::string& tool_name,
                                  const nlohmann::json& args,
                                  const std::string& session_key) = 0;
};

/**
 * Android tools manager
 * Delegates dynamic Android tool-channel calls to the registered callback
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
     * Call an Android tool channel
     * @param tool_name The outer tool channel name
     * @param args JSON object containing the original tool parameters
     * @return JSON string with result
     */
    std::string call_tool(const std::string& tool_name, const nlohmann::json& args);
    void set_current_session_id(const std::string& session_id);
    void clear_current_session_id();

    /**
     * Check if a callback is registered
     * @return true if callback is registered
     */
    bool is_available() const { return callback_ != nullptr; }

private:
    std::unique_ptr<AndroidToolCallback> callback_;
    std::string current_session_id_;
};

// Global AndroidTools instance
extern AndroidTools g_android_tools;

} // namespace icraw

#endif // ICRAW_ANDROID_TOOLS_HPP
