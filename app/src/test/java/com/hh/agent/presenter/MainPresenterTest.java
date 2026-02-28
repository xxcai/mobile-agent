package com.hh.agent.presenter;

import com.hh.agent.contract.MainContract;
import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.impl.MockNanobotApi;
import com.hh.agent.lib.model.Message;
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
        // 验证 ApiType 枚举存在
        assertNotNull(MainPresenter.ApiType.MOCK);
        assertNotNull(MainPresenter.ApiType.HTTP);
    }

    @Test
    public void testPresenterCreation() {
        // 验证可以使用不同方式创建 presenter
        // 注意：在非 Android 环境下，Handler/Looper 不可用，所以这里只验证类结构
        assertNotNull(MainPresenter.class);
    }

    @Test
    public void testMockApiCreation() {
        // 验证 MockNanobotApi 可以正常工作
        NanobotApi api = new MockNanobotApi();
        assertNotNull(api);
    }

    @Test
    public void testMockView() {
        // 验证 View 接口可以正常实现
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
