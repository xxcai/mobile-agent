package com.hh.agent;

import android.os.Bundle;
import android.app.Application;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.floating.FloatingBallManager;

/**
 * MainActivity - 主界面启动页
 * 作为空白容器，悬浮球浮在此界面上
 * Agent 功能通过悬浮球点击触发 ContainerActivity 加载
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 空白容器，不需要 setContentView

        // 注册生命周期回调，控制悬浮球显示
        getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(android.app.Activity activity) {
                if (activity == MainActivity.this) {
                    FloatingBallManager.getInstance(MainActivity.this).show();
                }
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
                if (activity == MainActivity.this) {
                    FloatingBallManager.getInstance(MainActivity.this).hide();
                }
            }

            // 其他回调方法保持空实现
            @Override
            public void onActivityCreated(android.app.Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(android.app.Activity activity) {}

            @Override
            public void onActivityStopped(android.app.Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }
}
