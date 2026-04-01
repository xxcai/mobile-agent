package com.hh.agent;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hh.agent.mockbusiness.BusinessHomeFragment;
import com.hh.agent.mockdiscover.DiscoverFeedFragment;
import com.hh.agent.mockemail.EmailListFragment;
import com.hh.agent.mockim.ChatListFragment;
import com.hh.agent.mockim.PlaceholderFragment;

public class MainActivity extends AppCompatActivity {

    private TextView homeTitleView;
    private TextView tabChats;
    private TextView tabContacts;
    private TextView tabDiscover;
    private TextView tabProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        bindEvents();

        if (savedInstanceState == null) {
            showChatsTab();
        }
    }

    private void bindViews() {
        homeTitleView = findViewById(R.id.homeTitleView);
        tabChats = findViewById(R.id.tabChats);
        tabContacts = findViewById(R.id.tabContacts);
        tabDiscover = findViewById(R.id.tabDiscover);
        tabProfile = findViewById(R.id.tabProfile);
    }

    private void bindEvents() {
        tabChats.setOnClickListener(v -> showChatsTab());
        tabContacts.setOnClickListener(v -> showEmailTab());
        tabDiscover.setOnClickListener(v -> showDiscoverTab());
        tabProfile.setOnClickListener(v -> showBusinessTab());
        homeTitleView.setOnLongClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RouteManualVerificationActivity.class));
            return true;
        });
    }

    private void showChatsTab() {
        switchContent(ChatListFragment.newInstance());
        homeTitleView.setText("微信");
        updateSelectedTab(tabChats);
    }

    private void showEmailTab() {
        switchContent(EmailListFragment.newInstance());
        homeTitleView.setText("Email");
        updateSelectedTab(tabContacts);
    }

    private void showDiscoverTab() {
        switchContent(DiscoverFeedFragment.newInstance());
        homeTitleView.setText("发现");
        updateSelectedTab(tabDiscover);
    }

    private void showPlaceholderTab(String title) {
        switchContent(PlaceholderFragment.newInstance(title));
        homeTitleView.setText(title);
        updateSelectedTab(resolveTabView(title));
    }

    private void showBusinessTab() {
        switchContent(BusinessHomeFragment.newInstance());
        homeTitleView.setText("业务");
        updateSelectedTab(tabProfile);
    }

    private TextView resolveTabView(String title) {
        if ("Email".equals(title)) {
            return tabContacts;
        }
        if ("发现".equals(title)) {
            return tabDiscover;
        }
        if ("业务".equals(title)) {
            return tabProfile;
        }
        return tabProfile;
    }

    private void switchContent(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentContainer, fragment)
                .commit();
    }

    private void updateSelectedTab(TextView selectedTab) {
        TextView[] tabs = new TextView[]{tabChats, tabContacts, tabDiscover, tabProfile};
        for (TextView tab : tabs) {
            boolean selected = tab == selectedTab;
            tab.setSelected(selected);
            tab.setTextColor(getColor(selected ? R.color.mock_im_tab_selected : R.color.mock_im_tab_unselected));
        }
    }
}
