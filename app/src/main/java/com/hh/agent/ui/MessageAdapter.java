package com.hh.agent.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.R;
import com.hh.agent.lib.model.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 消息列表的 RecyclerView 适配器
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view, timeFormat);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    /**
     * 更新消息列表
     */
    public void setMessages(List<Message> messages) {
        this.messages = new ArrayList<>(messages);
        notifyDataSetChanged();
    }

    /**
     * 添加单条消息
     */
    public void addMessage(Message message) {
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    /**
     * 清空消息列表
     */
    public void clear() {
        this.messages.clear();
        notifyDataSetChanged();
    }

    /**
     * ViewHolder 类
     */
    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvContent;
        private final TextView tvTimestamp;
        private final TextView tvRole;
        private final SimpleDateFormat timeFormat;

        MessageViewHolder(@NonNull View itemView, SimpleDateFormat timeFormat) {
            super(itemView);
            this.timeFormat = timeFormat;
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvRole = itemView.findViewById(R.id.tvRole);
        }

        void bind(Message message) {
            tvContent.setText(message.getContent());
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));

            // 设置角色标签
            String role = message.getRole();
            if ("user".equals(role)) {
                tvRole.setText("用户");
            } else if ("assistant".equals(role)) {
                tvRole.setText("助手");
            } else if ("system".equals(role)) {
                tvRole.setText("系统");
            } else {
                tvRole.setText(role);
            }
        }
    }
}
