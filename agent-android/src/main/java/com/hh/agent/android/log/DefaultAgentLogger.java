package com.hh.agent.android.log;

import android.util.Log;

/**
 * 默认 Agent logger，直接转发到 Android Log。
 */
public final class DefaultAgentLogger implements AgentLogger {

    @Override
    public void d(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void i(String tag, String message) {
        Log.i(tag, message);
    }

    @Override
    public void w(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void e(String tag, String message) {
        Log.e(tag, message);
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
    }
}
