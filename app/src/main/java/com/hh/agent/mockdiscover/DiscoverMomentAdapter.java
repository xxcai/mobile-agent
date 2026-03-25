package com.hh.agent.mockdiscover;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.hh.agent.R;

import java.util.List;

public class DiscoverMomentAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<DiscoverMoment> moments;

    public DiscoverMomentAdapter(Context context, List<DiscoverMoment> moments) {
        this.inflater = LayoutInflater.from(context);
        this.moments = moments;
    }

    @Override
    public int getCount() {
        return moments.size();
    }

    @Override
    public Object getItem(int position) {
        return moments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_discover_moment, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DiscoverMoment moment = moments.get(position);
        holder.avatarView.setText(moment.getAuthor().substring(0, 1));
        holder.authorView.setText(moment.getAuthor());
        holder.timeView.setText(moment.getTime());
        holder.contentView.setText(moment.getContent());

        if (moment.getLocation().isEmpty()) {
            holder.locationView.setVisibility(View.GONE);
        } else {
            holder.locationView.setVisibility(View.VISIBLE);
            holder.locationView.setText("定位 · " + moment.getLocation());
        }

        if (moment.getMediaHint().isEmpty()) {
            holder.mediaCardView.setVisibility(View.GONE);
        } else {
            holder.mediaCardView.setVisibility(View.VISIBLE);
            holder.mediaCardView.setText(moment.getMediaHint());
        }

        holder.statsView.setText("点赞 " + moment.getLikeCount() + "  评论 " + moment.getCommentCount());
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView avatarView;
        private final TextView authorView;
        private final TextView timeView;
        private final TextView contentView;
        private final TextView locationView;
        private final TextView mediaCardView;
        private final TextView statsView;

        private ViewHolder(View itemView) {
            avatarView = itemView.findViewById(R.id.momentAvatarView);
            authorView = itemView.findViewById(R.id.momentAuthorView);
            timeView = itemView.findViewById(R.id.momentTimeView);
            contentView = itemView.findViewById(R.id.momentContentView);
            locationView = itemView.findViewById(R.id.momentLocationView);
            mediaCardView = itemView.findViewById(R.id.momentMediaCardView);
            statsView = itemView.findViewById(R.id.momentStatsView);
        }
    }
}
