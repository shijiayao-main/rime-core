package com.jiaoay.rime.ime.symbol;

// Tab是滑动键盘顶部的标签按钮（包含返回键）。
// 为了公用候选栏的皮肤参数以及外观，保持了和普通键盘布局相似的代码。此类相当于原键盘布局的Rime.RimeCandidate

import android.graphics.Rect;

import com.jiaoay.rime.ime.enums.KeyCommandType;
import com.jiaoay.rime.ime.enums.SymbolKeyboardType;

public class TabTag {
    String text; // text for tab
    Rect geometry; // position and size info of tab
    String comment; // not used
    SymbolKeyboardType type; //
    KeyCommandType command; // command for tag without key

    public TabTag(String text, SymbolKeyboardType type, String comment) {
        this.text = text;
        this.comment = comment;
        this.type = type;
    }

    public TabTag(String text, SymbolKeyboardType type, KeyCommandType command) {
        this.text = text;
        this.command = command;
        this.type = type;
    }
}
