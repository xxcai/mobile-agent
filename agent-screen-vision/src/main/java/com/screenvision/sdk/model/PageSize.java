package com.screenvision.sdk.model;

public final class PageSize {
    private final int width;
    private final int height;

    public PageSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

