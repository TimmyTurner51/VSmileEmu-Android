package com.vsmileemu.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Manages audio playback for the emulator with non-blocking writes
 */
class AudioManager {
    companion object {
        private const val TAG = "AudioManager"
        private const val SAMPLE_RATE = 48000 // Output sample rate after resampling
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var audioQueue = Channel<ShortArray>(capacity = 4)
    private var audioJob: Job? = null
    private var droppedSamples = 0
    
    /**
     * Initialize the audio system
     */
    fun initialize() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            val bufferSize = minBufferSize * 4
            
            Log.i(TAG, "Audio buffer config: minSize=$minBufferSize, using=${bufferSize}")
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            Log.i(TAG, "AudioTrack initialized: sample rate=$SAMPLE_RATE, buffer=$bufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio", e)
            audioTrack = null
        }
    }
    
    /**
     * Start audio playback
     */
    fun start() {
        try {
            audioTrack?.let { track ->
                if (!isPlaying) {
                    // Recreate channel if it was closed
                    if (audioQueue.isClosedForSend) {
                        audioQueue = Channel(capacity = 4)
                    }
                    
                    track.play()
                    isPlaying = true
                    Log.i(TAG, "Audio playback started")
                    
                    // Start audio writing coroutine (runs on IO dispatcher to avoid blocking)
                    droppedSamples = 0
                    audioJob = audioScope.launch {
                        try {
                            for (samples in audioQueue) {
                                if (isPlaying && samples.isNotEmpty()) {
                                    // Use blocking write for smooth playback
                                    val written = track.write(samples, 0, samples.size)
                                    if (written < 0) {
                                        Log.w(TAG, "AudioTrack write error: $written")
                                    } else if (written < samples.size) {
                                        Log.w(TAG, "Partial audio write: $written / ${samples.size}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Audio writer coroutine error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
        }
    }
    
    /**
     * Stop audio playback
     */
    fun stop() {
        try {
            isPlaying = false
            audioJob?.cancel()
            audioJob = null
            audioQueue.close()
            
            audioTrack?.let {
                it.pause()
                it.flush()
                Log.i(TAG, "Audio playback stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio playback", e)
        }
    }
    
    /**
     * Write audio samples to the playback buffer (non-blocking)
     * @param samples ShortArray of stereo 16-bit PCM samples (interleaved L-R-L-R...)
     */
    fun writeSamples(samples: ShortArray) {
        try {
            if (isPlaying && samples.isNotEmpty()) {
                // Non-blocking write - if queue is full, skip this batch
                val result = audioQueue.trySend(samples)
                if (result.isFailure) {
                    droppedSamples++
                    if (droppedSamples % 100 == 0) {
                        Log.w(TAG, "Audio queue full, dropped $droppedSamples sample batches")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue audio samples", e)
        }
    }
    
    /**
     * Release audio resources
     */
    fun release() {
        try {
            stop()
            audioTrack?.release()
            audioTrack = null
            Log.i(TAG, "Audio resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release audio", e)
        }
    }
    
    /**
     * Get the current playback state
     */
    fun isPlaying(): Boolean = isPlaying
}

