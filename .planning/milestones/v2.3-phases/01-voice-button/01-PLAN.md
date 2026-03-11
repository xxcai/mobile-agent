---
phase: 01-voice-button
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/res/layout/activity_main.xml
  - agent-android/src/main/res/drawable/ic_mic.xml
  - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
autonomous: true
requirements:
  - VT-01
  - VT-02
  - VT-03
must_haves:
  truths:
    - 用户可以在输入框右侧看到语音按钮（当语音功能开启时）
    - 按钮顺序为 etMessage → btnVoice → btnSend，符合视觉布局
    - 按钮在 app 关闭语音功能时默认隐藏，开启时显示
  artifacts:
    - path: "agent-android/src/main/res/layout/activity_main.xml"
      provides: "输入区域布局，包含语音按钮"
      contains: "android:id=\"@+id/btnVoice\""
    - path: "agent-android/src/main/res/drawable/ic_mic.xml"
      provides: "麦克风图标 drawable"
      contains: "<vector"
    - path: "agent-android/src/main/java/com/hh/agent/android/AgentActivity.java"
      provides: "语音按钮控制逻辑"
      contains: "btnVoice"
  key_links:
    - from: "AgentActivity.java"
      to: "activity_main.xml"
      via: "findViewById"
      pattern: "btnVoice.*findViewById"
---

<objective>
在聊天界面添加语音按钮，用户可以看到并控制语音功能

Purpose: 为语音输入功能提供 UI 入口
Output: 可见的语音按钮，带显示/隐藏控制
</objective>

<execution_context>
@/Users/caixiao/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/milestones/v2.3-phases/01-voice-button/01-CONTEXT.md

从 CONTEXT.md 的关键决策:
1. 在 activity_main.xml 的 inputContainer 中添加 ImageButton (btnVoice)
2. 位置: etMessage → btnVoice → btnSend
3. 按钮使用麦克风图标
4. 按钮默认隐藏 (android:visibility="gone")
5. 通过 setVisibility() 控制显示/隐藏
6. 尺寸: 48dp x 48dp (与 btnSend 一致)
7. 使用现有的 bg_send_button 背景

当前代码结构:
- inputContainer: LinearLayout (horizontal)
- etMessage: EditText (weight=1)
- btnSend: ImageButton (48dp x 48dp)
</context>

<tasks>

<task type="auto">
  <name>Task 1: 在 activity_main.xml 添加语音按钮</name>
  <files>agent-android/src/main/res/layout/activity_main.xml</files>
  <action>
1. 在 inputContainer 中的 etMessage 和 btnSend 之间添加 ImageButton:
   ```xml
   <!-- 语音按钮 -->
   <ImageButton
       android:id="@+id/btnVoice"
       android:layout_width="48dp"
       android:layout_height="48dp"
       android:layout_marginStart="8dp"
       android:src="@drawable/ic_mic"
       android:background="@drawable/bg_send_button"
       android:contentDescription="语音输入"
       android:scaleType="centerInside"
       android:padding="12dp"
       android:tint="@android:color/white"
       android:visibility="gone" />
   ```

2. 确保按钮位于 etMessage 和 btnSend 之间

3. 设置默认 visibility="gone" (隐藏)
  </action>
  <verify>
  <automated>检查布局文件语法正确</automated>
  </verify>
  <done>
  - btnVoice 存在于 inputContainer 中
  - 位置在 etMessage 和 btnSend 之间
  - 默认隐藏 (visibility="gone")
  - 尺寸 48dp x 48dp
  </done>
</task>

<task type="auto">
  <name>Task 2: 创建麦克风图标 drawable</name>
  <files>agent-android/src/main/res/drawable/ic_mic.xml</files>
  <action>
1. 创建 ic_mic.xml vector drawable:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <vector xmlns:android="http://schemas.android.com/apk/res/android"
       android:width="24dp"
       android:height="24dp"
       android:viewportWidth="24"
       android:viewportHeight="24">
       <path
           android:fillColor="#FFFFFF"
           android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72H17.3z"/>
   </vector>
   ```
  </action>
  <verify>
  <automated>检查 vector drawable 语法正确</automated>
  </verify>
  <done>
  - ic_mic.xml 存在于 drawable 目录
  - 麦克风图标渲染正确
  </done>
</task>

<task type="auto">
  <name>Task 3: 在 AgentActivity 中添加语音按钮控制</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AgentActivity.java</files>
  <action>
1. 在 AgentActivity 中添加字段:
   ```java
   private ImageButton btnVoice;
   ```

2. 在 onCreate 或初始化方法中找到按钮:
   ```java
   btnVoice = findViewById(R.id.btnVoice);
   ```

3. 添加公开方法控制显示/隐藏:
   ```java
   /**
    * 设置语音按钮是否可见
    * @param visible true 显示，false 隐藏
    */
   public void setVoiceButtonVisible(boolean visible) {
       if (btnVoice != null) {
           btnVoice.setVisibility(visible ? View.VISIBLE : View.GONE);
       }
   }
   ```

4. (可选) 设置点击监听器为后续 Phase 2 预留:
   ```java
   if (btnVoice != null) {
       btnVoice.setOnClickListener(v -> {
           // Phase 2: 开始录音
       });
   }
   ```
  </action>
  <verify>
  <automated>编译检查: cd agent-android && ./gradlew compileDebugJavaWithJavac 2>&1 | head -30</automated>
  </verify>
  <done>
  - btnVoice 字段已添加
  - setVoiceButtonVisible(true/false) 方法可用
  - 编译无错误
  </done>
</task>

</tasks>

<verification>
1. 编译: `cd agent-android && ./gradlew compileDebugJavaWithJavac` 无错误
2. 代码审查: btnVoice 正确添加，visibility 控制正常
3. 布局验证: 按钮顺序为 etMessage → btnVoice → btnSend
</verification>

<success_criteria>
- VT-01: 在输入框 (etMessage) 右侧添加语音按钮 (btnVoice)
- VT-02: 按钮位置顺序: etMessage → btnVoice → btnSend
- VT-03: 按钮仅在 app 开启语音功能时显示，默认隐藏
</success_criteria>

<output>
After completion, create `.planning/milestones/v2.3-phases/01-voice-button/01-SUMMARY.md`
