package com.hh.agent.mockbusiness;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hh.agent.BusinessWebActivity;
import com.hh.agent.R;
import com.hh.agent.mockbusiness.model.BannerItem;
import com.hh.agent.mockbusiness.model.BusinessQuickAction;
import com.hh.agent.mockbusiness.model.TodoItem;

import java.util.List;

public class BusinessHomeFragment extends Fragment {

    public static BusinessHomeFragment newInstance() {
        return new BusinessHomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_business_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        List<BusinessQuickAction> quickActions = MockBusinessRepository.getQuickActions();
        bindQuickActions(view, quickActions);
        bindBanners(view, MockBusinessRepository.getBannerItems());
        bindMeetingCard(view);
        bindAttendanceCard(view);
        bindTodoList(view, MockBusinessRepository.getTodoItems());
        bindTopMore(view);
        adjustQuickActionPages(view);
    }

    private void bindQuickActions(View root, List<BusinessQuickAction> quickActions) {
        GridLayout firstPage = root.findViewById(R.id.quickActionPageOne);
        GridLayout secondPage = root.findViewById(R.id.quickActionPageTwo);
        for (int index = 0; index < quickActions.size(); index++) {
            BusinessQuickAction action = quickActions.get(index);
            if (index < 4) {
                View itemView = createQuickActionView(firstPage, action);
                firstPage.addView(itemView);
            } else {
                View itemView = createQuickActionView(secondPage, action);
                secondPage.addView(itemView);
            }
        }
    }

    private View createQuickActionView(ViewGroup parent, BusinessQuickAction action) {
        View itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_business_quick_action, parent, false);
        ImageView iconView = itemView.findViewById(R.id.quickActionIcon);
        TextView titleView = itemView.findViewById(R.id.quickActionTitle);
        TextView subtitleView = itemView.findViewById(R.id.quickActionSubtitle);
        View iconBackground = itemView.findViewById(R.id.quickActionIconBg);
        iconView.setImageResource(action.getIconResId());
        iconBackground.setBackgroundResource(action.getIconBackgroundResId());
        titleView.setText(action.getTitle());
        subtitleView.setText(action.getSubtitle());
        itemView.setOnClickListener(v -> openWebPage(action.getTitle(), action.getHtmlContent()));
        return itemView;
    }

    private void bindBanners(View root, List<BannerItem> items) {
        LinearLayout container = root.findViewById(R.id.bannerContainer);
        for (BannerItem item : items) {
            View bannerView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_business_banner, container, false);
            TextView titleView = bannerView.findViewById(R.id.bannerTitleView);
            TextView subtitleView = bannerView.findViewById(R.id.bannerSubtitleView);
            View backgroundView = bannerView.findViewById(R.id.bannerBackgroundView);
            titleView.setText(item.getTitle());
            subtitleView.setText(item.getSubtitle());
            backgroundView.setBackgroundResource(item.getBackgroundResId());
            bannerView.setOnClickListener(v -> openWebPage(item.getTitle(), item.getHtmlContent()));
            container.addView(bannerView);
        }
    }

    private void bindMeetingCard(View root) {
        View card = root.findViewById(R.id.businessMeetingCard);
        card.setOnClickListener(v -> openWebPage("会议提醒", MockBusinessRepository.getMeetingHtml()));
    }

    private void bindAttendanceCard(View root) {
        View card = root.findViewById(R.id.businessAttendanceCard);
        View punchButton = root.findViewById(R.id.businessPunchButton);
        View.OnClickListener listener =
                v -> openWebPage("打卡中心", MockBusinessRepository.getAttendanceHtml());
        card.setOnClickListener(listener);
        punchButton.setOnClickListener(listener);
    }

    private void bindTodoList(View root, List<TodoItem> todoItems) {
        LinearLayout container = root.findViewById(R.id.todoListContainer);
        View card = root.findViewById(R.id.businessTodoCard);
        card.setOnClickListener(v -> openWebPage("待办列表", MockBusinessRepository.getTodoHtml()));
        for (TodoItem item : todoItems) {
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_business_todo, container, false);
            TextView badgeView = itemView.findViewById(R.id.todoBadgeView);
            TextView titleView = itemView.findViewById(R.id.todoTitleView);
            TextView subtitleView = itemView.findViewById(R.id.todoSubtitleView);
            titleView.setText(item.getTitle());
            subtitleView.setText(item.getSubtitle());
            badgeView.setText(item.isHighlighted() ? "紧急" : "待办");
            badgeView.setBackgroundResource(item.isHighlighted()
                    ? R.drawable.bg_business_badge_hot
                    : R.drawable.bg_business_badge_normal);
            itemView.setOnClickListener(v -> openWebPage(item.getTitle(), item.getHtmlContent()));
            container.addView(itemView);
        }
    }

    private void bindTopMore(View root) {
        root.findViewById(R.id.businessMoreEntry)
                .setOnClickListener(v -> openWebPage("业务总览", MockBusinessRepository.getMoreHtml()));
    }

    private void adjustQuickActionPages(View root) {
        HorizontalScrollView scrollView = root.findViewById(R.id.quickActionScrollView);
        View pageOne = root.findViewById(R.id.quickActionPageOne);
        View pageTwo = root.findViewById(R.id.quickActionPageTwo);
        scrollView.post(() -> {
            int pageWidth = scrollView.getWidth();
            if (pageWidth <= 0) {
                return;
            }
            pageOne.getLayoutParams().width = pageWidth;
            pageTwo.getLayoutParams().width = pageWidth;
            pageOne.requestLayout();
            pageTwo.requestLayout();
        });
    }

    private void openWebPage(String title, String htmlContent) {
        Intent intent = new Intent(requireContext(), BusinessWebActivity.class);
        intent.putExtra(BusinessWebActivity.EXTRA_TITLE, title);
        intent.putExtra(BusinessWebActivity.EXTRA_HTML_CONTENT, htmlContent);
        startActivity(intent);
    }
}
