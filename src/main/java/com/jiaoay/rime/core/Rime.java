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

package com.jiaoay.rime.core;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jiaoay.rime.data.AppPrefs;
import com.jiaoay.rime.data.DataManager;
import com.jiaoay.rime.data.opencc.OpenCCDictManager;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rime與OpenCC的Java實現
 *
 * @see <a href="https://github.com/rime/librime">Rime</a> <a
 * href="https://github.com/BYVoid/OpenCC">OpenCC</a>
 */
public class Rime {

    private static Rime self;

    private static final RimeCommit mCommit = new RimeCommit();
    private static final RimeContext mContext = new RimeContext();
    private static final RimeStatus mStatus = new RimeStatus();
    private static RimeSchema mSchema;
    private static List<?> mSchemaList;
    private static boolean mOnMessage;

    /**
     * Android SDK包含了如下6个修饰键的状态，其中function键会被trime消费掉，因此只处理5个键
     * Android和librime对按键命名并不一致。读取可能有误。librime按键命名见如下链接，
     * https://github.com/rime/librime/blob/master/src/rime/key_table.cc
     **/
    public static int META_SHIFT_ON = get_modifier_by_name("Shift");
    public static int META_CTRL_ON = get_modifier_by_name("Control");
    public static int META_ALT_ON = get_modifier_by_name("Alt");
    public static int META_SYM_ON = get_modifier_by_name("Super");
    public static int META_META_ON = get_modifier_by_name("Meta");

    public static int META_RELEASE_ON = get_modifier_by_name("Release");
    private static boolean showSwitches = true;
    static boolean showSwitchArrow = false;

    public static void setShowSwitches(boolean show) {
        showSwitches = show;
    }

    public static void setShowSwitchArrow(boolean show) {
        showSwitchArrow = show;
    }

    public static boolean hasMenu() {
        return isComposing() && mContext.menu.num_candidates != 0;
    }

    public static boolean hasLeft() {
        return hasMenu() && mContext.menu.page_no != 0;
    }

    public static boolean hasRight() {
        return hasMenu() && !mContext.menu.is_last_page;
    }

    public static boolean isPaging() {
        return hasLeft();
    }

    public static boolean isComposing() {
        return mStatus.is_composing;
    }

    public static boolean isAsciiMode() {
        return mStatus.is_ascii_mode;
    }

    public static boolean isAsciiPunch() {
        return mStatus.is_ascii_punct;
    }

    public static boolean showAsciiPunch() {
        return mStatus.is_ascii_punct || mStatus.is_ascii_mode;
    }

    public static RimeComposition getComposition() {
        if (mContext == null) return null;
        return mContext.composition;
    }

    public static String getCompositionText() {
        RimeComposition composition = getComposition();
        return (composition == null || composition.preedit == null) ? "" : composition.preedit;
    }

    public static String getComposingText() {
        if (mContext.commit_text_preview == null) return "";
        return mContext.commit_text_preview;
    }

    public Rime(Context context, boolean full_check) {
        init(full_check);
        self = this;
    }

    private static void initSchema() {
        mSchemaList = get_schema_list();
        String schema_id = getSchemaId();
        mSchema = new RimeSchema(schema_id);
        getStatus();
    }

    @SuppressWarnings("UnusedReturnValue")
    private static boolean getStatus() {
        mSchema.getValue();
        return get_status(mStatus);
    }

    private static void init(boolean full_check) {
        String methodName =
                "\t<TrimeInit>\t" + Thread.currentThread().getStackTrace()[2].getMethodName() + "\t";
        mOnMessage = false;
        final AppPrefs appPrefs = AppPrefs.defaultInstance();
        final String sharedDataDir = appPrefs.getProfile().getSharedDataDir();
        final String userDataDir = appPrefs.getProfile().getUserDataDir();

        // Initialize librime APIs
        setup(sharedDataDir, userDataDir);
        initialize(sharedDataDir, userDataDir);

        check(full_check);
        set_notification_handler();
        if (!find_session()) {
            if (create_session() == 0) {
                return;
            }
        }
        initSchema();

    }

    public static void destroy() {
        destroy_session();
        finalize1();
        self = null;
    }

    public static String getCommitText() {
        return mCommit.text;
    }

    public static boolean getCommit() {
        return get_commit(mCommit);
    }

    public static void getContexts() {
        // get_context() 是耗时操作
        get_context(mContext);
        getStatus();
    }

