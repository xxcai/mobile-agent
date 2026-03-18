package com.hh.agent;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

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
        // Agent 初始化已在 App.onCreate() 中完成
    }
}
