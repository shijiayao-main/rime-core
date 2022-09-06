package com.jiaoay.rime.core;


/**
 * Rime環境，包括 {@link RimeComposition 編碼區} 、{@link RimeMenu 候選區}
 */
public class RimeContext {
    int data_size;
    // v0.9
    RimeComposition composition;
    RimeMenu menu;
    // v0.9.2
    String commit_text_preview;
    String[] select_labels;

    public int size() {
        if (menu == null) return 0;
        return menu.num_candidates;
    }

    public RimeCandidate[] getCandidates() {
        return size() == 0 ? null : menu.candidates;
    }
}
