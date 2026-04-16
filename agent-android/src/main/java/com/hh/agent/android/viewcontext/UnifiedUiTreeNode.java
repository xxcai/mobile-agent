package com.hh.agent.android.viewcontext;

public final class UnifiedUiTreeNode {
    public final String tag;
    public final String text;
    public final String role;
    public final int index;

    public UnifiedUiTreeNode(String tag, String text, String role, int index) {
        this.tag = tag;
        this.text = text;
        this.role = role;
        this.index = index;
    }
}
