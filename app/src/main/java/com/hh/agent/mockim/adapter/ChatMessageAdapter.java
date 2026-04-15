package com.hh.agent.mockim.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hh.agent.R;
import com.hh.agent.mockim.model.ChatMessage;

import java.util.List;

public class ChatMessageAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<ChatMessage> messages;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.inflater = LayoutInflater.from(context);
        this.messages = messages;
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Object getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_chat_message, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ChatMessage message = messages.get(position);
        holder.contentView.setText(message.getContent());
        holder.bubbleView.setBackgroundResource(
                message.isFromMe() ? R.drawable.bg_message_outgoing : R.drawable.bg_message_incoming);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleView.getLayoutParams();
        if (message.isFromMe()) {
            params.gravity = Gravity.END;
            params.setMargins(dp(parent.getContext(), 56), dp(parent.getContext(), 6), 0, dp(parent.getContext(), 6));
        } else {
            params.gravity = Gravity.START;
            params.setMargins(0, dp(parent.getContext(), 6), dp(parent.getContext(), 56), dp(parent.getContext(), 6));
        }
        holder.bubbleView.setLayoutParams(params);

        return convertView;
    }

    private int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private static final class ViewHolder {
        private final TextView contentView;
        private final LinearLayout bubbleView;

        private ViewHolder(View itemView) {
            contentView = itemView.findViewById(R.id.messageContentView);
            bubbleView = itemView.findViewById(R.id.messageBubbleView);
        }
    }
}
