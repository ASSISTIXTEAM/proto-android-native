cmake_minimum_required(VERSION 3.22.1)
project(proto_whisper C CXX)

if(ANDROID)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

set(WHISPER_CPP_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../vendor/whisper.cpp)
if(NOT EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "vendor/whisper.cpp missing")
endif()

add_subdirectory(${WHISPER_CPP_DIR} ${CMAKE_BINARY_DIR}/whisper-build)

find_library(log-lib log)

add_library(proto_whisper SHARED proto_whisper_jni.c)
target_include_directories(proto_whisper PRIVATE ${WHISPER_CPP_DIR}/include)
target_link_libraries(proto_whisper whisper ${log-lib})
target_compile_options(proto_whisper PRIVATE -O3)
