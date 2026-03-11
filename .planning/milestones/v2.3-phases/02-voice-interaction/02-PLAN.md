---
phase: 02-voice-interaction
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/res/drawable/ic_mic_recording.xml
  - agent-android/src/main/java/com/hh/agent/android/voice/IVoiceRecognizer.java
  - agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java
  - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
autonomous: true
requirements:
  - VT-04
  - VT-05
  - VT-06
must_haves:
  truths:
    - 用户按压语音按钮时，界面显示录音动画提示，语音识别开始
    - 用户讲话过程中，语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
    - 用户松手时，录音动画结束，语音识别停止
  artifacts:
    - path: "agent-android/src/main/res/drawable/ic_mic_recording.xml"
      provides: "录音状态图标 drawable"
      contains: "<vector"
    - path: "agent-android/src/main/java/com/hh/agent/android/voice/IVoiceRecognizer.java"
      provides: "语音识别接口定义"
      contains: "interface IVoiceRecognizer"
    - path: "agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java"
      provides: "Mock 语音识别实现，模拟实时更新"
      contains: "class MockVoiceRecognizer"
    - path: "agent-android/src/main/java/com/hh/agent/android/AgentActivity.java"
      provides: "按压说话逻辑集成"
      contains: "OnTouchListener"
  key_links:
    - from: "AgentActivity.java"
      to: "IVoiceRecognizer"
      via: "实例化 MockVoiceRecognizer"
      pattern: "new MockVoiceRecognizer"
    - from: "AgentActivity.java"
      to: "etMessage"
      via: "setText"
      pattern: "etMessage.setText"
    - from: "AgentActivity.java"
      to: "btnVoice"
      via: "setOnTouchListener"
      pattern: "btnVoice.setOnTouchListener"
---

<objective>
实现按压说话模式：用户按压语音按钮开始录音并识别，松手结束。实时转写结果显示在输入框。

Purpose: 让用户通过按压按钮进行语音输入，系统实时返回转写结果
Output: 完整的按压说话交互流程
</objective>

<execution_context>
@/Users/caixiao/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/milestones/v2.3-phases/02-voice-interaction/02-CONTEXT.md

来自 CONTEXT.md 的关键决策:
1. 按压说话模式: 按住按钮开始，松手结束
2. 录音动画: 图标切换 (ic_mic → ic_mic_recording)
3. 语音识别接口: IVoiceRecognizer 接口抽象
   - Callback: onSuccess(text), onFail(error)
   - 方法: start(Callback), stop()
4. App 层 Mock: MockVoiceRecognizer 实现
   - 模拟多次更新: ["你", "你好", "你好，", ...]
   - UI 层通过独立机制处理实时更新

Phase 1 已完成:
- btnVoice 已添加到 activity_main.xml
- AgentActivity 有 setVoiceButtonVisible() 方法

代码结构:
- btnVoice: ImageButton (已存在于 AgentActivity.java)
- etMessage: EditText (输入框，转写结果写入目标)
- voiceRecognizer: IVoiceRecognizer (语音识别器实例)
- isRecording: boolean (录音状态标志)
</context>

<tasks>

<task type="auto">
  <name>Task 1: 创建录音状态图标 drawable</name>
  <files>agent-android/src/main/res/drawable/ic_mic_recording.xml</files>
  <action>
