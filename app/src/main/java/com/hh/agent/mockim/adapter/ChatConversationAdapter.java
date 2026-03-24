package com.hh.agent.mockim.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.hh.agent.R;
import com.hh.agent.mockim.model.ChatConversation;

import java.util.List;

public class ChatConversationAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<ChatConversation> conversations;

    public ChatConversationAdapter(Context context, List<ChatConversation> conversations) {
        this.inflater = LayoutInflater.from(context);
        this.conversations = conversations;
    }

    @Override
    public int getCount() {
        return conversations.size();
    }

    @Override
    public Object getItem(int position) {
        return conversations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_chat_conversation, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ChatConversation conversation = conversations.get(position);
        holder.avatarView.setText(conversation.getTitle().substring(0, 1));
        holder.titleView.setText(conversation.getTitle());
        holder.timeView.setText(conversation.getTime());
        holder.messageView.setText(conversation.getLastMessage());
        holder.pinView.setVisibility(conversation.isPinned() ? View.VISIBLE : View.GONE);

        if (conversation.getUnreadCount().isEmpty()) {
            holder.unreadView.setVisibility(View.GONE);
        } else {
            holder.unreadView.setVisibility(View.VISIBLE);
            holder.unreadView.setText(conversation.getUnreadCount());
        }

        return convertView;
    }

    private static final class ViewHolder {
        private final TextView avatarView;
        private final TextView titleView;
        private final TextView timeView;
        private final TextView messageView;
        private final TextView unreadView;
        private final TextView pinView;

        private ViewHolder(View itemView) {
            avatarView = itemView.findViewById(R.id.avatarView);
            titleView = itemView.findViewById(R.id.titleView);
            timeView = itemView.findViewById(R.id.timeView);
            messageView = itemView.findViewById(R.id.messageView);
            unreadView = itemView.findViewById(R.id.unreadView);
            pinView = itemView.findViewById(R.id.pinView);
        }
    }
}
