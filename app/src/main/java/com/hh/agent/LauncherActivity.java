package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.android.AgentActivity;

/**
 * 启动界面 - 直接进入 AgentActivity
 */
public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 跳转到 agent-android 的 AgentActivity
        Intent intent = new Intent(this, AgentActivity.class);
        startActivity(intent);
        finish();
    }
}
