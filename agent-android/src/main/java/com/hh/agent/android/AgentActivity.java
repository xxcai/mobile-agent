package com.hh.agent.android;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.voice.IVoiceRecognizer;
import com.hh.agent.android.voice.VoiceRecognizerHolder;
import com.hh.agent.library.model.Message;
import com.hh.agent.android.presenter.MainPresenter;
import com.hh.agent.android.presenter.NativeMobileAgentApiAdapter;
import com.hh.agent.android.ui.MessageAdapter;

import java.util.List;

/**
 * 主界面 Activity - 原生 Java 实现
 */
public class AgentActivity extends AppCompatActivity implements MainContract.View {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnVoice;
    private Toolbar toolbar;
    private MessageAdapter adapter;
    private MainPresenter presenter;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        initViews();

        // 显示语音按钮
        setVoiceButtonVisible(true);

        // 加载 native agent 配置
        NativeMobileAgentApiAdapter.loadConfigFromAssets(this);

        // 初始化 Presenter，使用 Native C++ Agent
        presenter = new MainPresenter(this, "native:default");
        presenter.attachView(this);

        // 加载历史消息
        presenter.loadMessages();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);

        // 设置语音按钮监听器
        setupVoiceButtonListener();

        // 设置 Toolbar
        setSupportActionBar(toolbar);

        // 设置 RecyclerView
        adapter = new MessageAdapter(this);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
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
                        isRecording = true;
                        updateVoiceButtonState(true);
                        recognizer.start(new IVoiceRecognizer.Callback() {
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
                                    Toast.makeText(AgentActivity.this, error, Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLoading() {
        btnSend.setEnabled(false);
    }

    @Override
    public void hideLoading() {
        btnSend.setEnabled(true);
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
    protected void onDestroy() {
        super.onDestroy();
        presenter.detachView();
        presenter.destroy();
    }
}
