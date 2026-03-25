package com.hh.agent.mockdiscover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hh.agent.R;

public class DiscoverFeedFragment extends Fragment {

    public static DiscoverFeedFragment newInstance() {
        return new DiscoverFeedFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView listView = view.findViewById(R.id.momentsListView);
        listView.setAdapter(new DiscoverMomentAdapter(requireContext(), MockDiscoverRepository.getMoments()));
    }
}
