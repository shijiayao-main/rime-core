package com.jiaoay.rime.core;

/**
 * Rime候選項
 */
public class RimeCandidate {
    public String text;
    public String comment;

    public RimeCandidate(String text, String comment) {
        this.text = text;
        this.comment = comment;
    }

    public RimeCandidate() {
    }
}