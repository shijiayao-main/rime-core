package com.jiaoay.rime.core

import android.text.TextUtils
import com.jiaoay.rime.data.AppPrefs
import com.jiaoay.rime.data.DataManager
import com.jiaoay.rime.data.opencc.OpenCCDictManager
import java.io.BufferedReader
import java.io.CharArrayWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class RimeManager private constructor() {

    companion object {
        val Instance: RimeManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            RimeManager()
        }
    }

    private val rimeCommit = RimeCommit()
    private val rimeContext: RimeContext = RimeContext()
    private val rimeStatus = RimeStatus()
    private var rimeSchema: RimeSchema? = null
    private var rimeSchemaList: List<Map<String?, String?>?>? = null
    private var onMessage: AtomicBoolean = AtomicBoolean(false)

    private var showSwitches = true

    var showSwitchArrow = false

    var rimeListener: RimeListener? = null

    private val rime: Rime by lazy {
        Rime()
    }

    init {
        Rime.notificationCallback = { messageType, messageValue ->
            onMessage.set(true)
            rimeListener?.handleRimeNotification(messageType, messageValue)
            onMessage.set(false)
        }
    }

    /////
    fun openccDictConv(src: String, dest: String, mode: Boolean) {
        rime.openccDictConv(src, dest, mode)
    }

    fun getOpenccVersion(): String {
        return rime.get_opencc_version() ?: ""
    }

    fun getLibRimeVersion(): String {
        return rime.get_librime_version() ?: ""
    }

    fun selectSchemas(schemaIdList: Array<String?>): Boolean {
        return rime.select_schemas(schemaIdList)
    }

    fun getSelectedSchemaList(): List<Map<String?, String?>?>? {
        return rime.get_selected_schema_list()
    }

    fun getAvailableSchemaList(): List<Map<String?, String?>?>? {
        return rime.get_available_schema_list()
    }

    fun schemaGetValue(name: String, key: String): Any? {
        return rime.schema_get_value(name, key)
    }

    fun configGetMap(name: String, key: String): Map<String?, Map<String?, *>?>? {
        return rime.config_get_map(name = name, key = key)
    }

    fun getKeycodeByName(name: String): Int {
        return rime.get_keycode_by_name(name)
    }

    fun deployConfigFile(fileName: String, versionKey: String) {
        rime.deploy_config_file(fileName, versionKey)
    }

    fun getUserDataDir(): String {
        return rime.get_user_data_dir()
    }
    //////

    fun syncRime(fullCheck: Boolean = false) {
        onMessage.set(false)
        val appPrefs = AppPrefs.defaultInstance()
        val sharedDataDir = appPrefs.profile.sharedDataDir
        val userDataDir = appPrefs.profile.userDataDir

        // Initialize librime APIs
        rime.setup(sharedDataDir, userDataDir)
        rime.initialize(sharedDataDir, userDataDir)
        check(fullCheck)
        rime.set_notification_handler()
        if (rime.find_session() || rime.create_session() != 0) {
            initSchema()
        }
        if (fullCheck) {
            OpenCCDictManager.internalDeploy()
        }
    }


    fun syncUserData(): Boolean {
        val result = rime.sync_user_data()
        destroyRime()
        syncRime(true)
        return result
    }

    fun getCommitText(): String {
        return rimeCommit.text
    }

    fun getCommit(): Boolean {
        return rime.get_commit(rimeCommit)
    }

    // get_context() 是耗时操作
    fun getContexts(): Boolean {
        // TODO: get_context() 是耗时操作
        rime.get_context(rimeContext)
        return getStatus()
    }

    fun setShowSwitches(show: Boolean) {
        showSwitches = show
    }

    fun hasMenu(): Boolean {
        return isComposing && rimeContext.menu?.num_candidates != 0
    }

    fun hasLeft(): Boolean {
        return hasMenu() && rimeContext.menu?.page_no != 0
    }

    fun hasRight(): Boolean {
        return hasMenu() && rimeContext.menu?.is_last_page != true
    }

    val isPaging: Boolean
        get() = hasLeft()

    val isComposing: Boolean
        get() = rimeStatus.is_composing

    val isAsciiMode: Boolean
        get() = rimeStatus.is_ascii_mode

    val isAsciiPunch: Boolean
        get() = rimeStatus.is_ascii_punct

    fun showAsciiPunch(): Boolean {
        return rimeStatus.is_ascii_punct || rimeStatus.is_ascii_mode
    }

    val composition: RimeComposition?
        get() = rimeContext.composition

    val compositionText: String
        get() {
            return composition?.preedit ?: ""
        }

    fun getComposingText(): String {
        return if (rimeContext.commitTextPreview == null) {
            ""
        } else {
            rimeContext.commitTextPreview ?: ""
        }
    }

    fun initSchema() {
        rimeSchemaList = rime.get_schema_list()
        rimeSchema = RimeSchema(schemaId)
        getStatus()
    }

    fun getStatus(): Boolean {
        rimeSchema?.value
        return rime.get_status(rimeStatus)
    }

    fun destroyRime() {
        rime.destroy_session()
        rime.finalize1()
    }


    fun check(full_check: Boolean) {
        if (
            rime.start_maintenance(full_check) &&
            rime.is_maintenance_mode()
        ) {
            rime.join_maintenance_thread()
        }
    }

    fun openccConvert(line: String, name: String): String {
        if (!TextUtils.isEmpty(name)) {
            val f = File(DataManager.getDataDir("opencc"), name)
            if (f.exists()) {
                return rime.opencc_convert(line, f.absolutePath) ?: ""
            }
        }
        return line
    }


    fun rimeGetInput(): String {
        return rime.get_input() ?: ""
    }

    fun rimeGetCaretPos(): Int {
        return rime.get_caret_pos()
    }

    fun rimeSetCaretPos(caret_pos: Int) {
        rime.set_caret_pos(caret_pos)
        runBlocking {
            getContexts()
        }
    }

    fun selectSchema(id: Int): Boolean {
        rimeSchemaList?.let { list ->
            val n = list.size
            if (id < 0 || id >= n) return false
            val m: Map<String?, String?> = list[id] ?: return false
            val target: String = m["schema_id"] ?: return false
            return if (target.contentEquals(schemaId)) {
                false
            } else {
                selectSchema(target)
            }
        }
        return false
    }


    private fun overWriteSchema(schema_id: String, map: MutableMap<String, String>): Boolean {
        val file = File(
            rime.get_user_data_dir() + File.separator + "build",
            "$schema_id.schema.yaml"
        )
        try {
            val `in` = FileReader(file)
            val bufIn = BufferedReader(`in`)
            val tempStream = CharArrayWriter()
            var line: String?
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


    // 刷新当前输入方案
    fun applySchemaChange() {
        // 实测直接select_schema(schema_id)方案没有重新载入，切换到不存在的方案，再切回去（会产生1秒的额外耗时）.需要找到更好的方法
        // 不发生覆盖则不生效
        if (overWriteSchema(schemaId)) {
            rime.select_schema("null")
            rime.select_schema(schemaId)
        }
        getContexts()
    }


    fun isVoidKeycode(keycode: Int): Boolean {
        val voidSymbol = 0xffffff
        return keycode <= 0 || keycode == voidSymbol
    }

    // KeyProcess 调用JNI方法发送keycode和mask
    private fun onKey(keycode: Int, mask: Int): Boolean {
        if (isVoidKeycode(keycode)) return false
        // 此处调用native方法是耗时操作
        val b = rime.process_key(keycode, mask)
        getContexts()
        return b
    }

    // KeyProcess 调用JNI方法发送keycode和mask
    fun onKey(event: IntArray?): Boolean {
        return if (event != null && event.size == 2) {
            onKey(event[0], event[1])
        } else {
            false
        }
    }

    fun isValidText(text: CharSequence?): Boolean {
        if (text == null || text.isEmpty()) return false
        val ch = text.toString().codePointAt(0)
        return ch in 0x20..0x7f
    }

    fun onText(text: CharSequence): Boolean {
        if (!isValidText(text)) return false
        val b = rime.simulate_key_sequence(text.toString().replace("{}", "{braceleft}{braceright}"))
        getContexts()
        return b
    }

    fun getCandidates(): Array<RimeCandidate?> {
        return if (!isComposing && showSwitches) {
            rimeSchema?.candidates ?: arrayOf()
        } else {
            rimeContext.candidates ?: arrayOf()
        }
    }

    val candidatesWithoutSwitch: Array<RimeCandidate?>?
        get() = if (isComposing) {
            rimeContext.candidates
        } else {
            null
        }

    val selectLabels: Array<String?>?
        get() {
            if (rimeContext.size() > 0) {
                rimeContext.selectLabels?.let {
                    return it
                }
                rimeContext.menu?.select_keys?.let {
                    return it.split("\\B".toRegex())
                        .dropLastWhile {
                            it.isEmpty()
                        }
                        .toTypedArray()
                }
                val n = rimeContext.size()
                val labels = arrayOfNulls<String>(n)
                for (i in 0 until n) {
                    labels[i] = ((i + 1) % 10).toString()
                }
                return labels
            }
            return null
        }

    fun getCandHighlightIndex(): Int {
        return if (isComposing) {
            rimeContext.menu?.highlighted_candidate_index ?: -1
        } else {
            -1
        }
    }


    fun commitComposition(): Boolean {
        val b = rime.commit_composition()
        getContexts()
        return b
    }

    fun clearComposition() {
        rime.clear_composition()
        getContexts()
    }

    fun selectCandidate(index: Int): Boolean {
        // TODO: 之后再改
        val b = rime.select_candidate_on_current_page(index)
        runBlocking {
            getContexts()
        }
        return b
    }

    fun deleteCandidate(index: Int): Boolean {
        // TODO: 之后再改
        val b = rime.delete_candidate_on_current_page(index)
        runBlocking {
            getContexts()
        }
        return b
    }

    fun setOption(option: String?, value: Boolean) {
        if (onMessage.get()) return
        rime.set_option(option, value)
    }

    fun getOption(option: String?): Boolean {
        return rime.get_option(option)
    }

    fun toggleOption(option: String?) {
        val b = getOption(option)
        setOption(option, !b)
    }

    fun toggleOption(i: Int) {
        rimeSchema?.toggleOption(i)
    }

    fun setProperty(prop: String?, value: String?) {
        if (onMessage.get()) return
        rime.set_property(prop, value)
    }

    fun getProperty(prop: String?): String {
        return rime.get_property(prop)
    }

    val schemaId: String
        get() = rime.get_current_schema()

    val isEmpty: Boolean
        get() {
            return schemaId.contentEquals(".default")
        }

    val schemaNames: Array<String?>
        get() {
            rimeSchemaList?.let { list ->
                val n = list.size
                val names = arrayOfNulls<String>(n)
                for ((i, o) in list.withIndex()) {
                    names[i] = o?.get("name") ?: ""
                }
                return names
            }

            return arrayOf()
        }

    val schemaIndex: Int
        get() {
            rimeSchemaList?.let { list ->
                for ((i, o) in list.withIndex()) {
                    if (o?.get("schema_id").toString().contentEquals(schemaId)) {
                        return i
                    }
                }
            }
            return 0
        }

    val schemaName: String
        get() = rimeStatus.schema_name

    private fun selectSchema(schemaId: String): Boolean {
        overWriteSchema(schemaId)
        val b = rime.select_schema(schemaId)
        getContexts()
        return b
    }

    // 临时修改scheme文件参数
    // 临时修改build后的scheme可以避免build过程的耗时
    // 另外实际上jni读入yaml、修改、导出的效率并不高
    private fun overWriteSchema(schemaId: String): Boolean {
        val map: MutableMap<String, String> = HashMap()
        val pageSize = AppPrefs.defaultInstance().keyboard.candidatePageSize
        if (pageSize != "0") {
            map["page_size"] = pageSize
        }
        return if (map.isEmpty()) false else overWriteSchema(schemaId, map)
    }


    /**
     * 部署config文件到build目录
     *
     * @param name         配置名称，不含yaml后缀
     * @param skipIfExists 启用此模式时，如build目录已经存在对应名称的文件，且大小超过10k，则不重新部署，从而节约时间
     * @return
     */
    fun deployConfigFile(name: String, skipIfExists: Boolean): Boolean {
        val fileName = "$name.yaml"
        if (skipIfExists) {
            val f = File(rime.get_user_data_dir() + File.separator + "build", fileName)
            if (f.exists()) {
                if (f.length() > 10000) {
                    return true
                }
            } else {
                return rime.deploy_config_file(fileName, "config_version")
            }
        }
        return rime.deploy_config_file(fileName, "config_version")
    }


    /**
     * Android SDK包含了如下6个修饰键的状态，其中function键会被trime消费掉，因此只处理5个键
     * Android和librime对按键命名并不一致。读取可能有误。librime按键命名见如下链接，
     * https://github.com/rime/librime/blob/master/src/rime/key_table.cc
     */
    fun metaShiftOn(): Int {
        return rime.get_modifier_by_name("Shift")
    }

    fun metaCtrlOn(): Int {
        return rime.get_modifier_by_name("Control")
    }

    fun metaAltOn(): Int {
        return rime.get_modifier_by_name("Alt")
    }

    fun metaSymOn(): Int {
        return rime.get_modifier_by_name("Super")
    }

    fun metaMetaOn(): Int {
        return rime.get_modifier_by_name("Meta")
    }

    fun metaReleaseOn(): Int {
        return rime.get_modifier_by_name("Release")
    }
}