package com.hh.agent.android.voice;

import android.util.Log;

/**
 * 语音识别器持有者单例类
 * 用于管理语音识别器实例，支持 app 层通过 Setter 注入自定义实现
 *
 * 使用方式：
 * 1. 在 Application 或初始化时注入实现：VoiceRecognizerHolder.getInstance().setRecognizer(new YourVoiceRecognizer());
 * 2. 在需要使用的地方获取：VoiceRecognizerHolder.getInstance().getRecognizer()
 */
public class VoiceRecognizerHolder {

    private static final String TAG = "VoiceRecognizerHolder";

    private static volatile VoiceRecognizerHolder instance;

    private IVoiceRecognizer recognizer;

    /**
     * 私有构造函数，单例模式
     */
    private VoiceRecognizerHolder() {
    }

    /**
     * 获取单例实例（双重检查锁定，线程安全）
     *
     * @return VoiceRecognizerHolder 单例实例
     */
    public static VoiceRecognizerHolder getInstance() {
        if (instance == null) {
            synchronized (VoiceRecognizerHolder.class) {
                if (instance == null) {
                    instance = new VoiceRecognizerHolder();
                }
            }
        }
        return instance;
    }

    /**
     * 设置语音识别器实现
     * 由 app 层调用，注入自定义的语音识别实现
     *
     * @param recognizer 语音识别器实现
     */
    public void setRecognizer(IVoiceRecognizer recognizer) {
        this.recognizer = recognizer;
        Log.d(TAG, "Voice recognizer set: " + (recognizer != null ? recognizer.getClass().getSimpleName() : "null"));
    }

    /**
     * 获取语音识别器
     * 如果未设置则返回 null，由 app 层主动注入
     *
     * @return 语音识别器实例，可能为 null
     */
    public IVoiceRecognizer getRecognizer() {
        return recognizer;
    }
}
