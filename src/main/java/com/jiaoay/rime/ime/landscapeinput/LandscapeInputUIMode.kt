package com.jiaoay.rime.ime.landscapeinput

import java.util.*

enum class LandscapeInputUIMode {
    AUTO_SHOW,
    ALWAYS_SHOW,
    NEVER_SHOW;

    companion object {
        private val convertMap: HashMap<String, LandscapeInputUIMode> = hashMapOf()
        fun fromString(mode: String): LandscapeInputUIMode {
            val type = convertMap[mode.uppercase(Locale.getDefault())]
            return type ?: AUTO_SHOW
        }
    }
}
