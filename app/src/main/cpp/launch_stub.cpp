// Bundles libc++_shared.so for WebRTC (matches stable 3.8.x APK layout).
#include <jni.h>

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}
