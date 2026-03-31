package com.screenvision.sdk.internal.mnn;

import android.util.Log;

public final class MnnUiClassifier implements AutoCloseable {
    public static final int FEATURE_DIM = 21;
    public static final int OUTPUT_DIM = 5;
    private static final String TAG = "ScreenVisionMnn";
    private static final boolean LIBRARIES_LOADED = loadLibraries();

    private long nativeHandle;

    public MnnUiClassifier(int numThreads) {
        nativeHandle = LIBRARIES_LOADED ? nativeCreate(Math.max(1, numThreads)) : 0L;
    }

    public boolean isReady() {
        return nativeHandle != 0L;
    }

    public float[] predict(float[] featureVector) {
        if (nativeHandle == 0L || featureVector == null || featureVector.length != FEATURE_DIM) {
            return null;
        }
        return nativePredict(nativeHandle, featureVector);
    }

    @Override
    public void close() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle);
            nativeHandle = 0L;
        }
    }

    private static boolean loadLibraries() {
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("MNN");
            System.loadLibrary("MNN_Express");
            System.loadLibrary("screenvision_mnn");
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to load MNN native libraries", throwable);
            return false;
        }
    }

    private static native long nativeCreate(int numThreads);

    private static native float[] nativePredict(long handle, float[] featureVector);

    private static native void nativeRelease(long handle);
}