set(ICRAW_LLM_PROVIDER_TEST_SOURCES
        llm_provider_tests.cpp
        ../../main/cpp/src/core/llm_provider.cpp
        ../../main/cpp/src/core/openai_compatible_provider_base.cpp
        ../../main/cpp/src/core/llm_provider_factory.cpp
        ../../main/cpp/src/core/curl_http_client.cpp
        ../../main/cpp/src/core/providers/generic_openai_provider.cpp
        ../../main/cpp/src/core/providers/minimax_provider.cpp
        ../../main/cpp/src/core/providers/qwen_provider.cpp
        ../../main/cpp/src/core/providers/glm_provider.cpp
        ../../main/cpp/src/log/logger.cpp
        ../../main/cpp/src/log/logger_backend_spdlog.cpp
)

add_executable(icraw_llm_provider_tests ${ICRAW_LLM_PROVIDER_TEST_SOURCES})

target_include_directories(icraw_llm_provider_tests PRIVATE
        ../../main/cpp/include
        ../../main/cpp/src/log
)

target_link_libraries(icraw_llm_provider_tests PRIVATE
        CURL::libcurl
        nlohmann_json::nlohmann_json
        spdlog::spdlog_header_only
        ZLIB::ZLIB
)

add_test(NAME icraw_llm_provider_tests COMMAND icraw_llm_provider_tests)

set(ICRAW_MEMORY_SYSTEM_TEST_SOURCES
        memory_system_tests.cpp
        ../../main/cpp/src/config.cpp
        ../../main/cpp/src/log/logger.cpp
        ../../main/cpp/src/log/logger_backend_spdlog.cpp
        ../../main/cpp/src/core/content_block.cpp
        ../../main/cpp/src/core/memory_manager.cpp
        ../../main/cpp/src/core/skill_loader.cpp
        ../../main/cpp/src/core/agent_loop.cpp
        ../../main/cpp/src/core/llm_provider.cpp
        ../../main/cpp/src/core/token_utils.cpp
        ../../main/cpp/src/tools/tool_registry.cpp
        ../../main/cpp/src/platform/android/android_tools.cpp
)

add_executable(icraw_memory_system_tests ${ICRAW_MEMORY_SYSTEM_TEST_SOURCES})

target_include_directories(icraw_memory_system_tests PRIVATE
        ../../main/cpp/include
        ../../main/cpp/src/log
)

target_link_libraries(icraw_memory_system_tests PRIVATE
        CURL::libcurl
        nlohmann_json::nlohmann_json
        spdlog::spdlog_header_only
        ZLIB::ZLIB
        SQLite::SQLite3
)

add_test(NAME icraw_memory_system_tests COMMAND icraw_memory_system_tests)
