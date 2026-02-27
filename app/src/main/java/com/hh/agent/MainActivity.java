package com.hh.agent;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.hh.agent.lib.NativeLib;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NativeLib nativeLib = new NativeLib();

        String helloFromCpp = nativeLib.stringFromJNI();
        int sum = nativeLib.add(5, 3);
        String message = nativeLib.getMessage();

        String result = "Hello World\n\n" +
                "C++ Test:\n" +
                "stringFromJNI: " + helloFromCpp + "\n" +
                "add(5, 3): " + sum + "\n" +
                "getMessage: " + message;

        TextView textView = new TextView(this);
        textView.setTextSize(18);
        textView.setText(result);
        textView.setPadding(50, 50, 50, 50);

        setContentView(textView);
    }
}
