package com.jiaoay.rime.core

/**
 * Rime方案
 */
class RimeSchema(private val schemaId: String) {
    private val kRadioSelected = " ✓"
    var schema: Map<String, Any>? = HashMap()
    var switches: MutableList<MutableMap<String, Any>> = ArrayList()

    init {
        init()
    }

    fun init() {
        var o: Any? = RimeManager.Instance.schemaGetValue(schemaId, "schema")
        if (o == null || o !is Map<*, *>) return
        schema = o as Map<String, Any>
        o = RimeManager.Instance.schemaGetValue(schemaId, "switches")
        if (o == null || o !is List<*>) return
        switches = o as MutableList<MutableMap<String, Any>>
        check() // 檢查不在選單中顯示的選項
        o = RimeManager.Instance.schemaGetValue(schemaId, "menu")
        if (o == null || o !is HashMap<*, *>) return
    }

    fun check() {
        if (switches.isEmpty()) return
        val it: MutableIterator<*> = switches.iterator()
        while (it.hasNext()) {
            val o = it.next() as Map<*, *>
            if (!o.containsKey("states")) it.remove()
        }
    }

    val candidates: Array<RimeCandidate?>?
        get() {
            if (switches.isEmpty()) return null
            val candidates = arrayOfNulls<RimeCandidate>(switches.size)
            var i = 0
            for (o in switches) {
                candidates[i] = RimeCandidate()
                val states = o["states"] as List<*>?
                var value = o["value"] as Int?
                if (value == null) value = 0
                candidates[i]!!.text = states!![value].toString()
                val kRightArrow = "→ "
                if (RimeManager.Instance.showSwitchArrow) candidates[i]!!.comment = if (o.containsKey("options")) "" else kRightArrow + states[1 - value].toString() else candidates[i]!!.comment = if (o.containsKey("options")) "" else states[1 - value].toString()
                i++
            }
            return candidates
        }

    // 無方案
    val value: Unit
        get() {
            if (switches.isEmpty()) return  // 無方案
            for (j in switches.indices) {
                val o = switches[j]
                if (o.containsKey("options")) {
                    val options = o["options"] as List<*>?
                    for (i in options!!.indices) {
                        val s = options[i] as String
                        if (RimeManager.Instance.getOption(s)) {
                            o["value"] = i
                            break
                        }
                    }
                } else {
                    o["value"] = if (RimeManager.Instance.getOption(o["name"].toString())) 1 else 0
                }
                switches[j] = o
            }
        }

    fun toggleOption(i: Int) {
        if (switches.isEmpty()) return
        val o: MutableMap<String, Any> = switches[i]
        var value: Int = o["value"]?.let {
            if (it is Int) {
                it
            } else {
                0
            }
        } ?: 0
        if (o.containsKey("options")) {
            val options: List<String> = o["options"]?.let {
                if (it is List<*>) {
                    it as List<String>
                } else {
                    listOf()
                }
            } ?: listOf()
            RimeManager.Instance.setOption(options[value], false)
            value = (value + 1) % options.size
            RimeManager.Instance.setOption(options[value], true)
        } else {
            value = 1 - value
            RimeManager.Instance.setOption(o["name"].toString(), value == 1)
        }
        o["value"] = value
        switches[i] = o
    }
}