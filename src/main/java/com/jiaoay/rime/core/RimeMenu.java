package com.jiaoay.rime.core;

/**
 * Rime候選區，包含多個{@link RimeCandidate 候選項}
 */
public class RimeMenu {
    int page_size;
    int page_no;
    boolean is_last_page;
    int highlighted_candidate_index;
    int num_candidates;
    RimeCandidate[] candidates;
    String select_keys;
}