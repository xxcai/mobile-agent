package com.hh.agent;

import android.app.Application;

import androidx.fragment.app.Fragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import com.hh.agent.mockim.ChatListFragment;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = MainActivityTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MainActivityTest {

    public static final class TestApplication extends Application {
    }

    @Test
    public void mainActivityDoesNotRenderTopBarH5BenchmarkButton() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        assertEquals(0, activity.getResources().getIdentifier(
                "openH5BenchmarkButton", "id", activity.getPackageName()));
    }

    @Test
    public void mainActivityStillShowsChatsTabByDefault() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.contentContainer);

        assertEquals(ChatListFragment.class, fragment.getClass());
    }
}
