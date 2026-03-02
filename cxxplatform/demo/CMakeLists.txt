cmake_minimum_required(VERSION 3.16)

add_executable(icraw_demo
    main.cpp
)

target_include_directories(icraw_demo PRIVATE 
    ${CMAKE_CURRENT_SOURCE_DIR}/../include
    ${CMAKE_CURRENT_BINARY_DIR}/include
)

target_link_libraries(icraw_demo PRIVATE icraw)

# Windows specific settings
if(WIN32)
    target_compile_definitions(icraw_demo PRIVATE
        _WIN32_WINNT=0x0601
        _CRT_SECURE_NO_WARNINGS
        _CRT_NONSTDC_WARNINGS
    )
    target_compile_options(icraw_demo PRIVATE /W4)
endif()
