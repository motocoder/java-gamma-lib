//
// Created by Sean Wagner on 1/8/26.
//

#ifndef TETROBE_BERSERKR_H
#define TETROBE_BERSERKR_H

#endif //TETROBE_BERSERKR_H

#define LOG_TAG "autoharnesstag"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
// Add macros for other priorities like DEBUG, WARN, VERBOSE as needed

#include <android/log.h>
#include "android_native_app_glue.h"
#include "jni.h"

#define MAX(a, b) (((a) > (b)) ? (a) : (b))
#define MIN(a, b) (((a) < (b)) ? (a) : (b))

void loggerInit(JNIEnv *env);
void logAndroidDebug(const char * logger, const char * message);