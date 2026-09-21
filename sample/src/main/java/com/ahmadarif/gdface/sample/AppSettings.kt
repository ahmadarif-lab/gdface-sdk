package com.ahmadarif.gdface.sample

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahmadarif.gdface.sdk.GdFaceEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** The app's settings: saved in SharedPreferences and readable as Compose state. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("gdface_sample", Context.MODE_PRIVATE)

    var onboarded by pref("onboarded", false)
    var livenessEnabled by pref("liveness_enabled", true)
    var livenessThreshold by pref("liveness_threshold", GdFaceEngine.DEFAULT_LIVENESS_THRESHOLD)
    var maskEnabled by pref("mask_enabled", true)

    /** A face counts as masked when the SDK's mask score reaches this value. */
    var maskThreshold by pref("mask_threshold", 0.6f)

    private var recognitionDay by pref("recognition_day", "")
    private var recognitionCount by pref("recognition_count", 0)

    val recognitionsToday: Int get() = if (recognitionDay == today()) recognitionCount else 0

    fun recordRecognition() {
        val today = today()
        if (recognitionDay != today) {
            recognitionDay = today
            recognitionCount = 0
        }
        recognitionCount += 1
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun <T : Any> pref(key: String, default: T) = PrefState(key, default)

    private inner class PrefState<T : Any>(private val key: String, default: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(read(default))

        @Suppress("UNCHECKED_CAST")
        private fun read(default: T): T = when (default) {
            is Boolean -> prefs.getBoolean(key, default)
            is Float -> prefs.getFloat(key, default)
            is Int -> prefs.getInt(key, default)
            is String -> prefs.getString(key, default) ?: default
            else -> error("Unsupported preference type")
        } as T

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            prefs.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }.apply()
        }
    }
}
