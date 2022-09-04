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

import android.content.Context
import android.text.TextUtils
import com.jiaoay.rime.core.RimeEvent.OptionEvent
import com.jiaoay.rime.core.RimeEvent.RimeNotificationHandler.create
import com.jiaoay.rime.core.RimeEvent.SchemaEvent
import com.jiaoay.rime.data.AppPrefs.Companion.defaultInstance
import com.jiaoay.rime.data.DataManager.getDataDir
import com.jiaoay.rime.data.opencc.OpenCCDictManager.internalDeploy
import java.io.*

/**
 * Rime與OpenCC的Java實現
 *
 * @see [Rime](https://github.com/rime/librime) [OpenCC](https://github.com/BYVoid/OpenCC)
 */
class Rime(context: Context?, full_check: Boolean) {
    init {
        init(full_check)
        self = this
    }

    companion object {
        private var self: Rime? = null
        private val mCommit = RimeCommit()
        private val mContext: RimeContext? = RimeContext()
        private val mStatus = RimeStatus()
        private var mSchema: RimeSchema? = null
        private var mSchemaList: List<*>? = null
        private var mOnMessage = false

        /**
         * Android SDK包含了如下6个修饰键的状态，其中function键会被trime消费掉，因此只处理5个键
         * Android和librime对按键命名并不一致。读取可能有误。librime按键命名见如下链接，
         * https://github.com/rime/librime/blob/master/src/rime/key_table.cc
         */
        @JvmField
        var META_SHIFT_ON = get_modifier_by_name("Shift")

        @JvmField
        var META_CTRL_ON = get_modifier_by_name("Control")

        @JvmField
        var META_ALT_ON = get_modifier_by_name("Alt")

        @JvmField
        var META_SYM_ON = get_modifier_by_name("Super")

        @JvmField
        var META_META_ON = get_modifier_by_name("Meta")

        @JvmField
        var META_RELEASE_ON = get_modifier_by_name("Release")
        private var showSwitches = true

        @JvmField
        var showSwitchArrow = false

        @JvmStatic
        fun setShowSwitches(show: Boolean) {
            showSwitches = show
        }

        @JvmStatic
        fun setShowSwitchArrow(show: Boolean) {
            showSwitchArrow = show
        }

        @JvmStatic
        fun hasMenu(): Boolean {
            return isComposing && mContext!!.menu.num_candidates != 0
        }

        @JvmStatic
        fun hasLeft(): Boolean {
            return hasMenu() && mContext!!.menu.page_no != 0
        }

        @JvmStatic
        fun hasRight(): Boolean {
            return hasMenu() && !mContext!!.menu.is_last_page
        }

        @JvmStatic
        val isPaging: Boolean
            get() = hasLeft()
        @JvmStatic
        val isComposing: Boolean
            get() = mStatus.is_composing
        @JvmStatic
        val isAsciiMode: Boolean
            get() = mStatus.is_ascii_mode
        @JvmStatic
        val isAsciiPunch: Boolean
            get() = mStatus.is_ascii_punct

        @JvmStatic
        fun showAsciiPunch(): Boolean {
            return mStatus.is_ascii_punct || mStatus.is_ascii_mode
        }

        @JvmStatic
        val composition: RimeComposition?
            get() = if (mContext == null) null else mContext.composition
        val compositionText: String
            get() {
                val composition = composition
                return if (composition == null || composition.preedit == null) "" else composition.preedit
            }
        val composingText: String
            get() = if (mContext!!.commit_text_preview == null) "" else mContext.commit_text_preview

        private fun initSchema() {
            mSchemaList = get_schema_list()
            val schema_id = schemaId
            mSchema = RimeSchema(schema_id)
            status
        }

        private val status: Boolean
            private get() {
                mSchema!!.getValue()
                return get_status(mStatus)
            }

        private fun init(full_check: Boolean) {
            val methodName =
                "\t<TrimeInit>\t" + Thread.currentThread().stackTrace[2].methodName + "\t"
            mOnMessage = false
            val appPrefs = defaultInstance()
            val sharedDataDir = appPrefs.profile.sharedDataDir
            val userDataDir = appPrefs.profile.userDataDir

            // Initialize librime APIs
            setup(sharedDataDir, userDataDir)
            initialize(sharedDataDir, userDataDir)
            check(full_check)
            set_notification_handler()
            if (!find_session()) {
                if (create_session() == 0) {
                    return
                }
            }
            initSchema()
        }

        fun destroy() {
            destroy_session()
            finalize1()
            self = null
        }

        val commitText: String
            get() = mCommit.text
        val commit: Boolean
            get() = get_commit(mCommit)

        // get_context() 是耗时操作
        val contexts: Unit
            get() {
                // get_context() 是耗时操作
                get_context(mContext)
                status
            }

        fun isVoidKeycode(keycode: Int): Boolean {
            val XK_VoidSymbol = 0xffffff
            return keycode <= 0 || keycode == XK_VoidSymbol
        }

        // KeyProcess 调用JNI方法发送keycode和mask
        private fun onKey(keycode: Int, mask: Int): Boolean {
            if (isVoidKeycode(keycode)) return false
            // 此处调用native方法是耗时操作
            val b = process_key(keycode, mask)
            contexts
            return b
        }

        // KeyProcess 调用JNI方法发送keycode和mask
        fun onKey(event: IntArray?): Boolean {
            return if (event != null && event.size == 2) onKey(event[0], event[1]) else false
        }

        fun isValidText(text: CharSequence?): Boolean {
            if (text == null || text.length == 0) return false
            val ch = text.toString().codePointAt(0)
            return ch >= 0x20 && ch < 0x80
        }

        fun onText(text: CharSequence): Boolean {
            if (!isValidText(text)) return false
            val b = simulate_key_sequence(text.toString().replace("{}", "{braceleft}{braceright}"))
            contexts
            return b
        }

        @JvmStatic
        val candidates: Array<RimeCandidate>
            get() = if (!isComposing && showSwitches) mSchema!!.candidates else mContext!!.candidates

        @JvmStatic
        val candidatesWithoutSwitch: Array<RimeCandidate>?
            get() = if (isComposing) mContext!!.candidates else null

        @JvmStatic
        val selectLabels: Array<String?>?
            get() {
                if (mContext != null && mContext.size() > 0) {
                    if (mContext.select_labels != null) return mContext.select_labels
                    if (mContext.menu.select_keys != null) return mContext.menu.select_keys.split("\\B".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val n = mContext.size()
                    val labels = arrayOfNulls<String>(n)
                    for (i in 0 until n) {
                        labels[i] = ((i + 1) % 10).toString()
                    }
                    return labels
                }
                return null
            }

        @JvmStatic
        val candHighlightIndex: Int
            get() = if (isComposing) mContext!!.menu.highlighted_candidate_index else -1

        fun commitComposition(): Boolean {
            val b = commit_composition()
            contexts
            return b
        }

        fun clearComposition() {
            clear_composition()
            contexts
        }

        fun selectCandidate(index: Int): Boolean {
            val b = select_candidate_on_current_page(index)
            contexts
            return b
        }

        fun deleteCandidate(index: Int): Boolean {
            val b = delete_candidate_on_current_page(index)
            contexts
            return b
        }

        @JvmStatic
        fun setOption(option: String?, value: Boolean) {
            if (mOnMessage) return
            set_option(option, value)
        }

        @JvmStatic
        fun getOption(option: String?): Boolean {
            return get_option(option)
        }

        fun toggleOption(option: String?) {
            val b = getOption(option)
            setOption(option, !b)
        }

        fun toggleOption(i: Int) {
            mSchema!!.toggleOption(i)
        }

        fun setProperty(prop: String?, value: String?) {
            if (mOnMessage) return
            set_property(prop, value)
        }

        fun getProperty(prop: String?): String {
            return get_property(prop)
        }

        @JvmStatic
        val schemaId: String
            get() = get_current_schema()

        private fun isEmpty(s: String): Boolean {
            return s.contentEquals(".default") // 無方案
        }

        val isEmpty: Boolean
            get() = isEmpty(schemaId)
        val schemaNames: Array<String?>
            get() {
                val n = mSchemaList!!.size
                val names = arrayOfNulls<String>(n)
                var i = 0
                for (o in mSchemaList!!) {
                    val m = o as Map<*, *>
                    names[i++] = m["name"] as String?
                }
                return names
            }
        val schemaIndex: Int
            get() {
                val schema_id = schemaId
                var i = 0
                for (o in mSchemaList!!) {
                    val m = o as Map<*, *>
                    if (m["schema_id"].toString().contentEquals(schema_id)) return i
                    i++
                }
                return 0
            }

        @JvmStatic
        val schemaName: String
            get() = mStatus.schema_name

        private fun selectSchema(schema_id: String?): Boolean {
            overWriteSchema(schema_id)
            val b = select_schema(schema_id)
            contexts
            return b
        }

        // 刷新当前输入方案
        fun applySchemaChange() {
            val schema_id = schemaId
            // 实测直接select_schema(schema_id)方案没有重新载入，切换到不存在的方案，再切回去（会产生1秒的额外耗时）.需要找到更好的方法
            // 不发生覆盖则不生效
            if (overWriteSchema(schema_id)) {
                select_schema("nill")
                select_schema(schema_id)
            }
            contexts
        }

        // 临时修改scheme文件参数
        // 临时修改build后的scheme可以避免build过程的耗时
        // 另外实际上jni读入yaml、修改、导出的效率并不高
        private fun overWriteSchema(schema_id: String?): Boolean {
            val map: MutableMap<String, String> = HashMap()
            val page_size = defaultInstance().keyboard.candidatePageSize
            if (page_size != "0") {
                map["page_size"] = page_size
            }
            return if (map.isEmpty()) false else overWriteSchema(schema_id, map)
        }

        private fun overWriteSchema(schema_id: String?, map: MutableMap<String, String>): Boolean {
            var schema_id = schema_id
            if (schema_id == null) schema_id = schemaId
            val file =
                File(get_user_data_dir() + File.separator + "build", "$schema_id.schema.yaml")
            try {
                val `in` = FileReader(file)
                val bufIn = BufferedReader(`in`)
                val tempStream = CharArrayWriter()
                var line: String? = null
                read@ while (bufIn.readLine().also { line = it } != null) {
                    for (k in map.keys) {
                        val key = "$k: "
                        if (line!!.contains(key)) {
                            val value = ": " + map[k] + System.getProperty("line.separator")
                            tempStream.write(line!!.replaceFirst(":.+".toRegex(), value))
                            map.remove(k)
                            continue@read
                        }
                    }
                    tempStream.write(line)
                    tempStream.append(System.getProperty("line.separator"))
                }
                bufIn.close()
                val out = FileWriter(file)
                tempStream.writeTo(out)
                out.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
            return map.isEmpty()
        }

        fun selectSchema(id: Int): Boolean {
            val n = mSchemaList!!.size
            if (id < 0 || id >= n) return false
            val schema_id = schemaId
            val m = mSchemaList!![id] as Map<String, String>
            val target = m["schema_id"]
            return if (target.contentEquals(schema_id)) false else selectSchema(target)
        }

        @JvmStatic
        @JvmOverloads
        operator fun get(context: Context?, full_check: Boolean = false): Rime? {
            if (self == null) {
                if (full_check) {
                    internalDeploy()
                }
                self = Rime(context, full_check)
                System.loadLibrary("rime")
            }
            return self
        }

        @JvmStatic
        fun RimeGetInput(): String {
            val s = get_input()
            return s ?: ""
        }

        fun RimeGetCaretPos(): Int {
            return get_caret_pos()
        }

        @JvmStatic
        fun RimeSetCaretPos(caret_pos: Int) {
            set_caret_pos(caret_pos)
            contexts
        }

        @JvmStatic
        fun handleRimeNotification(message_type: String?, message_value: String) {
            mOnMessage = true
            /*val event = create(message_type!!, message_value)
            // Timber.i("message: [%s] %s", message_type, message_value);
            val rimeInputMethodEditorService: RimeInputMethodEditorService =
                RimeInputMethodEditorService.getService()
            if (event is SchemaEvent) {
                initSchema()
                IMEExtensionsKt.initKeyboard(rimeInputMethodEditorService)
            } else if (event is OptionEvent) {
                status
                contexts // 切換中英文、簡繁體時更新候選
                val value = !message_value.startsWith("!")
                val option = message_value.substring(if (value) 0 else 1)
                rimeInputMethodEditorService.getTextInputManager().onOptionChanged(option, value)
            }*/
            mOnMessage = false
        }

        @JvmStatic
        fun openccConvert(line: String?, name: String?): String? {
            if (!TextUtils.isEmpty(name)) {
                val f = File(getDataDir("opencc"), name)
                if (f.exists()) return opencc_convert(line, f.absolutePath)
            }
            return line
        }

        fun check(full_check: Boolean) {
            if (start_maintenance(full_check) && is_maintenance_mode()) {
                join_maintenance_thread()
            }
        }

        fun syncUserData(context: Context?): Boolean {
            val b = sync_user_data()
            destroy()
            Companion[context, true]
            return b
        }

        // init
        @JvmStatic
        external fun setup(shared_data_dir: String?, user_data_dir: String?)
        @JvmStatic
        external fun set_notification_handler()

        // entry and exit
        @JvmStatic
        external fun initialize(shared_data_dir: String?, user_data_dir: String?)
        @JvmStatic
        external fun finalize1()
        @JvmStatic
        external fun start_maintenance(full_check: Boolean): Boolean
        @JvmStatic
        external fun is_maintenance_mode(): Boolean
        @JvmStatic
        external fun join_maintenance_thread()

        // deployment
        @JvmStatic
        external fun deployer_initialize(shared_data_dir: String?, user_data_dir: String?)
        @JvmStatic
        external fun prebuild(): Boolean
        @JvmStatic
        external fun deploy(): Boolean
        @JvmStatic
        external fun deploy_schema(schema_file: String?): Boolean
        @JvmStatic
        external fun deploy_config_file(file_name: String?, version_key: String?): Boolean

        /**
         * 部署config文件到build目录
         *
         * @param name         配置名称，不含yaml后缀
         * @param skipIfExists 启用此模式时，如build目录已经存在对应名称的文件，且大小超过10k，则不重新部署，从而节约时间
         * @return
         */
        @JvmStatic
        fun deploy_config_file(name: String, skipIfExists: Boolean): Boolean {
            val file_name = "$name.yaml"
            if (skipIfExists) {
                val f = File(get_user_data_dir() + File.separator + "build", file_name)
                if (f.exists()) {
                    if (f.length() > 10000) {
                        return true
                    }
                } else {
                    return deploy_config_file(file_name, "config_version")
                }
            }
            return deploy_config_file(file_name, "config_version")
        }

        @JvmStatic
        external fun sync_user_data(): Boolean

        // session management
        @JvmStatic
        external fun create_session(): Int
        @JvmStatic
        external fun find_session(): Boolean
        @JvmStatic
        external fun destroy_session(): Boolean
        @JvmStatic
        external fun cleanup_stale_sessions()
        @JvmStatic
        external fun cleanup_all_sessions()

        // input
        @JvmStatic
        external fun process_key(keycode: Int, mask: Int): Boolean
        @JvmStatic
        external fun commit_composition(): Boolean
        @JvmStatic
        external fun clear_composition()

        // output
        @JvmStatic
        external fun get_commit(commit: RimeCommit?): Boolean
        @JvmStatic
        external fun get_context(context: RimeContext?): Boolean
        @JvmStatic
        external fun get_status(status: RimeStatus?): Boolean

        // runtime options
        @JvmStatic
        external fun set_option(option: String?, value: Boolean)

        @JvmStatic
        external fun get_option(option: String?): Boolean
        @JvmStatic
        external fun set_property(prop: String?, value: String?)
        @JvmStatic
        external fun get_property(prop: String?): String
        @JvmStatic
        external fun get_schema_list(): List<Map<String?, String?>?>?
        @JvmStatic
        external fun get_current_schema(): String
        @JvmStatic
        external fun select_schema(schema_id: String?): Boolean

        // configuration
        @JvmStatic
        external fun config_get_bool(name: String?, key: String?): Boolean?
        @JvmStatic
        external fun config_set_bool(name: String?, key: String?, value: Boolean): Boolean
        @JvmStatic
        external fun config_get_int(name: String?, key: String?): Int?
        @JvmStatic
        external fun config_set_int(name: String?, key: String?, value: Int): Boolean
        @JvmStatic
        external fun config_get_double(name: String?, key: String?): Double?
        @JvmStatic
        external fun config_set_double(name: String?, key: String?, value: Double): Boolean
        @JvmStatic
        external fun config_get_string(name: String?, key: String?): String?
        @JvmStatic
        external fun config_set_string(name: String?, key: String?, value: String?): Boolean
        @JvmStatic
        external fun config_list_size(name: String?, key: String?): Int
        @JvmStatic
        external fun config_get_list(name: String?, key: String?): List<*>?

        @JvmStatic
        external fun config_get_map(name: String?, key: String?): Map<String?, Map<String?, *>?>?
        @JvmStatic
        external fun config_get_value(name: String?, key: String?): Any?

        @JvmStatic
        external fun schema_get_value(name: String?, key: String?): Any?

        // testing
        @JvmStatic
        external fun simulate_key_sequence(key_sequence: String?): Boolean
        @JvmStatic
        external fun get_input(): String?
        @JvmStatic
        external fun get_caret_pos(): Int
        @JvmStatic
        external fun set_caret_pos(caret_pos: Int)
        @JvmStatic
        external fun select_candidate(index: Int): Boolean
        @JvmStatic
        external fun select_candidate_on_current_page(index: Int): Boolean
        @JvmStatic
        external fun delete_candidate(index: Int): Boolean
        @JvmStatic
        external fun delete_candidate_on_current_page(index: Int): Boolean
        @JvmStatic
        external fun get_version(): String?
        @JvmStatic
        external fun get_librime_version(): String?

        // module
        @JvmStatic
        external fun run_task(task_name: String?): Boolean
        @JvmStatic
        external fun get_shared_data_dir(): String?

        @JvmStatic
        external fun get_user_data_dir(): String
        @JvmStatic
        external fun get_sync_dir(): String?
        @JvmStatic
        external fun get_user_id(): String?

        // key_table
        @JvmStatic
        external fun get_modifier_by_name(name: String?): Int

        @JvmStatic
        external fun get_keycode_by_name(name: String?): Int

        // customize setting
        @JvmStatic
        external fun customize_bool(name: String?, key: String?, value: Boolean): Boolean
        @JvmStatic
        external fun customize_int(name: String?, key: String?, value: Int): Boolean
        @JvmStatic
        external fun customize_double(name: String?, key: String?, value: Double): Boolean
        @JvmStatic
        external fun customize_string(name: String?, key: String?, value: String?): Boolean
        @JvmStatic
        external fun get_available_schema_list(): List<Map<String?, String?>?>?
        @JvmStatic
        external fun get_selected_schema_list(): List<Map<String?, String?>?>?
        @JvmStatic
        external fun select_schemas(schema_id_list: Array<String?>?): Boolean

        // opencc
        @JvmStatic
        external fun get_opencc_version(): String?
        @JvmStatic
        external fun opencc_convert(line: String?, name: String?): String?
        @JvmStatic
        external fun opencc_convert_dictionary(
            inputFileName: String?, outputFileName: String?, formatFrom: String?, formatTo: String?
        )
        @JvmStatic
        external fun get_trime_version(): String?
    }
}