    public static boolean isVoidKeycode(int keycode) {
        int XK_VoidSymbol = 0xffffff;
        return keycode <= 0 || keycode == XK_VoidSymbol;
    }

    // KeyProcess 调用JNI方法发送keycode和mask
    private static boolean onKey(int keycode, int mask) {
        if (isVoidKeycode(keycode)) return false;
        // 此处调用native方法是耗时操作
        final boolean b = process_key(keycode, mask);
        getContexts();
        return b;
    }

    // KeyProcess 调用JNI方法发送keycode和mask
    public static boolean onKey(int[] event) {
        if (event != null && event.length == 2) return onKey(event[0], event[1]);
        return false;
    }

    public static boolean isValidText(CharSequence text) {
        if (text == null || text.length() == 0) return false;
        int ch = text.toString().codePointAt(0);
        return ch >= 0x20 && ch < 0x80;
    }

    public static boolean onText(CharSequence text) {
        if (!isValidText(text)) return false;
        boolean b = simulate_key_sequence(text.toString().replace("{}", "{braceleft}{braceright}"));
        getContexts();
        return b;
    }

    public static RimeCandidate[] getCandidates() {
        if (!isComposing() && showSwitches) return mSchema.getCandidates();
        return mContext.getCandidates();
    }

    public static RimeCandidate[] getCandidatesWithoutSwitch() {
        if (isComposing()) return mContext.getCandidates();
        return null;
    }

    public static String[] getSelectLabels() {
        if (mContext != null && mContext.size() > 0) {
            if (mContext.select_labels != null) return mContext.select_labels;
            if (mContext.menu.select_keys != null) return mContext.menu.select_keys.split("\\B");
            int n = mContext.size();
            String[] labels = new String[n];
            for (int i = 0; i < n; i++) {
                labels[i] = String.valueOf((i + 1) % 10);
            }
            return labels;
        }
        return null;
    }

    public static int getCandHighlightIndex() {
        return isComposing() ? mContext.menu.highlighted_candidate_index : -1;
    }

    public static boolean commitComposition() {
        boolean b = commit_composition();
        getContexts();
        return b;
    }

    public static void clearComposition() {
        clear_composition();
        getContexts();
    }

    public static boolean selectCandidate(int index) {
        boolean b = select_candidate_on_current_page(index);
        getContexts();
        return b;
    }

    public static boolean deleteCandidate(int index) {
        boolean b = delete_candidate_on_current_page(index);
        getContexts();
        return b;
    }

    public static void setOption(String option, boolean value) {
        if (mOnMessage) return;
        set_option(option, value);
    }

    public static boolean getOption(String option) {
        return get_option(option);
    }

    public static void toggleOption(String option) {
        boolean b = getOption(option);
        setOption(option, !b);
    }

    public static void toggleOption(int i) {
        mSchema.toggleOption(i);
    }

    public static void setProperty(String prop, String value) {
        if (mOnMessage) return;
        set_property(prop, value);
    }

    public static String getProperty(String prop) {
        return get_property(prop);
    }

    public static String getSchemaId() {
        return get_current_schema();
    }

    private static boolean isEmpty(@NonNull String s) {
        return s.contentEquals(".default"); // 無方案
    }

    public static boolean isEmpty() {
        return isEmpty(getSchemaId());
    }

    public static String[] getSchemaNames() {
        int n = mSchemaList.size();
        String[] names = new String[n];
        int i = 0;
        for (Object o : mSchemaList) {
            Map<?, ?> m = (Map<?, ?>) o;
            names[i++] = (String) m.get("name");
        }
        return names;
    }

