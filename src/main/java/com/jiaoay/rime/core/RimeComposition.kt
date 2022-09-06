package com.jiaoay.rime.core;

/**
 * Rime編碼區
 */

public class RimeComposition {
    int length;
    int cursor_pos;
    int sel_start;
    int sel_end;
    String preedit;
    byte[] bytes;

    public String getText() {
        if (length == 0) return "";
        bytes = preedit.getBytes();
        return preedit;
    }

    public int getStart() {
        if (length == 0) return 0;
        return new String(bytes, 0, sel_start).length();
    }

    public int getEnd() {
        if (length == 0) return 0;
        return new String(bytes, 0, sel_end).length();
    }
}