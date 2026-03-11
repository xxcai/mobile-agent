package com.hh.agent.voice;

import android.os.Handler;
import android.os.Looper;

/**
 * Mock 语音识别器实现
 * 用于测试和开发，模拟实时转写更新
 */
public class MockVoiceRecognizer implements com.hh.agent.android.voice.IVoiceRecognizer {

    private static final String[] MOCK_RESULTS = {
        "你", "你好", "你好，", "你好，今", "你好，今天",
        "你好，今天天", "你好，今天天气", "你好，今天天气很",
        "你好，今天天气很好", "你好，今天天气很好。"
    };

    private static final long UPDATE_INTERVAL = 300; // 毫秒

    private Callback currentCallback;
    private boolean isRecognizing = false;
    private Handler handler;
    private int currentIndex = 0;

    public MockVoiceRecognizer() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void start(Callback callback) {
        if (isRecognizing) {
            return;
        }

        this.currentCallback = callback;
        this.isRecognizing = true;
        this.currentIndex = 0;

        // 开始模拟实时更新
        startMockUpdates();
    }

    @Override
    public void stop() {
        isRecognizing = false;
        handler.removeCallbacksAndMessages(null);
        currentCallback = null;
    }

    @Override
    public boolean isRecognizing() {
        return isRecognizing;
    }

    private void startMockUpdates() {
        if (!isRecognizing || currentIndex >= MOCK_RESULTS.length) {
            // 最终结果
            if (isRecognizing && currentCallback != null) {
                currentCallback.onSuccess(MOCK_RESULTS[MOCK_RESULTS.length - 1]);
            }
            stop();
            return;
        }

        // 发送中间结果（模拟实时转写）
        if (currentCallback != null) {
            currentCallback.onSuccess(MOCK_RESULTS[currentIndex]);
        }

        currentIndex++;

        // 延迟后继续
        handler.postDelayed(this::startMockUpdates, UPDATE_INTERVAL);
    }
}
