cmake_minimum_required(VERSION 3.16)

# vcpkg dependency
find_package(Catch2 CONFIG REQUIRED)

# Test executable
add_executable(icraw_tests
    content_block.test.cpp
    memory_manager.test.cpp
    tool_registry.test.cpp
    skill_loader.test.cpp
    skill_loader_enhancements.test.cpp
    memory_optimization.test.cpp
)

target_link_libraries(icraw_tests
    PRIVATE
        icraw
        Catch2::Catch2WithMain
)

target_include_directories(icraw_tests PRIVATE ${CMAKE_SOURCE_DIR}/include)

# Register tests with CTest
include(Catch)
catch_discover_tests(icraw_tests)
