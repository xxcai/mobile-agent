package com.hh.agent.android;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.MainPresenter;
import com.hh.agent.android.presenter.StreamingManager;
import com.hh.agent.android.ui.MessageAdapter;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.ToolCall;

import java.util.List;

/**
 * Agent Fragment - 从 AgentActivity 抽取的 UI 逻辑
 * 用于嵌入到 ContainerActivity 中显示
 */
public class AgentFragment extends Fragment implements MainContract.MessageListView, MainContract.StreamingView {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static boolean sPermissionGranted = false;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnVoice;
    private androidx.appcompat.widget.Toolbar toolbar;
    private View voiceRecordingOverlay;
    private MessageAdapter adapter;
    private MainPresenter presenter;
    private StreamingManager streamingManager;
    private boolean isRecording = false;
    private boolean permissionGranted = false;
    // 当前正在更新的 response 消息，用于流式增量更新
    private Message currentResponseMessage = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        // 从静态变量恢复权限状态
        permissionGranted = sPermissionGranted;

        // 检查并初始化权限状态（仅当未授权时）
        if (!permissionGranted) {
            checkAndRequestAudioPermission();
        }

        // 仅在已注入语音识别器时显示语音按钮
        if (VoiceRecognizerHolder.getInstance().getRecognizer() != null) {
            setVoiceButtonVisible(true);
        }

        // 加载 native agent 配置
        if (getActivity() != null) {
            com.hh.agent.android.presenter.NativeMobileAgentApiAdapter.loadConfigFromAssets(getActivity());
        }

        // 初始化 Presenter，使用单例模式
        presenter = MainPresenter.getInstance();
        presenter.attachView(this, this);

        // 初始化 StreamingManager
        streamingManager = new StreamingManager(presenter.getMobileAgentApi());

