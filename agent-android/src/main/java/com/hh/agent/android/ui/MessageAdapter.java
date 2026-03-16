package com.hh.agent.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.android.R;
import com.hh.agent.library.model.Message;

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
    public static final int VIEW_TYPE_TOOL_USE = 3;
    public static final int VIEW_TYPE_TOOL_RESULT = 4;

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
        } else if (viewType == VIEW_TYPE_TOOL_USE) {
            View view = inflater.inflate(R.layout.item_tool_use, parent, false);
            return new ToolUseViewHolder(view);
        } else if (viewType == VIEW_TYPE_TOOL_RESULT) {
            View view = inflater.inflate(R.layout.item_tool_result, parent, false);
            return new ToolResultViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view, timeFormat, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        Log.d("MessageAdapter", "onBindViewHolder: pos=" + position + ", holder=" + holder.getClass().getSimpleName() + ", msg.role=" + message.getRole() + ", msg.content=" + message.getContent());
        if (holder instanceof ThinkingViewHolder) {
            ((ThinkingViewHolder) holder).bindMessage(message);
        } else if (holder instanceof ToolUseViewHolder) {
            ((ToolUseViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof ToolResultViewHolder) {
            ((ToolResultViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        String role = message.getRole();
        if ("thinking".equals(role)) {
            return VIEW_TYPE_THINKING;
        } else if ("user".equals(role)) {
            return VIEW_TYPE_USER;
        } else if ("sending".equals(role)) {
            return VIEW_TYPE_THINKING;  // 复用 thinking 样式显示发送中
        } else if ("tool_use".equals(role)) {
            return VIEW_TYPE_TOOL_USE;
        } else if ("tool_result".equals(role)) {
            return VIEW_TYPE_TOOL_RESULT;
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
        Log.d("MessageAdapter", "addMessage: role=" + message.getRole() + ", content=" + message.getContent());
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
     * 更新思考中的消息内容
     * @param content 新的文本内容（替换而非追加）
     */
    public void updateThinkingMessage(String content) {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("thinking".equals(msg.getRole())) {
                msg.setContent(content);
                notifyItemChanged(i);
                return;
            }
        }
    }

    /**
     * 获取思考消息的索引
     * @return thinking 消息的索引，不存在则返回 -1
     */
    public int getThinkingMessageIndex() {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("thinking".equals(msg.getRole())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 添加工具调用消息
     * @param toolName 工具名称
     * @param toolInput 工具输入参数
     */
    public void addToolUseMessage(String toolName, String toolInput) {
        Message message = new Message();
        message.setRole("tool_use");
        message.setName(toolName);
        message.setContent(toolInput);
        message.setTimestamp(System.currentTimeMillis());
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    /**
     * 添加工具结果消息
     * @param toolName 工具名称
     * @param toolResult 工具返回结果
     */
    public void addToolResultMessage(String toolName, String toolResult) {
        Message message = new Message();
        message.setRole("tool_result");
        message.setName(toolName);
        message.setContent(toolResult);
        message.setTimestamp(System.currentTimeMillis());
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
     * 获取指定位置的消息
     * @param position 消息位置
     * @return 消息对象，不存在则返回 null
     */
    public Message getMessageAt(int position) {
        if (position >= 0 && position < messages.size()) {
            return messages.get(position);
        }
        return null;
    }

    /**
     * 删除发送中状态消息
     */
    public void removeSendingMessage() {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("sending".equals(msg.getRole())) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    /**
     * 添加发送中状态消息
     * @return 发送中消息的索引
     */
    public int addSendingMessage() {
        Message message = new Message();
        message.setRole("sending");
        message.setContent("发送中...");
        message.setTimestamp(System.currentTimeMillis());
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
        return this.messages.size() - 1;
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

        private final TextView tvThinking;

        ThinkingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvThinking = itemView.findViewById(R.id.tvThinking);
            Log.d("MessageAdapter", "ThinkingViewHolder created, tvThinking=" + tvThinking);
        }

        void bind(String content) {
            tvThinking.setText(content);
        }

        void bindMessage(Message message) {
            if (tvThinking == null) {
                Log.e("MessageAdapter", "tvThinking is null!");
                return;
            }
            String content = message.getContent();
            if (content == null) {
                content = "";
            }
            Log.d("MessageAdapter", "bindMessage: setting text to '" + content + "'");
            tvThinking.setText(content);
            Log.d("MessageAdapter", "bindMessage: text now is '" + tvThinking.getText() + "'");
        }
    }

    /**
     * 工具调用 ViewHolder
     */
    static class ToolUseViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvToolName;
        private final TextView tvToolInput;

        ToolUseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvToolName = itemView.findViewById(R.id.tvToolName);
            tvToolInput = itemView.findViewById(R.id.tvToolInput);
        }

        void bind(String toolName, String toolInput) {
            tvToolName.setText(toolName);
            tvToolInput.setText(toolInput);
        }
    }

    /**
     * 工具结果 ViewHolder
     */
    static class ToolResultViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvToolName;
        private final TextView tvToolResult;

        ToolResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvToolName = itemView.findViewById(R.id.tvToolName);
            tvToolResult = itemView.findViewById(R.id.tvToolResult);
        }

        void bind(String toolName, String toolResult) {
            tvToolName.setText(toolName + " Result");
            tvToolResult.setText(toolResult);
        }
    }
}
