#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define TAG "ProtoWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_org_assistix_proto_nativeapp_data_WhisperNative_nativeInitContext(JNIEnv *env, jclass clazz, jstring model_path) {
    (void) clazz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    if (path == NULL) {
        return 0;
    }
    LOGI("Loading whisper model: %s", path);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    if (ctx == NULL) {
        LOGE("whisper_init_from_file failed");
        return 0;
    }
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_org_assistix_proto_nativeapp_data_WhisperNative_nativeFreeContext(JNIEnv *env, jclass clazz, jlong ctx_ptr) {
    (void) env;
    (void) clazz;
    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (ctx != NULL) {
        whisper_free(ctx);
    }
}

JNIEXPORT jint JNICALL
Java_org_assistix_proto_nativeapp_data_WhisperNative_nativeFullTranscribe(
    JNIEnv *env, jclass clazz, jlong ctx_ptr, jint num_threads, jfloatArray audio_data, jstring language) {
    (void) clazz;
    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (ctx == NULL) {
        return -1;
    }
    jsize len = (*env)->GetArrayLength(env, audio_data);
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    if (samples == NULL) {
        return -2;
    }

    const char *lang = NULL;
    if (language != NULL) {
        lang = (*env)->GetStringUTFChars(env, language, NULL);
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = NULL;
    params.detect_language = true;
    if (lang != NULL && lang[0] != '\0' && strcmp(lang, "auto") != 0) {
        params.language = lang;
        params.detect_language = false;
    }
    params.n_threads = num_threads > 0 ? num_threads : 2;
    if (params.n_threads > 4) {
        params.n_threads = 4;
    }
    params.no_context = true;
    params.single_segment = true;
    params.temperature = 0.0f;
    params.temperature_inc = 0.2f;
    params.suppress_blank = true;

    int rc = whisper_full(ctx, params, samples, len);
    (*env)->ReleaseFloatArrayElements(env, audio_data, samples, JNI_ABORT);
    if (lang != NULL) {
        (*env)->ReleaseStringUTFChars(env, language, lang);
    }
    return rc;
}

JNIEXPORT jstring JNICALL
Java_org_assistix_proto_nativeapp_data_WhisperNative_nativeGetFullText(JNIEnv *env, jclass clazz, jlong ctx_ptr) {
    (void) clazz;
    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (ctx == NULL) {
        return (*env)->NewStringUTF(env, "");
    }
    const int n = whisper_full_n_segments(ctx);
    if (n <= 0) {
        return (*env)->NewStringUTF(env, "");
    }
    size_t cap = 256;
    size_t used = 0;
    char *buf = (char *) malloc(cap);
    if (buf == NULL) {
        return (*env)->NewStringUTF(env, "");
    }
    buf[0] = '\0';
    for (int i = 0; i < n; i++) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg == NULL) {
            continue;
        }
        size_t need = used + strlen(seg) + 2;
        if (need >= cap) {
            cap = need + 128;
            char *next = (char *) realloc(buf, cap);
            if (next == NULL) {
                break;
            }
            buf = next;
        }
        if (used > 0) {
            strcat(buf, " ");
            used++;
        }
        strcat(buf, seg);
        used = strlen(buf);
    }
    jstring out = (*env)->NewStringUTF(env, buf);
    free(buf);
    return out;
}
