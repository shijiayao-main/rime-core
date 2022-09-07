/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jiaoay.rime.core

/**
 * Rime與OpenCC的Java實現
 *
 * @see [Rime](https://github.com/rime/librime) [OpenCC](https://github.com/BYVoid/OpenCC)
 */
class Rime {

    var notificationCallback: ((String, String) -> Unit)? = null

    init {
        System.loadLibrary("rime")
    }

    fun handleRimeNotification(message_type: String?, message_value: String?) {
        notificationCallback?.invoke(message_type ?: "", message_value ?: "")
    }

    // init
    external fun setup(shared_data_dir: String?, user_data_dir: String?)

    external fun set_notification_handler()

    // entry and exit
    external fun initialize(shared_data_dir: String?, user_data_dir: String?)

    external fun finalize1()

    external fun start_maintenance(full_check: Boolean): Boolean

    external fun is_maintenance_mode(): Boolean

    external fun join_maintenance_thread()

    // deployment
    external fun deployer_initialize(shared_data_dir: String?, user_data_dir: String?)

    external fun prebuild(): Boolean

    external fun deploy(): Boolean

    external fun deploy_schema(schema_file: String?): Boolean

    external fun deploy_config_file(file_name: String?, version_key: String?): Boolean

    external fun sync_user_data(): Boolean

    // session management
    external fun create_session(): Int

    external fun find_session(): Boolean

    external fun destroy_session(): Boolean

    external fun cleanup_stale_sessions()

    external fun cleanup_all_sessions()

    // input
    external fun process_key(keycode: Int, mask: Int): Boolean

    external fun commit_composition(): Boolean

    external fun clear_composition()

    // output
    external fun get_commit(commit: RimeCommit?): Boolean

    external fun get_context(context: RimeContext?): Boolean

    external fun get_status(status: RimeStatus?): Boolean

    // runtime options
    external fun set_option(option: String?, value: Boolean)

    external fun get_option(option: String?): Boolean

    external fun set_property(prop: String?, value: String?)

    external fun get_property(prop: String?): String

    external fun get_schema_list(): List<Map<String?, String?>?>?

    external fun get_current_schema(): String

    external fun select_schema(schema_id: String?): Boolean

    // configuration
    external fun config_get_bool(name: String?, key: String?): Boolean?

    external fun config_set_bool(name: String?, key: String?, value: Boolean): Boolean

    external fun config_get_int(name: String?, key: String?): Int?

    external fun config_set_int(name: String?, key: String?, value: Int): Boolean

    external fun config_get_double(name: String?, key: String?): Double?

    external fun config_set_double(name: String?, key: String?, value: Double): Boolean

    external fun config_get_string(name: String?, key: String?): String?

    external fun config_set_string(name: String?, key: String?, value: String?): Boolean

    external fun config_list_size(name: String?, key: String?): Int

    external fun config_get_list(name: String?, key: String?): List<*>?

    external fun config_get_map(name: String, key: String): Map<String?, Map<String?, *>?>?

    external fun config_get_value(name: String?, key: String?): Any?

    external fun schema_get_value(name: String, key: String): Any?

    // testing
    external fun simulate_key_sequence(key_sequence: String?): Boolean

    external fun get_input(): String?

    external fun get_caret_pos(): Int

    external fun set_caret_pos(caret_pos: Int)

    external fun select_candidate(index: Int): Boolean

    external fun select_candidate_on_current_page(index: Int): Boolean

    external fun delete_candidate(index: Int): Boolean

    external fun delete_candidate_on_current_page(index: Int): Boolean

    external fun get_version(): String?

    external fun get_librime_version(): String?

    // module
    external fun run_task(task_name: String?): Boolean

    external fun get_shared_data_dir(): String?

    external fun get_user_data_dir(): String

    external fun get_sync_dir(): String?

    external fun get_user_id(): String?

    // key_table
    external fun get_modifier_by_name(name: String?): Int

    external fun get_keycode_by_name(name: String?): Int

    // customize setting
    external fun customize_bool(name: String?, key: String?, value: Boolean): Boolean

    external fun customize_int(name: String?, key: String?, value: Int): Boolean

    external fun customize_double(name: String?, key: String?, value: Double): Boolean

    external fun customize_string(name: String?, key: String?, value: String?): Boolean

    external fun get_available_schema_list(): List<Map<String?, String?>?>?

    external fun get_selected_schema_list(): List<Map<String?, String?>?>?

    external fun select_schemas(schema_id_list: Array<String?>): Boolean

    // opencc
    external fun get_opencc_version(): String?

    external fun opencc_convert(line: String?, name: String?): String?

    external fun opencc_convert_dictionary(
        inputFileName: String?, outputFileName: String?, formatFrom: String?, formatTo: String?
    )

    external fun get_trime_version(): String?

    external fun openccDictConv(src: String, dest: String, mode: Boolean)
}