package com.covaimetertaxi.driver.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.covaimetertaxi.driver.R

object TtsAnnouncer {
    private const val TAG = "TtsAnnouncer"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Pre-initialization is no longer required as TTS has been completely removed.
     * We keep this method as a lightweight stub so that existing lifecycle hooks do not break.
     */
    fun preInitialize(context: Context) {
        Log.d(TAG, "Running in pure pre-recorded audio mode with full Play Store AAB support.")
    }

    /**
     * Plays pre-recorded Tamil audio files from the raw resources folder.
     * Fully compatible with Play Store .aab (App Bundles) and ProGuard/R8 resource shrinking.
     */
    fun speakTamil(context: Context, text: String = "உங்கள் பயணத்திற்கு நன்றி. இறங்குவதற்கு முன் உடமைகளைச் சரிபார்த்துக் கொள்ளவும். இந்த நாள் இனிய நாளாக அமையட்டும்.") {
        val staticResId = when {
            text.contains("சீட் பெல்ட்") || text.contains("பாதுகாப்பாக") -> R.raw.trip_start
            text.contains("நன்றி") || text.contains("உடமைகளை") -> R.raw.trip_end
            else -> 0
        }

        if (staticResId != 0) {
            Log.d(TAG, "Playing static raw audio resource (resId=$staticResId)...")
            playRawAudio(context, staticResId)
            return
        }

        val rawResName = when {
            text.contains("சீட் பெல்ட்") || text.contains("பாதுகாப்பாக") -> "trip_start"
            text.contains("நன்றி") || text.contains("உடமைகளை") -> "trip_end"
            else -> null
        }

        if (rawResName != null) {
            val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
            if (resId != 0) {
                Log.d(TAG, "Pre-recorded raw audio '$rawResName' found via dynamic ID. Playing...")
                playRawAudio(context, resId)
            } else {
                Log.d(TAG, "Pre-recorded raw audio '$rawResName' NOT found in res/raw/.")
            }
        } else {
            Log.d(TAG, "No matching audio mapped for text: $text")
        }
    }

    /**
     * Plays any raw audio file by its resource name (e.g. "trip_start", "trip_end").
     */
    fun playRawByName(context: Context, rawName: String): Boolean {
        val staticId = when (rawName.lowercase().trim()) {
            "trip_start" -> R.raw.trip_start
            "trip_end" -> R.raw.trip_end
            else -> context.resources.getIdentifier(rawName, "raw", context.packageName)
        }
        return if (staticId != 0) {
            playRawAudio(context, staticId)
        } else {
            Log.w(TAG, "Audio resource '$rawName' not found in res/raw.")
            false
        }
    }

    /**
     * Plays raw audio using openRawResourceFd with explicit offsets and AudioAttributes,
     * ensuring 100% reliable playback in Play Store .aab (Android App Bundle) release builds.
     */
    private fun playRawAudio(context: Context, resId: Int): Boolean {
        return try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            // Primary: Use openRawResourceFd with explicit startOffset and length for AAB compatibility
            try {
                val afd = context.resources.openRawResourceFd(resId)
                if (afd != null) {
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build()
                        )
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        prepare()
                        setOnCompletionListener { mp ->
                            mp.release()
                            if (mediaPlayer == mp) {
                                mediaPlayer = null
                            }
                        }
                        start()
                    }
                    mediaPlayer = player
                    return true
                }
            } catch (fdException: Exception) {
                Log.w(TAG, "openRawResourceFd fallback to MediaPlayer.create: ${fdException.message}")
            }

            // Fallback: Standard MediaPlayer.create
            val player = MediaPlayer.create(context.applicationContext, resId)
            if (player != null) {
                player.setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                }
                player.start()
                mediaPlayer = player
                true
            } else {
                Log.e(TAG, "MediaPlayer.create returned null for resId=$resId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing raw audio file: ${e.message}", e)
            false
        }
    }

    /**
     * Clean up media player resources when active components are destroyed.
     */
    fun shutdown() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d(TAG, "Audio player shut down successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down audio player: ${e.message}", e)
        }
    }
}
