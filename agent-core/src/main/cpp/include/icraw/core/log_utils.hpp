#pragma once

#include <string>
#include <string_view>

namespace icraw::log_utils {

inline std::string truncate_for_debug(std::string_view value, size_t max_length = 120) {
    if (value.size() <= max_length) {
        return std::string(value);
    }
    return std::string(value.substr(0, max_length)) + "...";
}

} // namespace icraw::log_utils
