package com.hh.agent;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.contract.MainContract;
import com.hh.agent.lib.model.Message;
import com.hh.agent.presenter.MainPresenter;
import com.hh.agent.ui.MessageAdapter;

import java.util.List;

/**
 * 主界面 Activity
 */
public class MainActivity extends AppCompatActivity implements MainContract.View {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private Toolbar toolbar;
    private MessageAdapter adapter;
    private MainPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        initViews();

        // 初始化 Presenter
        presenter = new MainPresenter();
        presenter.attachView(this);

        // 加载历史消息
        presenter.loadMessages();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

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
        thinkingMsg.setContent("Nanobot 正在思考...");
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
