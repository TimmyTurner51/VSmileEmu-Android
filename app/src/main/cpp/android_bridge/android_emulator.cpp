/**
 * Android Emulator Wrapper Implementation
 */

#include "android_emulator.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

// Include veesem core
#include "core/vsmile/vsmile.h"

#define LOG_TAG "AndroidEmulator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Display constants
constexpr int DISPLAY_WIDTH = 320;
constexpr int DISPLAY_HEIGHT = 240;
constexpr size_t FRAMEBUFFER_SIZE = DISPLAY_WIDTH * DISPLAY_HEIGHT * 2; // RGB565

AndroidEmulator::AndroidEmulator()
    : paused_(false),
      currentFPS_(0.0f),
      frameCount_(0) {
    
    // Pre-allocate buffers
    framebuffer_.resize(FRAMEBUFFER_SIZE);
    audioBuffer_.reserve(2048 * 2); // Reserve space for audio
    
    lastFrameTime_ = Clock::now();
    fpsUpdateTime_ = lastFrameTime_;
}

AndroidEmulator::~AndroidEmulator() {
    emulator_.reset();
}

bool AndroidEmulator::initialize(
    const uint8_t* biosData,
    size_t biosSize,
    const uint8_t* romData,
    size_t romSize,
    VideoTiming timing) {
    
    if (romData == nullptr || romSize == 0) {
        LOGE("ROM data is required");
        return false;
    }
    
    try {
        // Prepare BIOS
        std::unique_ptr<VSmile::SysRomType> sysRom;
        if (biosData != nullptr && biosSize > 0) {
            sysRom = std::make_unique<VSmile::SysRomType>();
            size_t copySize = std::min(biosSize, sysRom->size() * sizeof(word_t));
            std::memcpy(sysRom->data(), biosData, copySize);
            LOGI("BIOS loaded: %zu bytes", copySize);
        } else {
            // Use dummy BIOS (veesem supports this)
            sysRom = nullptr;
            LOGI("Using dummy BIOS");
        }
        
        // Prepare ROM
        auto cartRom = std::make_unique<VSmile::CartRomType>();
        size_t copySize = std::min(romSize, cartRom->size() * sizeof(word_t));
        std::memcpy(cartRom->data(), romData, copySize);
        LOGI("ROM loaded: %zu bytes", copySize);
        
        // Create emulator instance
        emulator_ = std::make_unique<VSmile>(
            std::move(sysRom),
            std::move(cartRom),
            VSmile::CartType::STANDARD,
            nullptr,  // No Art Studio NVRAM for now
            0xe,      // Region code (UK English)
            true,     // Show VTech logo
            timing
        );
        
        LOGI("Emulator initialized successfully");
        return true;
        
    } catch (const std::exception& e) {
        LOGE("Exception during initialization: %s", e.what());
        return false;
    }
}

void AndroidEmulator::runFrame() {
    if (paused_ || !emulator_) {
        return;
    }
    
    // Run one frame of emulation
    emulator_->RunFrame();
    
    // Get video output (RGB555 format)
    auto picture = emulator_->GetPicture();
    
    // Convert RGB555 to RGB565 (Android-friendly)
    convertFramebufferRGB555toRGB565(
        picture.data(),
        framebuffer_.data(),
        DISPLAY_WIDTH * DISPLAY_HEIGHT
    );
    
    // Get audio output
    auto audioSpan = emulator_->GetAudio();
    audioBuffer_.clear();
    audioBuffer_.insert(
        audioBuffer_.end(),
        audioSpan.begin(),
        audioSpan.end()
    );
    
    // Update FPS counter
    updateFPS();
}

void AndroidEmulator::updateInput(const ControllerInput& input) {
    if (!emulator_) {
        return;
    }
    
    // Convert to veesem format
    VSmile::JoyInput joyInput;
    joyInput.enter = input.enter;
    joyInput.help = input.help;
    joyInput.back = input.back;
    joyInput.abc = input.abc;
    joyInput.red = input.red;
    joyInput.yellow = input.yellow;
    joyInput.blue = input.blue;
    joyInput.green = input.green;
    joyInput.x = input.joystickX;
    joyInput.y = input.joystickY;
    
    emulator_->UpdateJoystick(joyInput);
}

const uint8_t* AndroidEmulator::getFramebuffer() const {
    return framebuffer_.data();
}

size_t AndroidEmulator::getFramebufferSize() const {
    return framebuffer_.size();
}

const int16_t* AndroidEmulator::getAudioSamples() const {
    return audioBuffer_.data();
}

size_t AndroidEmulator::getAudioSampleCount() const {
    return audioBuffer_.size();
}

void AndroidEmulator::pause() {
    paused_ = true;
    LOGI("Emulation paused");
}

void AndroidEmulator::resume() {
    paused_ = false;
    LOGI("Emulation resumed");
}

void AndroidEmulator::reset() {
    if (emulator_) {
        emulator_->Reset();
        LOGI("Emulator reset");
    }
}

bool AndroidEmulator::isPaused() const {
    return paused_;
}

float AndroidEmulator::getFPS() const {
    return currentFPS_;
}

std::vector<uint8_t> AndroidEmulator::saveState() {
    // TODO: Implement save state serialization
    // This would serialize emulator state to bytes
    LOGI("Save state requested (not yet implemented)");
    return std::vector<uint8_t>();
}

bool AndroidEmulator::loadState(const std::vector<uint8_t>& data) {
    // TODO: Implement load state deserialization
    LOGI("Load state requested (not yet implemented)");
    return false;
}

void AndroidEmulator::updateFPS() {
    frameCount_++;
    
    auto now = Clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        now - fpsUpdateTime_
    ).count();
    
    // Update FPS every second
    if (elapsed >= 1000) {
        currentFPS_ = frameCount_ * 1000.0f / elapsed;
        frameCount_ = 0;
        fpsUpdateTime_ = now;
    }
}

void AndroidEmulator::convertFramebufferRGB555toRGB565(
    const uint8_t* src,
    uint8_t* dst,
    size_t pixelCount) {
    
    // Convert RGB555 (veesem format) to RGB565 (Android OpenGL ES format)
    // RGB555: XRRRRRGGGGGBBBBB (X = unused bit)
    // RGB565: RRRRRGGGGGGBBBBB
    
    const uint16_t* src16 = reinterpret_cast<const uint16_t*>(src);
    uint16_t* dst16 = reinterpret_cast<uint16_t*>(dst);
    
    for (size_t i = 0; i < pixelCount; i++) {
        uint16_t color555 = src16[i];
        
        // Extract RGB555 components (5 bits each)
        uint16_t r = (color555 >> 10) & 0x1F;  // Red: bits 10-14
        uint16_t g = (color555 >> 5) & 0x1F;   // Green: bits 5-9
        uint16_t b = color555 & 0x1F;          // Blue: bits 0-4
        
        // Convert 5-bit green to 6-bit by duplicating MSB
        uint16_t g6 = (g << 1) | (g >> 4);
        
        // Pack into RGB565
        uint16_t color565 = (r << 11) | (g6 << 5) | b;
        
        dst16[i] = color565;
    }
}






