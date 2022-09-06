package com.jiaoay.rime.core

/**
 * Rime候選區，包含多個[候選項][RimeCandidate]
 */
class RimeMenu {
    var page_size = 0
    var page_no = 0
    var is_last_page = false
    var highlighted_candidate_index = 0
    var num_candidates = 0
    var candidates: Array<RimeCandidate?>? = null
    var select_keys: String? = null
}