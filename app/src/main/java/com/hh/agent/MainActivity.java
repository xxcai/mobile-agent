package com.hh.agent;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hh.agent.mockdiscover.DiscoverFeedFragment;
import com.hh.agent.mockim.ChatListFragment;
import com.hh.agent.mockim.PlaceholderFragment;

public class MainActivity extends AppCompatActivity {

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
        tabChats = findViewById(R.id.tabChats);
        tabContacts = findViewById(R.id.tabContacts);
        tabDiscover = findViewById(R.id.tabDiscover);
        tabProfile = findViewById(R.id.tabProfile);
    }

    private void bindEvents() {
        tabChats.setOnClickListener(v -> showChatsTab());
        tabContacts.setOnClickListener(v -> showPlaceholderTab("通讯录"));
        tabDiscover.setOnClickListener(v -> showDiscoverTab());
        tabProfile.setOnClickListener(v -> showPlaceholderTab("我"));
    }

    private void showChatsTab() {
        switchContent(ChatListFragment.newInstance());
        updateSelectedTab(tabChats);
    }

    private void showDiscoverTab() {
        switchContent(DiscoverFeedFragment.newInstance());
        updateSelectedTab(tabDiscover);
    }

    private void showPlaceholderTab(String title) {
        switchContent(PlaceholderFragment.newInstance(title));
        updateSelectedTab(resolveTabView(title));
    }

    private TextView resolveTabView(String title) {
        if ("通讯录".equals(title)) {
            return tabContacts;
        }
        if ("发现".equals(title)) {
            return tabDiscover;
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
