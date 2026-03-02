cmake_minimum_required(VERSION 3.16)
project(icraw VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Options
option(BUILD_TESTS "Build tests" ON)
option(ICRAW_BUILD_SHARED "Build shared library" OFF)

# Compiler warnings
if(MSVC)
    add_compile_options(/W4)
else()
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# vcpkg dependencies via find_package
find_package(nlohmann_json CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(unofficial-sqlite3 CONFIG REQUIRED)
find_package(CURL CONFIG REQUIRED)
find_package(spdlog CONFIG REQUIRED)

# Main library
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
)

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

if(ICRAW_BUILD_SHARED)
    add_library(icraw SHARED ${ICRAW_SOURCES})
else()
    add_library(icraw STATIC ${ICRAW_SOURCES})
endif()

target_include_directories(icraw
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(icraw PUBLIC 
    nlohmann_json::nlohmann_json 
    unofficial::sqlite3::sqlite3
    CURL::libcurl
    spdlog::spdlog
)

# Platform-specific settings
if(WIN32)
    target_compile_definitions(icraw PRIVATE _WIN32_WINNT=0x0601)
elseif(ANDROID)
    target_compile_definitions(icraw PRIVATE ICRAW_ANDROID)
elseif(IOS)
    target_compile_definitions(icraw PRIVATE ICRAW_IOS)
endif()

# Demo
option(BUILD_DEMO "Build demo application" ON)
if(BUILD_DEMO)
    add_subdirectory(demo)
endif()

# Tests
if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

# Install
install(TARGETS icraw
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)
install(DIRECTORY include/icraw DESTINATION include)
