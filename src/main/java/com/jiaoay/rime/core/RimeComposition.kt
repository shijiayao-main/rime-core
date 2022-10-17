package com.jiaoay.rime.core

/**
 * Rime編碼區
 */
class RimeComposition {
    @JvmField
    var length = 0
    @JvmField
    var cursor_pos = 0
    @JvmField
    var sel_start = 0
    @JvmField
    var sel_end = 0
    @JvmField
    var preedit: String? = null
    @JvmField
    var bytes: ByteArray? = null
    val text: String?
        get() {
            if (length == 0) return ""
            bytes = preedit?.toByteArray()
            return preedit
        }
    val start: Int
        get() = if (length == 0) {
            0
        } else {
            bytes?.let {
                String(it, 0, sel_start).length
            } ?: 0
        }
    val end: Int
        get() = if (length == 0) {
            0
        } else {
            bytes?.let {
                String(it, 0, sel_end).length
            } ?: 0
        }
}