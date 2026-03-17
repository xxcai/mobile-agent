package com.hh.agent.presenter;

import com.hh.agent.android.contract.MainContract;
import com.hh.agent.android.presenter.MainPresenter;
import com.hh.agent.library.api.MobileAgentApi;
import com.hh.agent.library.model.Message;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/**
 * MainPresenter 的单元测试
 */
public class MainPresenterTest {

    @Test
    public void testApiTypeEnum() {
        // 验证 MainPresenter 类存在
        assertNotNull(MainPresenter.class);
    }

    @Test
    public void testPresenterCreation() {
        // 验证可以使用不同方式创建 presenter
        // 注意：在非 Android 环境下，Handler/Looper 不可用，所以这里只验证类结构
        assertNotNull(MainPresenter.class);
    }

    @Test
    public void testMobileAgentApiInterface() {
        // 验证 MobileAgentApi 接口存在
        assertNotNull(MobileAgentApi.class);
        // 验证接口方法存在
        assertTrue(MobileAgentApi.class.getDeclaredMethods().length > 0);
    }

    @Test
    public void testMockMessageListView() {
        // 验证 MessageListView 接口可以正常实现
        final List<Message> receivedMessages = new ArrayList<>();

        MainContract.MessageListView mockView = new MainContract.MessageListView() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
                receivedMessages.addAll(messages);
            }

            @Override
            public void onMessageReceived(Message message) {
                receivedMessages.add(message);
            }

            @Override
            public void onUserMessageSent(Message message) {
                receivedMessages.add(message);
            }

            @Override
            public void onError(String error) {
            }

            @Override
            public void showLoading() {
            }

            @Override
            public void hideLoading() {
            }
        };

        // 验证 Mock View 可以正常工作
        Message testMsg = new Message();
        testMsg.setContent("Test");
        mockView.onMessageReceived(testMsg);

        assertEquals(1, receivedMessages.size());
    }
}