        // 加载历史消息
        presenter.loadMessages();
    }

    /**
     * 检查并请求录音权限
     */
    private void checkAndRequestAudioPermission() {
        if (getActivity() == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
            sPermissionGranted = true;
        } else {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        rvMessages = view.findViewById(R.id.rvMessages);
        etMessage = view.findViewById(R.id.etMessage);
        btnSend = view.findViewById(R.id.btnSend);
        btnVoice = view.findViewById(R.id.btnVoice);
        voiceRecordingOverlay = view.findViewById(R.id.voiceRecordingOverlay);

        // 设置语音按钮监听器
        setupVoiceButtonListener();

        // 设置 Toolbar
        if (getActivity() != null) {
            ((androidx.appcompat.app.AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        }

        // 设置 RecyclerView
        adapter = new MessageAdapter(getContext());
        rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMessages.setAdapter(adapter);

        // 设置发送按钮点击事件
        btnSend.setOnClickListener(v -> {
            if (streamingManager.isStreaming()) {
                // 取消流式响应
                presenter.cancelStream();
                // 清除 AI 相关的中间消息（thinking, tool_use, tool_result）
                adapter.removeThinkingMessage();
                adapter.removeAiMessages();
                // 重置状态（按钮恢复，用户输入保留在 etMessage）
                resetStreamingState();
            } else {
                // 发送消息
                String content = etMessage.getText().toString().trim();
                if (!content.isEmpty()) {
                    presenter.sendMessage(content);
                    etMessage.setText("");
                }
            }
        });
    }

    public void setVoiceButtonVisible(boolean visible) {
        if (btnVoice != null) {
            btnVoice.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 设置语音按钮按压监听器（按压说话模式）
     */
    private void setupVoiceButtonListener() {
        if (btnVoice == null) {
            return;
        }

        btnVoice.setOnTouchListener((v, event) -> {
            IVoiceRecognizer recognizer = VoiceRecognizerHolder.getInstance().getRecognizer();
            if (recognizer == null) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 按下：开始录音
                    if (!isRecording) {
                        // 检查录音权限
                        if (!permissionGranted) {
                            requestPermissions(
                                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                                    REQUEST_RECORD_AUDIO_PERMISSION);
                            return true;
                        }
                        isRecording = true;
                        updateVoiceButtonState(true);
                        recognizer.start(new IVoiceRecognizer.Callback() {
                            @Override
                            public void onSuccess(String text) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (etMessage != null) {
                                            etMessage.setText(text);
                                            etMessage.setSelection(text.length());
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onFail(String error) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
                                    });
                                }
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
                        recognizer.stop();
                    }
                    return true;

                default:
                    return false;
            }
        });
    }

    /**
     * 更新语音按钮状态
     */
    private void updateVoiceButtonState(boolean recording) {
        if (btnVoice != null) {
            btnVoice.setImageResource(recording ? R.drawable.ic_mic_recording : R.drawable.ic_mic);
        }
        if (voiceRecordingOverlay != null) {
            voiceRecordingOverlay.setVisibility(recording ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onMessagesLoaded(List<Message> messages) {
        adapter.setMessages(messages);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onMessageReceived(Message message) {
        Log.d("AgentFragment", "onMessageReceived: role=" + message.getRole() + ", content=" + message.getContent());
        adapter.addMessage(message);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onUserMessageSent(Message message) {
        adapter.addMessage(message);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onError(String error) {
        if (getContext() != null) {
            Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
        }
    }

    // 流式回调方法实现
    @Override
    public void onStreamMessageUpdate(Message message) {
        Log.d("AgentFragment", "onStreamMessageUpdate: message=" + message);
        // 第一次收到响应时，创建 response 消息
        if (currentResponseMessage == null) {
            currentResponseMessage = message;
            adapter.addResponseMessage(message);
        } else {
            // 更新现有消息
            currentResponseMessage = message;
            adapter.updateResponseMessage(message);
        }

        // 强制刷新 RecyclerView
        rvMessages.invalidate();
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamTextDelta(String textDelta) {
        // 从 adapter 获取当前 thinking 消息内容并追加新文本
        String currentContent = adapter.getThinkingMessageContent();
        String newContent = (currentContent != null ? currentContent : "") + textDelta;
        Log.d("AgentFragment", "onStreamTextDelta: new='" + textDelta + "', accumulated='" + newContent + "'");

        // 更新 thinking 消息内容
        adapter.updateThinkingMessage(newContent);

        // 强制刷新 RecyclerView
        rvMessages.invalidate();
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamThinkDelta(String textDelta) {
        // 第一次收到响应时，创建 response 消息
        if (currentResponseMessage == null) {
            currentResponseMessage = new Message();
            currentResponseMessage.setRole("response");
            currentResponseMessage.setTimestamp(System.currentTimeMillis());
            adapter.addMessage(currentResponseMessage);
            Log.d("AgentFragment", "onStreamThinkDelta: created response message");
        }

        // 获取当前 think 内容并追加
        String currentContent = adapter.getThinkingMessageContent();
        String newContent = (currentContent != null ? currentContent : "") + textDelta;
        Log.d("AgentFragment", "onStreamThinkDelta: new='" + textDelta + "', accumulated='" + newContent + "'");

        // 更新 response 消息的 thinkContent 字段
        currentResponseMessage.setThinkContent(newContent);
        adapter.notifyDataSetChanged();

        // 刷新 RecyclerView
        rvMessages.invalidate();
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamContentDelta(String textDelta) {
        // 第一次收到响应时，创建 response 消息
        if (currentResponseMessage == null) {
            currentResponseMessage = new Message();
            currentResponseMessage.setRole("response");
            currentResponseMessage.setTimestamp(System.currentTimeMillis());
            adapter.addMessage(currentResponseMessage);
            Log.d("AgentFragment", "onStreamContentDelta: created response message");
        }

        // 获取当前正文内容并追加
        String currentContent = adapter.getContentMessageContent();
        String newContent = (currentContent != null ? currentContent : "") + textDelta;
        Log.d("AgentFragment", "onStreamContentDelta: new='" + textDelta + "', accumulated='" + newContent + "'");

        // 更新 response 消息的 content 字段
        currentResponseMessage.setContent(newContent);
        adapter.notifyDataSetChanged();

        // 刷新 RecyclerView
        rvMessages.invalidate();
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamToolUse(String id, String name, String argumentsJson) {
        Log.d("AgentFragment", "onStreamToolUse: id=" + id + ", name=" + name + ", args=" + argumentsJson);

        // 如果还没有 response 消息，先创建
        if (currentResponseMessage == null) {
            currentResponseMessage = new Message();
            currentResponseMessage.setRole("response");
            currentResponseMessage.setTimestamp(System.currentTimeMillis());
            adapter.addMessage(currentResponseMessage);
            Log.d("AgentFragment", "onStreamToolUse: created response message");
        }

        // 添加工具到 response 消息
        ToolCall toolCall = new ToolCall(id, name);
        toolCall.setArguments(argumentsJson);
        currentResponseMessage.addToolCall(toolCall);
        adapter.notifyDataSetChanged();

        // 自动滚动到最新消息
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamToolResult(String id, String result) {
        Log.d("AgentFragment", "onStreamToolResult: id=" + id + ", result=" + result);
        // 更新工具状态为已完成
        adapter.updateToolStatus(id, "completed");

        // 工具结果返回后，LLM 即将开始下一次响应
        // 清除旧的 thinking 消息，准备显示新的 thinking
        hideThinking();
        // 显示新的 thinking 消息
        showThinking();

        // 自动滚动到最新消息
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    /**
     * 获取最后一个工具调用的名称
     */
    private String getLastToolName() {
        for (int i = adapter.getItemCount() - 1; i >= 0; i--) {
            Message msg = adapter.getMessageAt(i);
            if (msg != null && "tool_use".equals(msg.getRole())) {
                return msg.getName();
            }
        }
        return "Tool";
    }

    /**
     * 删除发送中状态消息
     */
    private void removeSendingMessage() {
        adapter.removeSendingMessage();
    }

    @Override
    public void onStreamMessageEnd(Message message, String finishReason) {
        Log.d("AgentFragment", "onStreamMessageEnd: finishReason=" + finishReason + ", message=" + message);

        // 更新最终消息
        if (message != null) {
            if (currentResponseMessage == null) {
                adapter.addResponseMessage(message);
            } else {
                adapter.updateResponseMessage(message);
            }
        }

        // 清理当前响应消息标记
        currentResponseMessage = null;

        // 状态转换：正在响应 -> 历史响应
        if ("stop".equals(finishReason)) {
            // 1. 删除 thinking 消息（不再需要显示）
            adapter.removeThinkingMessage();

            // 2. 更新 response 消息，隐藏工具区和 think 区
            // 清除 toolCalls 列表会隐藏工具区
            adapter.clearToolCallsInResponse();

            // 刷新 RecyclerView 显示更新后的卡片
            rvMessages.scrollToPosition(adapter.getItemCount() - 1);

            Log.d("AgentFragment", "onStreamMessageEnd: stop, updated response card to history state");
            return;
        }

        // 如果 finish_reason 是 tool_calls，LLM 还要继续执行工具，不删除 thinking
        if ("tool_calls".equals(finishReason)) {
            Log.d("AgentFragment", "onStreamMessageEnd: tool_calls, keeping thinking message");
            return;
        }

        // 检查是否为错误类型的 finish_reason
        String[] errorFinishReasons = {"content_filter", "max_tokens", "length", "model_overloaded", "rate_limit", "error", "http_error", "parse_error", "cancel"};
        for (String errorType : errorFinishReasons) {
            if (errorType.equals(finishReason)) {
                Log.d("AgentFragment", "onStreamMessageEnd: error finish_reason=" + finishReason);
                // 显示错误消息
                adapter.addErrorMessage(finishReason, "API 响应被截断或内容不符合要求");
                // 隐藏 thinking 消息
                hideThinking();
                // 清除 AI 消息
                adapter.removeAiMessages();
                return;
            }
        }

        // 默认按异常处理
        Log.d("AgentFragment", "onStreamMessageEnd: unknown finishReason=" + finishReason + ", treating as error");
        adapter.removeThinkingMessage();
        adapter.removeAiMessages();
    }

    @Override
    public void onStreamError(String errorCode, String errorMessage) {
        // 清理当前响应消息标记
        currentResponseMessage = null;

        // 流式错误 - 显示错误消息到消息列表
        adapter.addErrorMessage(errorCode, errorMessage);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);

        // 清除 AI 相关消息（thinking, tool_use, tool_result, assistant）
        adapter.removeThinkingMessage();
        adapter.removeAiMessages();

        // 重置流式状态和按钮
        resetStreamingState();
    }

    @Override
    public void showLoading() {
        if (btnSend != null) {
            btnSend.setEnabled(false);
        }
    }

    @Override
    public void hideLoading() {
        if (btnSend != null) {
            btnSend.setEnabled(true);
        }
    }

    @Override
    public void showThinking() {
        // 切换按钮为取消状态
        btnSend.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnSend.setContentDescription(getString(R.string.cancel_button));

        Message thinkingMsg = new Message();
        thinkingMsg.setRole("thinking");
        thinkingMsg.setContent("MobileAgent 正在思考...");
        thinkingMsg.setTimestamp(System.currentTimeMillis());
        adapter.addMessage(thinkingMsg);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void hideThinking() {
        Log.d("AgentFragment", "hideThinking: removing thinking message");
        adapter.removeThinkingMessage();
        // 恢复按钮为发送状态
        resetSendButton();
    }

    /**
     * 重置发送按钮为正常状态
     */
    private void resetSendButton() {
        btnSend.setEnabled(true);
        btnSend.setImageResource(android.R.drawable.ic_menu_send);
        btnSend.setContentDescription(getString(R.string.send_button));
    }

    /**
     * 重置流式响应状态
     */
    private void resetStreamingState() {
        resetSendButton();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionGranted = true;
                sPermissionGranted = true;
                Toast.makeText(getContext(), "录音权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (presenter != null) {
            presenter.detachView();
            // presenter.destroy();   // 移除：不应销毁单例，应保留状态
        }
    }
}
