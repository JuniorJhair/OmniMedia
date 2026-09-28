package com.example.player.audio

import android.media.audiofx.Equalizer
import android.util.Log
import com.example.player.model.EqualizerBand
import com.example.player.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OmniEqualizerManager {
    private var equalizer: Equalizer? = null
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            readEqualizerState()
        } catch (e: Exception) {
            Log.w("OmniEqualizer", "Equalizer not supported on this device/session: ${e.message}")
            _state.value = EqualizerState(
                isAvailable = false,
                statusMessage = "El ecualizador de audio no es compatible con el dispositivo o salida actual."
            )
        }
    }

    fun readEqualizerState() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands
            val bandRange = eq.bandLevelRange
            val minGain = if (bandRange != null && bandRange.size >= 2) bandRange[0] else (-1500).toShort()
            val maxGain = if (bandRange != null && bandRange.size >= 2) bandRange[1] else 1500.toShort()

            val bands = (0 until numBands).map { i ->
                val shortIdx = i.toShort()
                EqualizerBand(
                    index = shortIdx,
                    centerFreqHz = eq.getCenterFreq(shortIdx) / 1000,
                    currentGainMb = eq.getBandLevel(shortIdx),
                    minGainMb = minGain,
                    maxGainMb = maxGain
                )
            }

            val numPresets = eq.numberOfPresets
            val presets = (0 until numPresets).map { i ->
                eq.getPresetName(i.toShort())
            }

            _state.value = EqualizerState(
                isAvailable = true,
                isEnabled = eq.enabled,
                bands = bands,
                presets = presets,
                currentPresetIndex = runCatching { eq.currentPreset }.getOrDefault((-1).toShort()),
                statusMessage = "Ecualizador activo y sincronizado"
            )
        } catch (e: Exception) {
            _state.value = EqualizerState(
                isAvailable = false,
                statusMessage = "No se pudieron obtener parámetros de ecualización: ${e.message}"
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        try {
            eq.enabled = enabled
            _state.value = _state.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("OmniEqualizer", "Error setting enabled", e)
        }
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(bandIndex, gainMb)
            val updatedBands = _state.value.bands.map {
                if (it.index == bandIndex) it.copy(currentGainMb = gainMb) else it
            }
            _state.value = _state.value.copy(bands = updatedBands, currentPresetIndex = -1)
        } catch (e: Exception) {
            Log.e("OmniEqualizer", "Error setting band gain", e)
        }
    }

    fun setPreset(presetIndex: Short) {
        val eq = equalizer ?: return
        try {
            eq.usePreset(presetIndex)
            readEqualizerState()
        } catch (e: Exception) {
            Log.e("OmniEqualizer", "Error applying preset", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
    }
}