    public static int getSchemaIndex() {
        String schema_id = getSchemaId();
        int i = 0;
        for (Object o : mSchemaList) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.get("schema_id").toString().contentEquals(schema_id)) return i;
            i++;
        }
        return 0;
    }

    public static String getSchemaName() {
        return mStatus.schema_name;
    }

    private static boolean selectSchema(String schema_id) {
        overWriteSchema(schema_id);
        boolean b = select_schema(schema_id);
        getContexts();
        return b;
    }

    // 刷新当前输入方案
    public static void applySchemaChange() {
        String schema_id = getSchemaId();
        // 实测直接select_schema(schema_id)方案没有重新载入，切换到不存在的方案，再切回去（会产生1秒的额外耗时）.需要找到更好的方法
        // 不发生覆盖则不生效
        if (overWriteSchema(schema_id)) {
            select_schema("nill");
            select_schema(schema_id);
        }
        getContexts();
    }

    // 临时修改scheme文件参数
    // 临时修改build后的scheme可以避免build过程的耗时
    // 另外实际上jni读入yaml、修改、导出的效率并不高
    private static boolean overWriteSchema(String schema_id) {
        Map<String, String> map = new HashMap<>();
        String page_size = AppPrefs.defaultInstance().getKeyboard().getCandidatePageSize();
        if (!page_size.equals("0")) {
            map.put("page_size", page_size);
        }
        if (map.isEmpty()) return false;
        return overWriteSchema(schema_id, map);
    }

    private static boolean overWriteSchema(String schema_id, Map<String, String> map) {
        if (schema_id == null) schema_id = getSchemaId();
        File file =
                new File(Rime.get_user_data_dir() + File.separator + "build", schema_id + ".schema.yaml");
        try {
            FileReader in = new FileReader(file);
            BufferedReader bufIn = new BufferedReader(in);
            CharArrayWriter tempStream = new CharArrayWriter();
            String line = null;
            read:
            while ((line = bufIn.readLine()) != null) {
                for (String k : map.keySet()) {
                    String key = k + ": ";
                    if (line.contains(key)) {
                        String value = ": " + map.get(k) + System.getProperty("line.separator");
                        tempStream.write(line.replaceFirst(":.+", value));
                        map.remove(k);
                        continue read;
                    }
                }
                tempStream.write(line);
                tempStream.append(System.getProperty("line.separator"));
            }
            bufIn.close();
            FileWriter out = new FileWriter(file);
            tempStream.writeTo(out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return map.isEmpty();
    }

    public static boolean selectSchema(int id) {
        int n = mSchemaList.size();
        if (id < 0 || id >= n) return false;
        final String schema_id = getSchemaId();
        Map<String, String> m = (Map<String, String>) mSchemaList.get(id);
        final String target = m.get("schema_id");
        if (target.contentEquals(schema_id)) return false;
        return selectSchema(target);
    }

    public static Rime get(Context context, boolean full_check) {
        if (self == null) {
            if (full_check) {
                OpenCCDictManager.internalDeploy();
            }
            self = new Rime(context, full_check);
            System.loadLibrary("rime");
        }
        return self;
    }

    public static Rime get(Context context) {
        return get(context, false);
    }

    public static String RimeGetInput() {
        String s = get_input();
        return s == null ? "" : s;
    }

    public static int RimeGetCaretPos() {
        return get_caret_pos();
    }

    public static void RimeSetCaretPos(int caret_pos) {
        set_caret_pos(caret_pos);
        getContexts();
    }

    public static void handleRimeNotification(String message_type, String message_value) {
        // TODO: 2022/9/2 临时解决编译问题
        /*mOnMessage = true;
        final RimeEvent event = RimeEvent.create(message_type, message_value);
        // Timber.i("message: [%s] %s", message_type, message_value);
        final RimeInputMethodEditorService rimeInputMethodEditorService = RimeInputMethodEditorService.getService();
        if (event instanceof RimeEvent.SchemaEvent) {
            initSchema();
            IMEExtensionsKt.initKeyboard(rimeInputMethodEditorService);
        } else if (event instanceof RimeEvent.OptionEvent) {
            getStatus();
            getContexts(); // 切換中英文、簡繁體時更新候選
            final boolean value = !message_value.startsWith("!");
            final String option = message_value.substring(value ? 0 : 1);
            rimeInputMethodEditorService.getTextInputManager().onOptionChanged(option, value);
        }
        mOnMessage = false;*/
    }

    public static String openccConvert(String line, String name) {
        if (!TextUtils.isEmpty(name)) {
            final File f = new File(DataManager.getDataDir("opencc"), name);
            if (f.exists()) return opencc_convert(line, f.getAbsolutePath());
        }
        return line;
    }

    public static void check(boolean full_check) {
        if (start_maintenance(full_check) && is_maintenance_mode()) {
            join_maintenance_thread();
        }
    }

    public static boolean syncUserData(Context context) {
        boolean b = sync_user_data();
        destroy();
        get(context, true);
        return b;
    }

    // init
    public static native void setup(String shared_data_dir, String user_data_dir);

    public static native void set_notification_handler();

    // entry and exit
    public static native void initialize(String shared_data_dir, String user_data_dir);

    public static native void finalize1();

    public static native boolean start_maintenance(boolean full_check);

    public static native boolean is_maintenance_mode();

    public static native void join_maintenance_thread();

    // deployment
    public static native void deployer_initialize(String shared_data_dir, String user_data_dir);

    public static native boolean prebuild();

    public static native boolean deploy();

    public static native boolean deploy_schema(String schema_file);

    public static native boolean deploy_config_file(String file_name, String version_key);

    /**
     * 部署config文件到build目录
     *
     * @param name         配置名称，不含yaml后缀
     * @param skipIfExists 启用此模式时，如build目录已经存在对应名称的文件，且大小超过10k，则不重新部署，从而节约时间
     * @return
     */
    public static boolean deploy_config_file(String name, boolean skipIfExists) {
        String file_name = name + ".yaml";
        if (skipIfExists) {
            File f = new File(Rime.get_user_data_dir() + File.separator + "build", file_name);
            if (f.exists()) {
                if (f.length() > 10000) {
                    return true;
                }
            } else {
                return Rime.deploy_config_file(file_name, "config_version");
            }
        }
        return Rime.deploy_config_file(file_name, "config_version");
    }

    public static native boolean sync_user_data();

    // session management
    public static native int create_session();

    public static native boolean find_session();

    public static native boolean destroy_session();

    public static native void cleanup_stale_sessions();

    public static native void cleanup_all_sessions();

    // input
    public static native boolean process_key(int keycode, int mask);

    public static native boolean commit_composition();

    public static native void clear_composition();

    // output
    public static native boolean get_commit(RimeCommit commit);

    public static native boolean get_context(RimeContext context);

    public static native boolean get_status(RimeStatus status);

    // runtime options
    public static native void set_option(String option, boolean value);

    public static native boolean get_option(String option);

    public static native void set_property(String prop, String value);

    public static native String get_property(String prop);

    @Nullable
    public static native List<Map<String, String>> get_schema_list();

    public static native String get_current_schema();

    public static native boolean select_schema(String schema_id);

    // configuration
    public static native Boolean config_get_bool(String name, String key);

    public static native boolean config_set_bool(String name, String key, boolean value);

    public static native Integer config_get_int(String name, String key);

    public static native boolean config_set_int(String name, String key, int value);

    public static native Double config_get_double(String name, String key);

    public static native boolean config_set_double(String name, String key, double value);

    public static native String config_get_string(String name, String key);

    public static native boolean config_set_string(String name, String key, String value);

    public static native int config_list_size(String name, String key);

    public static native List config_get_list(String name, String key);

    @Nullable
    public static native Map<String, Map<String, ?>> config_get_map(String name, String key);

    public static native Object config_get_value(String name, String key);

    public static native Object schema_get_value(String name, String key);

    // testing
    public static native boolean simulate_key_sequence(String key_sequence);

    public static native String get_input();

    public static native int get_caret_pos();

    public static native void set_caret_pos(int caret_pos);

    public static native boolean select_candidate(int index);

    public static native boolean select_candidate_on_current_page(int index);

    public static native boolean delete_candidate(int index);

    public static native boolean delete_candidate_on_current_page(int index);

    public static native String get_version();

    public static native String get_librime_version();

    // module
    public static native boolean run_task(String task_name);

    public static native String get_shared_data_dir();

    public static native String get_user_data_dir();

    public static native String get_sync_dir();

    public static native String get_user_id();

    // key_table
    public static native int get_modifier_by_name(String name);

    public static native int get_keycode_by_name(String name);

    // customize setting
    public static native boolean customize_bool(String name, String key, boolean value);

    public static native boolean customize_int(String name, String key, int value);

    public static native boolean customize_double(String name, String key, double value);

    public static native boolean customize_string(String name, String key, String value);

    @Nullable
    public static native List<Map<String, String>> get_available_schema_list();

    @Nullable
    public static native List<Map<String, String>> get_selected_schema_list();

    public static native boolean select_schemas(String[] schema_id_list);

    // opencc
    public static native String get_opencc_version();

    public static native String opencc_convert(String line, String name);

    public static native void opencc_convert_dictionary(
            String inputFileName, String outputFileName, String formatFrom, String formatTo);

    public static native String get_trime_version();
}
