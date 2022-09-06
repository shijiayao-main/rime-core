package com.jiaoay.rime.core

/**
 * Rime狀態
 */
class RimeStatus {
    var data_size = 0

    // v0.9
    var schema_id: String = ""
    var schema_name: String = ""
    var is_disabled = false
    var is_composing = false
    var is_ascii_mode = false
    var is_full_shape = false
    var is_simplified = false
    var is_traditional = false
    var is_ascii_punct = false
}