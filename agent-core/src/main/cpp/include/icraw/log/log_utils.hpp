#pragma once

#include <string>
#include <string_view>

namespace icraw::log_utils {

inline std::string truncate_for_debug(std::string_view value, size_t max_length = 120) {
    if (value.size() <= max_length) {
        return std::string(value);
    }

    // Debug previews may flow through JNI NewStringUTF. If we cut a UTF-8
    // multibyte character in half, JNI will abort on invalid Modified UTF-8.
    // Trim the preview back to the nearest complete UTF-8 code point first.
    size_t safe_end = max_length;
    while (safe_end > 0 && (static_cast<unsigned char>(value[safe_end]) & 0xC0) == 0x80) {
        --safe_end;
    }

    if (safe_end == 0) {
        return "...";
    }

    const unsigned char lead = static_cast<unsigned char>(value[safe_end]);
    size_t code_point_length = 1;
    if ((lead & 0x80) == 0x00) {
        code_point_length = 1;
    } else if ((lead & 0xE0) == 0xC0) {
        code_point_length = 2;
    } else if ((lead & 0xF0) == 0xE0) {
        code_point_length = 3;
    } else if ((lead & 0xF8) == 0xF0) {
        code_point_length = 4;
    } else {
        return std::string(value.substr(0, safe_end)) + "...";
    }

    if (safe_end + code_point_length > max_length) {
        return std::string(value.substr(0, safe_end)) + "...";
    }

    return std::string(value.substr(0, max_length)) + "...";
}

} // namespace icraw::log_utils
