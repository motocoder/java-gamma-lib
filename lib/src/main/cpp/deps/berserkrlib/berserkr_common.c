//
// Created by Sean Wagner on 1/8/26.
//

#include "include/berserkr.h"


static jobject loggerFactory;

struct android_app *_app;

void android_main(struct android_app *app)
{
    _app = app;
}

// NOTE: Add this to header (if apps really need it)
struct android_app *GetAndroidApp(void)
{
    return _app;
}

jobject GetNativeLoaderInstance(void)
{
    return GetAndroidApp()->activity->clazz;
}

JNIEnv* AttachCurrentThread(void)
{
    JavaVM *vm = GetAndroidApp()->activity->vm;
    JNIEnv *env = NULL;

    (*vm)->AttachCurrentThread(vm, &env, NULL);
    return env;
}

void logAndroidDebug(const char * logger, const char * message) {

    jobject nativeInstance = GetNativeLoaderInstance();

    if (nativeInstance != NULL && loggerFactory != NULL) {

        JNIEnv *env = AttachCurrentThread();

        jmethodID getLoggerMethod = (*env)->GetStaticMethodID(env, loggerFactory, "getLogger", "(Ljava/lang/String;)Lorg/slf4j/Logger;");

        jobject loggerObj = (*env)->CallStaticObjectMethod(env, loggerFactory, getLoggerMethod, (*env)->NewStringUTF(env, logger));

        jclass loggerClass = (*env)->GetObjectClass(env, loggerObj);

        // 2. Get the method ID for the "getAge" method, using its JNI signature "()I" (no args, returns int)
        jmethodID info = (*env)->GetMethodID(env, loggerClass, "info", "(Ljava/lang/String;)V");

        jobject newJavaString = (*env)->NewStringUTF(env, message);

        // 3. Call the Java method on the object and get the result
        (*env)->CallVoidMethod(env, loggerObj, info, newJavaString);

        (*env)->DeleteLocalRef(env, newJavaString);
    }

}

void loggerInit(JNIEnv *env) {

    jclass loggerFactoryClass = (*env)->FindClass(env, "org/slf4j/LoggerFactory");

    loggerFactory = (*env)->NewGlobalRef(env, loggerFactoryClass);

}


