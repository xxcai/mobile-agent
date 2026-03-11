package com.hh.agent.android.voice;

/**
 * 语音识别器接口
 * 用于抽象语音转文字能力，上层 app 可注入具体实现
 */
public interface IVoiceRecognizer {

    /**
     * 识别结果回调
     */
    interface Callback {
        /**
         * 识别成功
         * @param text 识别到的文本（完整文本）
         */
        void onSuccess(String text);

        /**
         * 识别失败
         * @param error 错误信息
         */
        void onFail(String error);
    }

    /**
     * 开始语音识别
     * @param callback 识别结果回调
     */
    void start(Callback callback);

    /**
     * 停止语音识别
     */
    void stop();

    /**
     * 检查是否正在识别
     * @return true 表示正在识别
     */
    boolean isRecognizing();
}
