package com.jiaoay.rime.ime.keyboard

import com.jiaoay.rime.core.RimeManager
import com.jiaoay.rime.ime.enums.Keycode
import com.jiaoay.rime.ime.enums.Keycode.Companion.keyNameOf

class RimeKeycode private constructor() {
    private val rimeKeycode: MutableMap<Int, Int>

    init {
        rimeKeycode = HashMap()
    }

    fun getRimeCode(code: Int): Int {
        if (!rimeKeycode.containsKey(code)) rimeKeycode[code] = rimeCode(code)
        return rimeKeycode[code] ?: 0
    }

    companion object {
        private var self: RimeKeycode? = null

        @JvmStatic
        fun get(): RimeKeycode? {
            if (self == null) self = RimeKeycode()
            return self
        }

        // TODO 把软键盘预设android_keys的keycode(index)->keyname(string)—>rimeKeycode的过程改为直接返回int
        // https://github.com/rime/librime/blob/99e269c8eb251deddbad9b0d2c4d965b228f8006/src/rime/key_table.cc
        fun rimeCode(code: Int): Int {
            var i = 0
            if (code >= 0 && code < Keycode.values().size) {
                val s = keyNameOf(code)
                i = RimeManager.Instance.getKeycodeByName(s)
            }
            return i
        }
    }
}