package com.hh.agent.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化视图
        initViews(view);

        // 仅在已注入语音识别器时显示语音按钮
        if (VoiceRecognizerHolder.getInstance().getRecognizer() != null) {
            setVoiceButtonVisible(true);
        }

        // 加载 native agent 配置
        if (getActivity() != null) {
            com.hh.agent.android.presenter.NativeMobileAgentApiAdapter.loadConfigFromAssets(getActivity());
        }

        // 初始化 Presenter，使用 Native C++ Agent
        presenter = new MainPresenter(getActivity(), "native:default");
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

        if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(getActivity(),
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
                etMessage.setText(""); // 清空输入框
            }
        });
    }

    /**
     * 设置语音按钮是否可见
     * @param visible true 显示，false 隐藏
     */
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
                            // 仅请求权限，不开始录音，等待用户再次操作
                            if (getActivity() != null) {
                                ActivityCompat.requestPermissions(getActivity(),
                                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                                        REQUEST_RECORD_AUDIO_PERMISSION);
                            }
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
                                            etMessage.setSelection(text.length()); // 光标移到末尾
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
     * @param recording true 表示录音中，false 表示空闲
     */
    private void updateVoiceButtonState(boolean recording) {
        if (btnVoice != null) {
            btnVoice.setImageResource(recording ? R.drawable.ic_mic_recording : R.drawable.ic_mic);
        }
        // 显示/隐藏录音遮罩
        if (voiceRecordingOverlay != null) {
            voiceRecordingOverlay.setVisibility(recording ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onMessagesLoaded(List<Message> messages) {
        adapter.setMessages(messages);
        // 滚动到底部
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onMessageReceived(Message message) {
        adapter.addMessage(message);
        // 滚动到底部
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onUserMessageSent(Message message) {
        adapter.addMessage(message);
        // 滚动到底部
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    @Override
    public void onError(String error) {
        if (getContext() != null) {
            Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
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
        adapter.removeThinkingMessage();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (presenter != null) {
            presenter.detachView();
            presenter.destroy();
        }
    }
}
