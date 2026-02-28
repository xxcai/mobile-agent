package com.hh.agent.contract;

import com.hh.agent.lib.model.Message;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/**
 * MainContract 的单元测试
 */
public class MainContractTest {

    @Test
    public void testViewInterfaceExists() {
        // 验证 View 接口存在
        assertNotNull(MainContract.View.class);
    }

    @Test
    public void testPresenterInterfaceExists() {
        // 验证 Presenter 接口存在
        assertNotNull(MainContract.Presenter.class);
    }

    @Test
    public void testMockView() {
        // 测试实现一个简单的 Mock View
        final List<Message> receivedMessages = new ArrayList<>();

        MainContract.View mockView = new MainContract.View() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
                receivedMessages.addAll(messages);
            }

            @Override
            public void onMessageReceived(Message message) {
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
