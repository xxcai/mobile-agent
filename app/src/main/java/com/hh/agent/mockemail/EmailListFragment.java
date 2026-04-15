package com.hh.agent.mockemail;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hh.agent.EmailDetailActivity;
import com.hh.agent.R;
import com.hh.agent.mockemail.adapter.EmailListAdapter;
import com.hh.agent.mockemail.model.MockEmail;

import java.util.ArrayList;
import java.util.List;

public class EmailListFragment extends Fragment {

    private static final int PAGE_SIZE = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MockEmail> loadedEmails = new ArrayList<>();

    private EmailListAdapter adapter;
    private View loadingContainer;
    private ListView listView;
    private View footerLoadingView;
    private ProgressBar footerProgressBar;
    private TextView footerTextView;
    private boolean initialLoaded;
    private boolean loadingMore;

    public static EmailListFragment newInstance() {
        return new EmailListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_email_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadingContainer = view.findViewById(R.id.emailInitialLoadingView);
        listView = view.findViewById(R.id.emailListView);
        adapter = new EmailListAdapter(requireContext(), loadedEmails);
        footerLoadingView = LayoutInflater.from(requireContext())
                .inflate(R.layout.view_email_loading_footer, listView, false);
        footerProgressBar = footerLoadingView.findViewById(R.id.emailFooterProgressBar);
        footerTextView = footerLoadingView.findViewById(R.id.emailFooterTextView);
        listView.addFooterView(footerLoadingView, null, false);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            if (position >= loadedEmails.size()) {
                return;
            }
            MockEmail email = loadedEmails.get(position);
            Intent intent = new Intent(requireContext(), EmailDetailActivity.class);
            intent.putExtra(EmailDetailActivity.EXTRA_EMAIL_ID, email.getId());
            startActivity(intent);
        });
        listView.setOnScrollListener(new EndlessScrollListener());

        showInitialLoading();
    }

    private void showInitialLoading() {
        initialLoaded = false;
        loadingContainer.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        handler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            loadedEmails.clear();
            loadedEmails.addAll(MockEmailRepository.getEmailsPage(0, PAGE_SIZE));
            adapter.notifyDataSetChanged();
            loadingContainer.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            initialLoaded = true;
            refreshFooterState();
        }, buildDelayMillis(loadedEmails.size()));
    }

    private void loadMoreEmails() {
        if (loadingMore || !MockEmailRepository.hasMore(loadedEmails.size())) {
            return;
        }
        loadingMore = true;
        refreshFooterState();
        int currentSize = loadedEmails.size();
        handler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            loadedEmails.addAll(MockEmailRepository.getEmailsPage(currentSize, PAGE_SIZE));
            adapter.notifyDataSetChanged();
            loadingMore = false;
            refreshFooterState();
        }, buildDelayMillis(currentSize + 1));
    }

    private void refreshFooterState() {
        boolean hasMore = MockEmailRepository.hasMore(loadedEmails.size());
        footerLoadingView.setVisibility(initialLoaded ? View.VISIBLE : View.GONE);
        footerProgressBar.setVisibility(loadingMore ? View.VISIBLE : View.GONE);
        if (loadingMore) {
            footerTextView.setText("正在加载更多邮件...");
        } else if (hasMore) {
            footerTextView.setText("上拉加载更多");
        } else {
            footerTextView.setText("没有更多邮件了");
        }
    }

    private int buildDelayMillis(int seed) {
        return 1000 + Math.abs(seed * 173 % 1000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private final class EndlessScrollListener implements AbsListView.OnScrollListener {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }

        @Override
        public void onScroll(AbsListView view,
                             int firstVisibleItem,
                             int visibleItemCount,
                             int totalItemCount) {
            if (!initialLoaded || loadingMore || totalItemCount == 0) {
                return;
            }
            if (firstVisibleItem + visibleItemCount >= totalItemCount) {
                loadMoreEmails();
            }
        }
    }
}