1. 创建 ic_mic_recording.xml vector drawable (波形/录音中图标):
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <vector xmlns:android="http://schemas.android.com/apk/res/android"
       android:width="24dp"
       android:height="24dp"
       android:viewportWidth="24"
       android:viewportHeight="24">
       <!-- 录音中波形图标 -->
       <path
           android:fillColor="#FF5722"
           android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72H17.3z"/>
       <!-- 录音指示点 -->
       <path
           android:fillColor="#F44336"
           android:pathData="M19,7v6c0,1.1 -0.9,2 -2,2h-1c-1.1,0 -2,-0.9 -2,-2V7"
           android:strokeWidth="2"
           android:strokeColor="#F44336"/>
   </vector>
   ```

2. 如果上述复杂，可使用简单版本 - 带颜色的麦克风:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <vector xmlns:android="http://schemas.android.com/apk/res/android"
       android:width="24dp"
       android:height="24dp"
       android:viewportWidth="24"
       android:viewportHeight="24">
       <path
           android:fillColor="#F44336"
           android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72H17.3z"/>
   </vector>
   ```
   使用红色 (#F44336) 表示录音状态。
  </action>
  <verify>
  <automated>检查 vector drawable 语法正确</automated>
  </verify>
  <done>
  - ic_mic_recording.xml 存在于 drawable 目录
  - 使用红色标识录音状态
  </done>
</task>

<task type="auto">
  <name>Task 2: 创建 IVoiceRecognizer 接口</name>
  <files>agent-android/src/main/java/com/hh/agent/android/voice/IVoiceRecognizer.java</files>
  <action>
1. 在 agent-android 模块创建 voice 包:
   ```java
   package com.hh.agent.android.voice;
   ```

2. 创建 IVoiceRecognizer 接口:
   ```java
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
   ```
  </action>
  <verify>
<automated>编译检查: cd agent-android && ./gradlew compileDebugJavaWithJavac 2>&1 | head -30</automated>
  </verify>
  <done>
  - IVoiceRecognizer 接口定义完成
  - 包含 Callback、start()、stop()、isRecognizing()
  - 编译通过
  </done>
</task>

<task type="auto">
  <name>Task 3: 创建 MockVoiceRecognizer 实现</name>
  <files>agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java</files>
  <action>
1. 创建 MockVoiceRecognizer 实现类:
   ```java
   package com.hh.agent.android.voice;

   import android.os.Handler;
   import android.os.Looper;

   /**
    * Mock 语音识别器实现
    * 用于测试和开发，模拟实时转写更新
    */
   public class MockVoiceRecognizer implements IVoiceRecognizer {

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
   ```
  </action>
  <verify>
<automated>编译检查: cd agent-android && ./gradlew compileDebugJavaWithJavac 2>&1 | head -30</automated>
  </verify>
  <done>
  - MockVoiceRecognizer 实现完成
  - 模拟多次实时更新 (每 300ms)
  - 最终返回完整文本
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: 实现按压说话逻辑</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AgentActivity.java</files>
  <behavior>
    - Test 1: 按下按钮 (ACTION_DOWN) 时，图标切换为 ic_mic_recording，调用 voiceRecognizer.start()
    - Test 2: 手指在按钮上移动 (ACTION_MOVE) 时，保持录音状态
    - Test 3: 松开按钮 (ACTION_UP) 时，图标恢复为 ic_mic，调用 voiceRecognizer.stop()
    - Test 4: 识别结果通过 callback.onSuccess() 返回，实时更新到 etMessage
  </behavior>
  <action>
1. 在 AgentActivity 中添加字段:
   ```java
   private IVoiceRecognizer voiceRecognizer;
   private boolean isRecording = false;
   ```

2. 在初始化时创建 voiceRecognizer 实例:
   ```java
   // 在 btnVoice 初始化后
   voiceRecognizer = new MockVoiceRecognizer();
   ```

3. 添加图标切换方法:
   ```java
   private void updateVoiceButtonState(boolean recording) {
       if (btnVoice != null) {
           btnVoice.setImageResource(recording ? R.drawable.ic_mic_recording : R.drawable.ic_mic);
       }
   }
   ```

4. 替换按钮的 OnClickListener 为 OnTouchListener:
   ```java
   btnVoice.setOnTouchListener((v, event) -> {
       switch (event.getAction()) {
           case MotionEvent.ACTION_DOWN:
               // 按下：开始录音
               if (!isRecording) {
                   isRecording = true;
                   updateVoiceButtonState(true);
                   voiceRecognizer.start(new IVoiceRecognizer.Callback() {
                       @Override
                       public void onSuccess(String text) {
                           runOnUiThread(() -> {
                               if (etMessage != null) {
                                   etMessage.setText(text);
                                   etMessage.setSelection(text.length()); // 光标移到末尾
                               }
                           });
                       }

                       @Override
                       public void onFail(String error) {
                           runOnUiThread(() -> {
                               // 可以显示 toast 或其他提示
                           });
                       }
                   });
               }
               return true;

           case MotionEvent.ACTION_UP:
           case MotionEvent.ACTION_CANCEL:
               // 松开：停止录音
               if (isRecording) {
                   isRecording = false;
                   updateVoiceButtonState(false);
                   voiceRecognizer.stop();
               }
               return true;

           default:
               return false;
       }
   });
   ```

5. 确保移除之前的 OnClickListener（如果有）
  </action>
  <verify>
<automated>编译检查: cd agent-android && ./gradlew compileDebugJavaWithJavac 2>&1 | head -30</automated>
  </verify>
  <done>
  - 按压按钮开始录音 (ACTION_DOWN)
  - 松开按钮停止录音 (ACTION_UP)
  - 图标在录音/非录音状态间切换
  - 转写结果实时更新到 etMessage
  - 编译通过
  </done>
</task>

</tasks>

<verification>
1. 编译: `cd agent-android && ./gradlew compileDebugJavaWithJavac` 无错误
2. 代码审查: 按压说话逻辑正确实现
3. 布局验证: 图标切换正确 (ic_mic ↔ ic_mic_recording)
4. 功能验证: 转写结果写入 etMessage
</verification>

<success_criteria>
- VT-04: 按压按钮显示录音动画提示，开始语音识别
- VT-05: 讲话过程中语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
- VT-06: 松手按钮结束语音识别，动画结束
</success_criteria>

<output>
After completion, create `.planning/milestones/v2.3-phases/02-voice-interaction/02-SUMMARY.md`
</output>
