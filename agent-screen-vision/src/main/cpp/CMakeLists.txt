cmake_minimum_required(VERSION 3.22.1)

project(screenvision_mnn)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(MNN_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/mnn_include")
set(MNN_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

add_library(mnn_prebuilt SHARED IMPORTED)
set_target_properties(
    mnn_prebuilt
    PROPERTIES IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN.so"
)

add_library(mnn_express_prebuilt SHARED IMPORTED)
set_target_properties(
    mnn_express_prebuilt
    PROPERTIES IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN_Express.so"
)

add_library(
    screenvision_mnn
    SHARED
    mnn_ui_classifier_jni.cpp
)

target_include_directories(
    screenvision_mnn
    PRIVATE
    "${MNN_INCLUDE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    screenvision_mnn
    PRIVATE
    mnn_express_prebuilt
    mnn_prebuilt
    ${log-lib}
    ${android-lib}
)