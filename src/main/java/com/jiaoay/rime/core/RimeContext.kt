package com.jiaoay.rime.core

/**
 * Rime環境，包括 [編碼區][RimeComposition] 、[候選區][RimeMenu]
 */
class RimeContext {
    @JvmField
    var dataSize = 0

    // v0.9
    @JvmField
    var composition: RimeComposition? = null

    @JvmField
    var menu: RimeMenu? = null

    // v0.9.2
    @JvmField
    var commitTextPreview: String? = null
    @JvmField
    var selectLabels: Array<String?>? = null

    fun size(): Int {
        return if (menu == null) 0 else menu?.num_candidates ?: 0
    }

    val candidates: Array<RimeCandidate?>?
        get() = if (size() == 0) null else menu?.candidates
}