package com.hh.agent.presenter;

import com.hh.agent.contract.MainContract;
import com.hh.agent.lib.api.NanobotApi;
import com.hh.agent.lib.impl.MockNanobotApi;
import com.hh.agent.lib.model.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/**
 * MainPresenter 的单元测试
 */
public class MainPresenterTest {

    private MainPresenter presenter;
    private NanobotApi api;
    private MockView mockView;

    @Before
    public void setUp() {
        api = new MockNanobotApi();
        presenter = new MainPresenter(api, "cli:test");
        mockView = new MockView();
    }

    @After
    public void tearDown() {
        presenter.destroy();
    }

    @Test
    public void testAttachView() {
        presenter.attachView(mockView);
        assertNotNull(mockView);
    }

    @Test
    public void testDetachView() {
        presenter.attachView(mockView);
        presenter.detachView();
        // 验证 view 被设置为 null
    }

    @Test
    public void testLoadMessages() throws InterruptedException {
        // 先创建会话
        api.createSession("cli", "test");

        final CountDownLatch latch = new CountDownLatch(1);

        presenter.attachView(new MainContract.View() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
                assertNotNull(messages);
                latch.countDown();
            }

            @Override
            public void onMessageReceived(Message message) {
            }

            @Override
            public void onError(String error) {
                fail("不应该有错误: " + error);
            }

            @Override
            public void showLoading() {
            }

            @Override
            public void hideLoading() {
            }
        });

        presenter.loadMessages();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("等待超时", completed);
    }

    @Test
    public void testSendMessage() throws InterruptedException {
        // 先创建会话
        api.createSession("cli", "test");

        final CountDownLatch latch = new CountDownLatch(1);

        presenter.attachView(new MainContract.View() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
            }

            @Override
            public void onMessageReceived(Message message) {
                assertNotNull(message);
                assertEquals("assistant", message.getRole());
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                fail("不应该有错误: " + error);
            }

            @Override
            public void showLoading() {
            }

            @Override
            public void hideLoading() {
            }
        });

        presenter.sendMessage("Hello");

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("等待超时", completed);
    }

    @Test
    public void testSendEmptyMessage() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        presenter.attachView(new MainContract.View() {
            @Override
            public void onMessagesLoaded(List<Message> messages) {
            }

            @Override
            public void onMessageReceived(Message message) {
                fail("不应该收到消息");
            }

            @Override
            public void onError(String error) {
                assertEquals("消息不能为空", error);
                latch.countDown();
            }

            @Override
            public void showLoading() {
            }

            @Override
            public void hideLoading() {
            }
        });

        presenter.sendMessage("");

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("等待超时", completed);
    }

    /**
     * Mock View 实现
     */
    private static class MockView implements MainContract.View {
        @Override
        public void onMessagesLoaded(List<Message> messages) {
        }

        @Override
        public void onMessageReceived(Message message) {
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
    }
}
