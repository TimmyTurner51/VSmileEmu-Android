#include <jni.h>
#include <android/log.h>
#include <memory>
#include <cstring>
#include <vector>

// Undefine Android system register macros that conflict with veesem
#ifdef REG_R0
#undef REG_R0
#endif
#ifdef REG_R1
#undef REG_R1
#endif
#ifdef REG_R2
#undef REG_R2
#endif
#ifdef REG_R3
#undef REG_R3
#endif
#ifdef REG_R4
#undef REG_R4
#endif
#ifdef REG_R5
#undef REG_R5
#endif
#ifdef REG_R6
#undef REG_R6
#endif
#ifdef REG_R7
#undef REG_R7
#endif

#include "core/vsmile/vsmile.h"
#include "core/vsmile/vsmile_joy.h"

#define LOG_TAG "VSmileNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global emulator instance
static std::unique_ptr<VSmile> g_vsmile;

extern "C" {

/**
 * Initialize the emulator with ROM data
 * @param sysrom System ROM (2MB), nullable
 * @param cartrom Cartridge ROM (8MB max)
 * @param cartSize Actual size of cartridge ROM
 * @param usePAL true for PAL timing (50Hz), false for NTSC (60Hz)
 */
JNIEXPORT jboolean JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeInit(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray sysrom,
        jbyteArray cartrom,
        jint cartSize,
        jboolean usePAL) {
    
    try {
        // Prepare system ROM
        auto sysrom_data = std::make_unique<VSmile::SysRomType>();
        
        if (sysrom != nullptr) {
            jbyte* sysrom_bytes = env->GetByteArrayElements(sysrom, nullptr);
            jsize sysrom_len = env->GetArrayLength(sysrom);
            
            if (sysrom_len != sizeof(VSmile::SysRomType)) {
                LOGE("Invalid system ROM size: %d (expected %zu)", sysrom_len, sizeof(VSmile::SysRomType));
                env->ReleaseByteArrayElements(sysrom, sysrom_bytes, JNI_ABORT);
                return JNI_FALSE;
            }
            
            // Copy ROM data
            // ROM files are little-endian, Android ARM is little-endian, so no swap needed
            std::memcpy(sysrom_data.get(), sysrom_bytes, sysrom_len);
            env->ReleaseByteArrayElements(sysrom, sysrom_bytes, JNI_ABORT);
            
            LOGI("System ROM loaded (%zu bytes)", sysrom_len);
        } else {
            // Provide dummy ROM if none provided
            sysrom_data->fill(0);
            // Add minimal boot code to make games work
            for (int i = 0xfffc0; i < 0xfffdc; i += 2) {
                (*sysrom_data)[i + 1] = 0x31;
            }
            LOGI("Using dummy system ROM");
        }
        
        // Prepare cartridge ROM
        auto cartrom_data = std::make_unique<VSmile::CartRomType>();
        cartrom_data->fill(0);
        
        jbyte* cartrom_bytes = env->GetByteArrayElements(cartrom, nullptr);
        jsize cartrom_len = env->GetArrayLength(cartrom);
        
        if (cartrom_len <= 0 || cartrom_len > sizeof(VSmile::CartRomType)) {
            LOGE("Invalid cartridge ROM size: %d", cartrom_len);
            env->ReleaseByteArrayElements(cartrom, cartrom_bytes, JNI_ABORT);
            return JNI_FALSE;
        }
        
        // Copy cartridge ROM
        // ROM files are little-endian, Android ARM is little-endian, so no swap needed
        std::memcpy(cartrom_data.get(), cartrom_bytes, cartrom_len);
        env->ReleaseByteArrayElements(cartrom, cartrom_bytes, JNI_ABORT);
        
        LOGI("Cartridge ROM loaded (%d bytes)", cartrom_len);
        
        // Create emulator instance
        VideoTiming timing = usePAL ? VideoTiming::PAL : VideoTiming::NTSC;
        
        g_vsmile = std::make_unique<VSmile>(
            std::move(sysrom_data),
            std::move(cartrom_data),
            VSmile::CartType::STANDARD,
            nullptr,  // No Art Studio NVRAM
            0xe,      // UK English region
            true,     // Show VTech logo
            timing
        );
        
        // CRITICAL: Reset the system to initialize CPU state and program counter
        g_vsmile->Reset();
        LOGI("VSmile system reset - CPU initialized");
        
        LOGI("Emulator initialized successfully (%s timing)", usePAL ? "PAL" : "NTSC");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Failed to initialize emulator: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * Run one frame of emulation
 */
JNIEXPORT void JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeRunFrame(
        JNIEnv* /* env */,
        jobject /* this */) {
    
    if (!g_vsmile) {
        LOGE("runFrame: g_vsmile is NULL!");
        return;
    }
    
    static int frame_counter = 0;
    if (frame_counter < 3) {
        LOGI("runFrame: Calling RunFrame() #%d", frame_counter);
    }
    
    g_vsmile->RunFrame();
    
    if (frame_counter < 3) {
        LOGI("runFrame: RunFrame() #%d completed", frame_counter);
    }
    frame_counter++;
}

/**
 * Get the current video frame (320x240 RGB565 format)
 * @return ByteArray containing frame data
 */
JNIEXPORT jbyteArray JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeGetFrameBuffer(
        JNIEnv* env,
        jobject /* this */) {
    
    if (!g_vsmile) {
        LOGE("getFrameBuffer: g_vsmile is NULL!");
        return nullptr;
    }
    
    auto picture = g_vsmile->GetPicture();
    LOGI("getFrameBuffer: picture.size() = %zu", picture.size());
    
    if (picture.size() == 0) {
        LOGE("getFrameBuffer: picture is EMPTY!");
        // Return an empty array instead of null for clarity
        return env->NewByteArray(0);
    }
    
    jbyteArray result = env->NewByteArray(static_cast<jsize>(picture.size()));
    
    if (result == nullptr) {
        LOGE("getFrameBuffer: NewByteArray FAILED!");
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(picture.size()),
                            reinterpret_cast<const jbyte*>(picture.data()));
    
    LOGI("getFrameBuffer: Returning %zu bytes", picture.size());
    return result;
}

/**
 * Get audio samples for the current frame
 * @return ShortArray containing stereo audio samples
 * 
 * IMPORTANT: VSmile SPU outputs UNSIGNED 16-bit audio (0-65535)
 * but Android AudioTrack expects SIGNED 16-bit (-32768 to 32767)
 * We need to convert by subtracting 32768 (or XOR with 0x8000)
 */
JNIEXPORT jshortArray JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeGetAudioSamples(
        JNIEnv* env,
        jobject /* this */) {
    
    if (!g_vsmile) {
        return nullptr;
    }
    
    auto audio = g_vsmile->GetAudio();
    jshortArray result = env->NewShortArray(static_cast<jsize>(audio.size()));
    
    if (result == nullptr) {
        return nullptr;
    }
    
    // Convert unsigned 16-bit to signed 16-bit
    // Method: XOR with 0x8000 to flip the sign bit (fastest method)
    std::vector<int16_t> converted(audio.size());
    for (size_t i = 0; i < audio.size(); i++) {
        converted[i] = static_cast<int16_t>(audio[i] ^ 0x8000);
    }
    
    env->SetShortArrayRegion(result, 0, static_cast<jsize>(converted.size()),
                             converted.data());
    
    return result;
}

/**
 * Send joystick input to the emulator
 */
JNIEXPORT void JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeSendInput(
        JNIEnv* /* env */,
        jobject /* this */,
        jboolean enter,
        jboolean help,
        jboolean back,
        jboolean abc,
        jboolean red,
        jboolean yellow,
        jboolean blue,
        jboolean green,
        jint joyX,
        jint joyY) {
    
    if (!g_vsmile) {
        return;
    }
    
    VSmileJoy::JoyInput input{};
    input.enter = enter;
    input.help = help;
    input.back = back;
    input.abc = abc;
    input.red = red;
    input.yellow = yellow;
    input.blue = blue;
    input.green = green;
    input.x = static_cast<int>(joyX);
    input.y = static_cast<int>(joyY);
    
    g_vsmile->UpdateJoystick(input);
}

/**
 * Press console power ON button
 */
JNIEXPORT void JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativePressOnButton(
        JNIEnv* /* env */,
        jobject /* this */,
        jboolean pressed) {
    
    if (g_vsmile) {
        g_vsmile->UpdateOnButton(pressed);
    }
}

/**
 * Destroy the emulator instance
 */
JNIEXPORT void JNICALL
Java_com_vsmileemu_android_core_EmulatorCore_nativeDestroy(
        JNIEnv* /* env */,
        jobject /* this */) {
    
    g_vsmile.reset();
    LOGI("Emulator destroyed");
}

} // extern "C"
