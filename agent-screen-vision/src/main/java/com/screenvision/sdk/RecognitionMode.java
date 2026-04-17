package com.screenvision.sdk;

public enum RecognitionMode {
    TEXT_ONLY,
    UI_ONLY,
    TEXT_AND_UI;

    public boolean includesText() {
        return this == TEXT_ONLY || this == TEXT_AND_UI;
    }

    public boolean includesUi() {
        return this == UI_ONLY || this == TEXT_AND_UI;
    }
}
