package com.hh.agent;

import android.app.Application;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = MainActivityTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MainActivityTest {

    public static final class TestApplication extends Application {
    }

    @Test
    public void topBarH5BenchmarkButton_noLongerExists() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class)
                .setup()
                .get();

        // Verify the button field no longer exists in MainActivity class
        Field buttonField = null;
        try {
            buttonField = MainActivity.class.getDeclaredField("openH5BenchmarkButton");
        } catch (NoSuchFieldException e) {
            // Expected - field should not exist
        }
        assertNull("MainActivity should not have openH5BenchmarkButton field", buttonField);

        // Verify the button is not in the layout
        int buttonId = activity.getResources().getIdentifier(
                "openH5BenchmarkButton", "id", activity.getPackageName());
        if (buttonId != 0) {
            Button button = activity.findViewById(buttonId);
            assertNull("openH5BenchmarkButton should not be in layout", button);
        }
    }
}
