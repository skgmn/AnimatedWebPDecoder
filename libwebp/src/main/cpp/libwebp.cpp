#include <jni.h>
#include <sstream>
#include <android/bitmap.h>
#include <android/log.h>

#include "src/webp/demux.h"

using namespace std;

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_createDecoder(
        JNIEnv *env,
        jclass clazz,
        jobject byte_buffer,
        jboolean premultiplied_alpha) {

    WebPData data;
    data.bytes = static_cast<const uint8_t *>(env->GetDirectBufferAddress(byte_buffer));
    data.size = env->GetDirectBufferCapacity(byte_buffer);

    WebPAnimDecoderOptions dec_options;
    WebPAnimDecoderOptionsInit(&dec_options);
    dec_options.color_mode = premultiplied_alpha ? MODE_rgbA : MODE_RGBA;
    dec_options.use_threads = true;

    auto *decoder = WebPAnimDecoderNew(&data, &dec_options);
    return reinterpret_cast<jlong>(decoder);
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_deleteDecoder(
        JNIEnv *env,
        jclass clazz,
        jlong decoder_ptr) {

    WebPAnimDecoderDelete(reinterpret_cast<WebPAnimDecoder *>(decoder_ptr));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_getMetadata(
        JNIEnv *env,
        jclass clazz,
        jlong decoder_ptr) {

    auto *decoder = reinterpret_cast<WebPAnimDecoder *>(decoder_ptr);

    WebPAnimInfo anim_info;
    WebPAnimDecoderGetInfo(decoder, &anim_info);

    const WebPDemuxer *demuxer = WebPAnimDecoderGetDemuxer(decoder);

    jclass metadataClass = env->FindClass(
            "com/github/skgmn/webpdecoder/libwebp/LibWebPAnimatedDecoder$Metadata");
    jmethodID metadataCtor = env->GetMethodID(metadataClass, "<init>", "(IIIII)V");
    jobject metadata = env->NewObject(
            metadataClass, metadataCtor,
            (jint) anim_info.canvas_width,
            (jint) anim_info.canvas_height,
            (jint) anim_info.loop_count,
            (jint) anim_info.bgcolor,
            (jint) anim_info.frame_count
    );
    return metadata;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_decodeNextFrame(
        JNIEnv *env,
        jclass clazz,
        jlong decoder_ptr,
        jobject out_bitmap) {

    auto *decoder = reinterpret_cast<WebPAnimDecoder *>(decoder_ptr);

    AndroidBitmapInfo bitmap_info;
    AndroidBitmap_getInfo(env, out_bitmap, &bitmap_info);

    uint8_t *bitmap_ptr;
    AndroidBitmap_lockPixels(env, out_bitmap, reinterpret_cast<void **>(&bitmap_ptr));

    uint8_t *buf;
    int duration = -1;
    WebPAnimDecoderGetNext(decoder, &buf, &duration);

    size_t widthBytes = 4 * bitmap_info.width;
    if (bitmap_info.width * 4 == bitmap_info.stride) {
        memcpy(bitmap_ptr, buf, widthBytes * bitmap_info.height);
    } else {
        for (int i = 0; i < bitmap_info.height; ++i) {
            memcpy(bitmap_ptr, buf, widthBytes);
            bitmap_ptr += bitmap_info.stride;
            buf += widthBytes;
        }
    }

    AndroidBitmap_unlockPixels(env, out_bitmap);

    return duration;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_reset(
        JNIEnv *env,
        jclass clazz,
        jlong decoder_ptr) {

    auto *decoder = reinterpret_cast<WebPAnimDecoder *>(decoder_ptr);
    WebPAnimDecoderReset(decoder);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_skgmn_webpdecoder_libwebp_LibWebPAnimatedDecoder_hasNextFrame(
        JNIEnv *env,
        jclass clazz,
        jlong decoder_ptr) {

    auto *decoder = reinterpret_cast<WebPAnimDecoder *>(decoder_ptr);
    return WebPAnimDecoderHasMoreFrames(decoder);
}