# 语音识别器注入指南

## 概述

`VoiceRecognizerHolder` 是一个单例类，用于管理语音识别器实例。它允许上层 app 通过 Setter 注入自定义的语音识别实现，从而实现语音识别能力的可替换性。

## 使用方式

### 1. 注入自定义语音识别实现

在 Application 或初始化时注入你的语音识别实现：

```java
// 在 Application 或初始化时注入
VoiceRecognizerHolder.getInstance().setRecognizer(new YourVoiceRecognizer());
```

### 2. 使用 Mock 实现进行开发测试

app 模块提供了 `MockVoiceRecognizer` 类，用于开发测试：

```java
// 导入 Mock 实现
import com.hh.agent.app.voice.MockVoiceRecognizer;

// 注入 Mock 实现
VoiceRecognizerHolder.getInstance().setRecognizer(new MockVoiceRecognizer());
```

### 3. 使用真实的语音识别 SDK

你也可以注入真实的语音识别 SDK 实现，例如：

```java
// 注入讯飞、百度或其他语音识别 SDK
VoiceRecognizerHolder.getInstance().setRecognizer(new YourRealVoiceRecognizer());
```

## IVoiceRecognizer 接口

你的实现需要实现 `IVoiceRecognizer` 接口：

```java
public interface IVoiceRecognizer {
    interface Callback {
        void onSuccess(String text);  // 识别成功
        void onFail(String error);    // 识别失败
    }

    void start(Callback callback);    // 开始语音识别
    void stop();                      // 停止语音识别
    boolean isRecognizing();          // 检查是否正在识别
}
```

## 完整示例

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 注入语音识别实现
        // 开发阶段使用 Mock，实现生产环境替换为真实 SDK
        VoiceRecognizerHolder.getInstance().setRecognizer(new MockVoiceRecognizer());
    }
}
```

## 注意事项

1. **必须在 AgentActivity 初始化之前注入**：建议在 Application 的 `onCreate` 方法中完成注入
2. **保持单例**：VoiceRecognizerHolder 是单例，整个应用生命周期内只需注入一次
3. **null 检查**：AgentActivity 会检查 `getRecognizer()` 是否为 null，如果未注入则语音功能不可用
