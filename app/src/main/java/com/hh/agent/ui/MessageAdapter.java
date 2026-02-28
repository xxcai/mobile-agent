package com.hh.agent.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
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

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/**
 * 消息列表的 RecyclerView 适配器
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_USER = 0;
    public static final int VIEW_TYPE_ASSISTANT = 1;
    public static final int VIEW_TYPE_THINKING = 2;

    private List<Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Markwon markwon;

    public MessageAdapter(Context context) {
        // 初始化 Markwon，添加常用插件
        this.markwon = Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .build();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_message_user, parent, false);
            return new MessageViewHolder(view, timeFormat, markwon);
        } else if (viewType == VIEW_TYPE_THINKING) {
            View view = inflater.inflate(R.layout.item_thinking, parent, false);
            return new ThinkingViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view, timeFormat, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if ("thinking".equals(message.getRole())) {
            return VIEW_TYPE_THINKING;
        } else if ("user".equals(message.getRole())) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_ASSISTANT;
        }
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
     * 移除思考中的提示消息
     */
    public void removeThinkingMessage() {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("thinking".equals(msg.getRole())) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    /**
     * 清空消息列表
     */
    public void clear() {
        this.messages.clear();
        notifyDataSetChanged();
    }

    /**
     * 普通消息 ViewHolder
     */
    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvContent;
        private final TextView tvTimestamp;
        private final TextView tvRole;
        private final SimpleDateFormat timeFormat;
        private final Markwon markwon;

        MessageViewHolder(@NonNull View itemView, SimpleDateFormat timeFormat, Markwon markwon) {
            super(itemView);
            this.timeFormat = timeFormat;
            this.markwon = markwon;
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvRole = itemView.findViewById(R.id.tvRole);
        }

        void bind(Message message) {
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));

            String role = message.getRole();
            if ("user".equals(role)) {
                tvRole.setText("我");
                tvContent.setText(message.getContent());
            } else if ("assistant".equals(role)) {
                tvRole.setText("助手");
                // 使用 Markwon 渲染 Markdown
                markwon.setMarkdown(tvContent, message.getContent());
            } else if ("system".equals(role)) {
                tvRole.setText("系统");
                tvContent.setText(message.getContent());
            } else {
                tvRole.setText(role);
                tvContent.setText(message.getContent());
            }
        }
    }

    /**
     * 思考中 ViewHolder
     */
    static class ThinkingViewHolder extends RecyclerView.ViewHolder {

        ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
