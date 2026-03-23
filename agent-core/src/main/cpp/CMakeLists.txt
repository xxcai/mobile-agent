cmake_minimum_required(VERSION 3.22.1)
project(icraw)

# Main library sources
set(ICRAW_SOURCES
        src/config.cpp
        src/log/logger.cpp
        src/log/logger_backend_android.cpp
        src/log/logger_backend_spdlog.cpp
        src/core/content_block.cpp
        src/core/memory_manager.cpp
        src/core/skill_loader.cpp
        src/core/prompt_builder.cpp
        src/core/agent_loop.cpp
        src/core/llm_provider.cpp
        src/core/curl_http_client.cpp
        src/core/token_utils.cpp
        src/core/mcp_client.cpp
        src/tools/tool_registry.cpp
        src/mobile_agent.cpp
        src/platform/android/android_tools.cpp
        src/platform/android/native_agent.cpp
)

# Headers for IDE navigation
set(ICRAW_HEADERS
        include/icraw/log/logger.hpp
        include/icraw/log/log_utils.hpp
        include/icraw/core/content_block.hpp
        include/icraw/core/memory_manager.hpp
        include/icraw/core/skill_loader.hpp
        include/icraw/core/prompt_builder.hpp
        include/icraw/core/agent_loop.hpp
        include/icraw/core/llm_provider.hpp
        include/icraw/core/http_client.hpp
        include/icraw/core/token_utils.hpp
        include/icraw/core/mcp_client.hpp
        include/icraw/tools/tool_registry.hpp
        include/icraw/platform/android/android_tools.hpp
        include/icraw/config.hpp
        include/icraw/mobile_agent.hpp
        include/icraw/types.hpp
)

# Create shared library
add_library(icraw SHARED ${ICRAW_SOURCES} ${ICRAW_HEADERS})

target_include_directories(icraw PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

if(ANDROID)
    add_compile_definitions(ICRAW_ANDROID)
endif()

# Conan dependencies via find_package (from agent/build/armv8/Release/generators)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(SQLite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(OpenSSL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(fmt CONFIG REQUIRED)

# Link dependencies - use imported targets where available
target_link_libraries(icraw PUBLIC
        CURL::libcurl
        nlohmann_json::nlohmann_json
        spdlog::spdlog_header_only
        ZLIB::ZLIB
        SQLite::SQLite3
)

if(ANDROID)
    target_link_libraries(icraw PUBLIC log)
endif()
