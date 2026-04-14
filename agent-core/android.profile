[settings]
os=Android
os.api_level=26
arch=armv8
compiler=clang
compiler.version=17
compiler.libcxx=c++_shared
compiler.cppstd=17
build_type=Release

[conf]
# Repository profile intentionally does not hardcode tools.android:ndk_path.
# Provide it via local Conan command (-c tools.android:ndk_path=...)
# or a machine-local profile include.
