package com.screenvision.sdk.internal;

import android.graphics.Bitmap;

public final class BitmapResizer {
    private BitmapResizer() {
    }

    public static Bitmap scaleDownIfNeeded(Bitmap source, int maxSidePx) {
        if (source == null || maxSidePx <= 0) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int maxSide = Math.max(width, height);
        if (maxSide <= maxSidePx) {
            return source;
        }
        float scale = maxSidePx / (float) maxSide;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }
}
