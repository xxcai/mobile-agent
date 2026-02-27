#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_hh_agent_lib_NativeLib_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_hh_agent_lib_NativeLib_add(JNIEnv* env, jobject /* this */, jint a, jint b) {
    return a + b;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hh_agent_lib_NativeLib_getMessage(JNIEnv* env, jobject /* this */) {
    std::string message = "C++ Native Library Test Success!";
    return env->NewStringUTF(message.c_str());
}
