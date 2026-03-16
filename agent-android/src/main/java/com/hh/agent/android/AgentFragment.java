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
import com.hh.agent.android.ui.MessageAdapter;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.library.model.Message;

import java.util.List;

/**
 * Agent Fragment - 从 AgentActivity 抽取的 UI 逻辑
 * 用于嵌入到 ContainerActivity 中显示
 */
public class AgentFragment extends Fragment implements MainContract.View {

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
    private boolean isRecording = false;
    private boolean permissionGranted = false;

    // 用于累积流式文本内容
    private StringBuilder streamTextBuffer = new StringBuilder();

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
        presenter.attachView(this);

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
            String content = etMessage.getText().toString().trim();
            if (!content.isEmpty()) {
                presenter.sendMessage(content);
                etMessage.setText("");
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
    public void onStreamTextDelta(String textDelta) {
        // 累积文本内容（替换模式）
        streamTextBuffer.setLength(0);
        streamTextBuffer.append(textDelta);

        // 更新 thinking 消息内容
        adapter.updateThinkingMessage(streamTextBuffer.toString());

        // 强制刷新 RecyclerView
        rvMessages.invalidate();
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamToolUse(String id, String name, String argumentsJson) {
        Log.d("AgentFragment", "onStreamToolUse: id=" + id + ", name=" + name + ", args=" + argumentsJson);
        // 添加工具调用消息
        adapter.addToolUseMessage(name, argumentsJson);

        // 自动滚动到最新消息
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onStreamToolResult(String id, String result) {
        Log.d("AgentFragment", "onStreamToolResult: id=" + id + ", result=" + result);
        // 添加工具结果消息
        // 从消息列表中获取最后一个 tool_use 消息的工具名称
        String toolName = getLastToolName();
        adapter.addToolResultMessage(toolName, result);

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
    public void onStreamMessageEnd(String finishReason) {
        Log.d("AgentFragment", "onStreamMessageEnd: finishReason=" + finishReason + ", streamTextBuffer=" + streamTextBuffer.toString());

        // 如果 finish_reason 是 tool_calls，LLM 还要继续执行工具，不删除 thinking
        if ("tool_calls".equals(finishReason)) {
            Log.d("AgentFragment", "onStreamMessageEnd: tool_calls, keeping thinking message");
            return;
        }

        // finish_reason 是 stop 时，才删除 thinking 并添加最终响应
        // 1. 删除 thinking 消息
        hideThinking();

        // 2. 创建新的 AI 助手消息
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(streamTextBuffer.toString());
        assistantMessage.setTimestamp(System.currentTimeMillis());

        Log.d("AgentFragment", "onStreamMessageEnd: adding assistant message, content=" + assistantMessage.getContent());
        // 3. 添加最终消息
        onMessageReceived(assistantMessage);

        // 清除累积的文本内容
        streamTextBuffer.setLength(0);
    }

    @Override
    public void onStreamError(String errorCode, String errorMessage) {
        // 流式错误 - 显示错误信息
        if (getContext() != null) {
            Toast.makeText(getContext(), "Stream error: " + errorMessage, Toast.LENGTH_SHORT).show();
        }
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
