package com.hh.agent.mockbusiness;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentActivity;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = BusinessHomeFragmentTest.TestApplication.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BusinessHomeFragmentTest {

    public static final class TestApplication extends Application {
    }

    public static final class NonDebugTestApplication extends Application {
        @Override
        public ApplicationInfo getApplicationInfo() {
            ApplicationInfo applicationInfo = super.getApplicationInfo();
            applicationInfo.flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
            return applicationInfo;
        }
    }

    @Test
    public void debugEntry_isVisibleInDebugBuild_andOpensDebugBusinessWebActivity() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .setup()
                .get();
        BusinessHomeFragment fragment = new BusinessHomeFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        int debugEntryId = activity.getResources().getIdentifier(
                "businessDebugEntry", "id", activity.getPackageName());
        assertTrue(debugEntryId != 0);

        View debugEntry = fragment.requireView().findViewById(debugEntryId);

        assertNotNull(debugEntry);
        assertEquals(View.VISIBLE, debugEntry.getVisibility());

        debugEntry.performClick();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(BusinessWebActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("业务调试页", intent.getStringExtra(BusinessWebActivity.EXTRA_TITLE));
        assertTrue(intent.getBooleanExtra(BusinessWebActivity.EXTRA_ENABLE_DEBUG_CONTROLS, false));
        assertTrue(intent.getBooleanExtra(BusinessWebActivity.EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE, false));
        assertEquals("debug submit button",
                intent.getStringExtra(BusinessWebActivity.EXTRA_PROBE_TARGET_HINT));
        assertEquals("business_page_form.html",
                intent.getStringExtra(BusinessWebActivity.EXTRA_PAGE_TEMPLATE_ASSET));
        assertFalse(intent.hasExtra(BusinessWebActivity.EXTRA_HTML_CONTENT));
    }

    @Test
    public void quickAction_stillOpensRegularBusinessWebActivityWithoutDebugExtras() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .setup()
                .get();
        BusinessHomeFragment fragment = new BusinessHomeFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        ViewGroup quickActionPage = fragment.requireView().findViewById(R.id.quickActionPageOne);
        View firstAction = quickActionPage.getChildAt(0);

        assertNotNull(firstAction);

        firstAction.performClick();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(BusinessWebActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(intent.hasExtra(BusinessWebActivity.EXTRA_HTML_CONTENT));
        assertFalse(intent.hasExtra(BusinessWebActivity.EXTRA_ENABLE_DEBUG_CONTROLS));
        assertFalse(intent.hasExtra(BusinessWebActivity.EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE));
        assertFalse(intent.hasExtra(BusinessWebActivity.EXTRA_PROBE_TARGET_HINT));
        assertFalse(intent.hasExtra(BusinessWebActivity.EXTRA_PAGE_TEMPLATE_ASSET));
    }

    @Test
    @Config(sdk = 34, application = NonDebugTestApplication.class)
    public void debugEntry_isGoneWhenAppIsNotDebuggable() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .setup()
                .get();
        BusinessHomeFragment fragment = new BusinessHomeFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        assertEquals(0,
                fragment.requireContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);

        View debugEntry = fragment.requireView().findViewById(R.id.businessDebugEntry);

        assertNotNull(debugEntry);
        assertEquals(View.GONE, debugEntry.getVisibility());
    }

    @Test
    public void isDebuggable_returnsFalseWhenDebuggableFlagMissing() {
        ApplicationInfo applicationInfo = new ApplicationInfo();

        assertFalse(BusinessHomeFragment.isDebuggable(applicationInfo));
    }
}
