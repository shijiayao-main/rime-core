package com.jiaoay.rime.core

/**
 * Rime候選項
 */
class RimeCandidate {
    @JvmField
    var text: String? = null
    @JvmField
    var comment: String? = null

    constructor(text: String?, comment: String?) {
        this.text = text
        this.comment = comment
    }

    constructor() {}
}