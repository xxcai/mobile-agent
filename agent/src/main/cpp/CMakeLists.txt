cmake_minimum_required(VERSION 3.22.1)
project(icraw VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Android-specific settings
if(ANDROID)
    # Conan dependencies location - relative path from agent/src/main/cpp to agent/build/armv8/Release/generators
    set(CONAN_DEPENDENCIES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/armv8/Release/generators")

    # Add Conan generated files to CMake module path and prefix path
    list(APPEND CMAKE_MODULE_PATH "${CONAN_DEPENDENCIES_DIR}")
    list(APPEND CMAKE_PREFIX_PATH "${CONAN_DEPENDENCIES_DIR}")

    # Set package config directories (point to directory containing .cmake files)
    set(nlohmann_json_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(spdlog_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(SQLite3_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(CURL_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(ZLIB_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(OpenSSL_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(OPENSSL_DIR "${CONAN_DEPENDENCIES_DIR}")
    set(fmt_DIR "${CONAN_DEPENDENCIES_DIR}")

    message(STATUS "Conan dependencies dir: ${CONAN_DEPENDENCIES_DIR}")
endif()

# Compiler warnings
if(MSVC)
    add_compile_options(/W4)
else()
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# Conan dependencies via find_package (from agent/build/armv8/Release/generators)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(SQLite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(fmt CONFIG REQUIRED)

# Main library sources
set(ICRAW_SOURCES
    src/config.cpp
    src/logger.cpp
    src/core/content_block.cpp
    src/core/memory_manager.cpp
    src/core/skill_loader.cpp
    src/core/prompt_builder.cpp
    src/core/agent_loop.cpp
    src/core/llm_provider.cpp
    src/core/curl_http_client.cpp
    src/core/token_utils.cpp
    src/tools/tool_registry.cpp
    src/mobile_agent.cpp
    native_agent.cpp
)

# Headers for IDE navigation
set(ICRAW_HEADERS
    include/icraw/core/logger.hpp
    include/icraw/core/content_block.hpp
    include/icraw/core/memory_manager.hpp
    include/icraw/core/skill_loader.hpp
    include/icraw/core/prompt_builder.hpp
    include/icraw/core/agent_loop.hpp
    include/icraw/core/llm_provider.hpp
    include/icraw/core/http_client.hpp
    include/icraw/core/token_utils.hpp
    include/icraw/tools/tool_registry.hpp
    include/icraw/config.hpp
    include/icraw/mobile_agent.hpp
    include/icraw/types.hpp
)

# Create shared library
add_library(icraw SHARED ${ICRAW_SOURCES} ${ICRAW_HEADERS})

target_include_directories(icraw
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

# Android-specific includes
if(ANDROID)
    # Add current source dir for our own headers
    target_include_directories(icraw PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

    # Add include paths from Conan generated data files
    # These must be included AFTER find_package but BEFORE linking
    include(${CONAN_DEPENDENCIES_DIR}/nlohmann_json-release-armv8-data.cmake)
    include(${CONAN_DEPENDENCIES_DIR}/spdlog-release-armv8-data.cmake)
    include(${CONAN_DEPENDENCIES_DIR}/fmt-release-armv8-data.cmake)
    include(${CONAN_DEPENDENCIES_DIR}/SQLite3-release-armv8-data.cmake)
    include(${CONAN_DEPENDENCIES_DIR}/CURL-release-armv8-data.cmake)

    target_include_directories(icraw PUBLIC
        ${nlohmann_json_INCLUDE_DIRS_RELEASE}
        ${spdlog_INCLUDE_DIRS_RELEASE}
        ${fmt_INCLUDE_DIRS_RELEASE}
        ${sqlite3_INCLUDE_DIRS_RELEASE}
        ${libcurl_INCLUDE_DIRS_RELEASE}
    )

    # Set package folder paths for libraries
    set(libcurl_PACKAGE_FOLDER_RELEASE "/Users/caixiao/.conan2/p/b/libcuee6a833948e64/p")
    set(sqlite3_PACKAGE_FOLDER_RELEASE "/Users/caixiao/.conan2/p/b/sqlitf38c5a96b1cb9/p")
    set(fmt_PACKAGE_FOLDER_RELEASE "/Users/caixiao/.conan2/p/b/fmtf605a6099d31d/p")

    # Add library directories for linking
    link_directories(
        ${libcurl_PACKAGE_FOLDER_RELEASE}/lib
        ${sqlite3_PACKAGE_FOLDER_RELEASE}/lib
        ${fmt_PACKAGE_FOLDER_RELEASE}/lib
    )

    # Add compile definitions from spdlog (for external fmt)
    target_compile_definitions(icraw PUBLIC ${spdlog_COMPILE_DEFINITIONS_RELEASE})
endif()

# Link dependencies (using SQLite3 from Conan, not unofficial-sqlite3)
# Use full paths for libraries
target_link_libraries(icraw PUBLIC
    nlohmann_json::nlohmann_json
    ${libcurl_PACKAGE_FOLDER_RELEASE}/lib/libcurl.so
    ${sqlite3_PACKAGE_FOLDER_RELEASE}/lib/libsqlite3.so
    spdlog::spdlog_header_only
    ${fmt_PACKAGE_FOLDER_RELEASE}/lib/libfmt.a
    ZLIB::ZLIB
)

# Platform-specific settings
if(ANDROID)
    target_compile_definitions(icraw PUBLIC ICRAW_ANDROID)
    target_link_libraries(icraw PUBLIC log)
endif()

# Install (optional for Android)
install(TARGETS icraw
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
