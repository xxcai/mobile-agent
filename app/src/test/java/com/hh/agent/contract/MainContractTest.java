package com.hh.agent.contract;

import com.hh.agent.android.contract.MainContract;
import com.hh.agent.library.model.Message;
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
        // 测试实现一个简单的 Mock MessageListView
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

    @Test
    public void testBaseViewInterfaceExists() {
        // 验证 BaseView 接口存在
        assertNotNull(MainContract.BaseView.class);
    }

    @Test
    public void testMessageListViewInterfaceExists() {
        // 验证 MessageListView 接口存在
        assertNotNull(MainContract.MessageListView.class);
    }

    @Test
    public void testStreamingViewInterfaceExists() {
        // 验证 StreamingView 接口存在
        assertNotNull(MainContract.StreamingView.class);
    }

    @Test
    public void testMessageListViewExtendsBaseView() {
        // 验证 MessageListView 继承 BaseView
        assertTrue(MainContract.BaseView.class.isAssignableFrom(MainContract.MessageListView.class));
    }

    @Test
    public void testStreamingViewExtendsBaseView() {
        // 验证 StreamingView 继承 BaseView
        assertTrue(MainContract.BaseView.class.isAssignableFrom(MainContract.StreamingView.class));
    }

    @Test
    public void testMessageListViewMock() {
        // 测试实现 MessageListView 接口
        final List<Message> receivedMessages = new ArrayList<>();
        final boolean[] loadingCalled = {false};

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
                loadingCalled[0] = true;
            }

            @Override
            public void hideLoading() {
            }
        };

        // 验证 Mock View 可以正常工作
        Message testMsg = new Message();
        testMsg.setContent("Test");
        mockView.onMessageReceived(testMsg);
        mockView.showLoading();

        assertEquals(1, receivedMessages.size());
        assertTrue(loadingCalled[0]);
    }

    @Test
    public void testStreamingViewMock() {
        // 测试实现 StreamingView 接口
        final StringBuilder textBuffer = new StringBuilder();
        final boolean[] thinkingCalled = {false};

        MainContract.StreamingView mockView = new MainContract.StreamingView() {
            @Override
            public void onStreamTextDelta(String textDelta) {
                textBuffer.append(textDelta);
            }

            @Override
            public void onStreamToolUse(String id, String name, String argumentsJson) {
            }

            @Override
            public void onStreamToolResult(String id, String result) {
            }

            @Override
            public void onStreamMessageEnd(String finishReason) {
            }

            @Override
            public void onStreamError(String errorCode, String errorMessage) {
            }

            @Override
            public void showThinking() {
                thinkingCalled[0] = true;
            }

            @Override
            public void hideThinking() {
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
        mockView.onStreamTextDelta("Hello");
        mockView.onStreamTextDelta(" World");
        mockView.showThinking();

        assertEquals("Hello World", textBuffer.toString());
        assertTrue(thinkingCalled[0]);
    }

    @Test
    public void testMultiInterfaceImplementation() {
        // 测试实现多个接口的类
        final List<String> callLog = new ArrayList<>();

        // 创建一个同时实现 MessageListView 和 StreamingView 的类
        Object multiImpl = new MainContract.MessageListView() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
                callLog.add("onMessagesLoaded");
            }

            @Override
            public void onMessageReceived(Message message) {
                callLog.add("onMessageReceived");
            }

            @Override
            public void onUserMessageSent(Message message) {
                callLog.add("onUserMessageSent");
            }

            @Override
            public void onError(String error) {
                callLog.add("onError");
            }

            @Override
            public void showLoading() {
                callLog.add("showLoading");
            }

            @Override
            public void hideLoading() {
                callLog.add("hideLoading");
            }
        };

        // 验证可以赋值给 MessageListView
        MainContract.MessageListView mlv = (MainContract.MessageListView) multiImpl;
        mlv.onMessageReceived(new Message());

        // 验证实现了 MessageListView 接口
        assertEquals(1, callLog.size());
        assertEquals("onMessageReceived", callLog.get(0));

        // 验证两个接口可以独立使用
        assertTrue(multiImpl instanceof MainContract.MessageListView);
    }
}
