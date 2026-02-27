package com.hh.agent.lib;

public class NativeLib {

    static {
        System.loadLibrary("native-lib");
    }

    public native String stringFromJNI();

    public native int add(int a, int b);

    public native String getMessage();
}
