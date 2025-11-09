/**
 * Android Emulator Wrapper
 * Wraps veesem core with Android-friendly interfaces
 */

#pragma once

#include <cstdint>
#include <vector>
#include <memory>
#include <chrono>

// Forward declare veesem types (keeps veesem includes isolated)
class VSmile;
enum class VideoTiming;

/**
 * Controller input state
 */
struct ControllerInput {
    bool enter = false;
    bool help = false;
    bool back = false;
    bool abc = false;
    bool red = false;
    bool yellow = false;
    bool blue = false;
    bool green = false;
    int joystickX = 0;  // -5 to +5
    int joystickY = 0;  // -5 to +5
};

/**
 * Android Emulator - Wraps veesem core
 */
class AndroidEmulator {
public:
    AndroidEmulator();
    ~AndroidEmulator();
    
    /**
     * Initialize emulator with ROM data
     * @param biosData BIOS file data (can be null for dummy BIOS)
     * @param biosSize Size of BIOS data
     * @param romData ROM file data (required)
     * @param romSize Size of ROM data
     * @param timing PAL or NTSC video timing
     * @return true on success
     */
    bool initialize(
        const uint8_t* biosData,
        size_t biosSize,
        const uint8_t* romData,
        size_t romSize,
        VideoTiming timing
    );
    
    /**
     * Run one frame of emulation
     */
    void runFrame();
    
    /**
     * Update controller input
     */
    void updateInput(const ControllerInput& input);
    
    /**
     * Get framebuffer (320x240 RGB565)
     * Returns pointer to internal buffer (valid until next runFrame)
     */
    const uint8_t* getFramebuffer() const;
    
    /**
     * Get framebuffer size in bytes
     */
    size_t getFramebufferSize() const;
    
    /**
     * Get audio samples (16-bit stereo interleaved)
     * Returns pointer to internal buffer (valid until next runFrame)
     */
    const int16_t* getAudioSamples() const;
    
    /**
     * Get number of audio samples (per channel)
     */
    size_t getAudioSampleCount() const;
    
    /**
     * Pause emulation
     */
    void pause();
    
    /**
     * Resume emulation
     */
    void resume();
    
    /**
     * Reset emulation
     */
    void reset();
    
    /**
     * Is emulation paused?
     */
    bool isPaused() const;
    
    /**
     * Get current FPS
     */
    float getFPS() const;
    
    /**
     * Save state to byte array
     */
    std::vector<uint8_t> saveState();
    
    /**
     * Load state from byte array
     */
    bool loadState(const std::vector<uint8_t>& data);
    
private:
    std::unique_ptr<VSmile> emulator_;
    std::vector<uint8_t> framebuffer_;  // RGB565 format
    std::vector<int16_t> audioBuffer_;   // Stereo interleaved
    bool paused_ = false;
    
    // FPS tracking
    using Clock = std::chrono::high_resolution_clock;
    using TimePoint = std::chrono::time_point<Clock>;
    TimePoint lastFrameTime_;
    float currentFPS_ = 0.0f;
    int frameCount_ = 0;
    TimePoint fpsUpdateTime_;
    
    void updateFPS();
    void convertFramebufferRGB555toRGB565(const uint8_t* src, uint8_t* dst, size_t pixelCount);
};






