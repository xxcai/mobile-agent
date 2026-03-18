package com.hh.agent.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.android.R;
import com.hh.agent.library.model.Message;
import com.hh.agent.library.model.ToolCall;

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
    public static final int VIEW_TYPE_ERROR = 5;
    public static final int VIEW_TYPE_RESPONSE = 6;

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
        } else if (viewType == VIEW_TYPE_ERROR) {
            View view = inflater.inflate(R.layout.item_error, parent, false);
            return new ErrorViewHolder(view);
        } else if (viewType == VIEW_TYPE_RESPONSE) {
            View view = inflater.inflate(R.layout.item_response_card, parent, false);
            return new ResponseCardViewHolder(view, timeFormat, markwon);
        } else {
            View view = inflater.inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view, timeFormat, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder instanceof ThinkingViewHolder) {
            ((ThinkingViewHolder) holder).bindMessage(message);
        } else if (holder instanceof ToolUseViewHolder) {
            ((ToolUseViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof ToolResultViewHolder) {
            ((ToolResultViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof ErrorViewHolder) {
            ((ErrorViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(message);
        } else if (holder instanceof ResponseCardViewHolder) {
            ((ResponseCardViewHolder) holder).bind(message);
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
        } else if ("error".equals(role)) {
            return VIEW_TYPE_ERROR;
        } else if ("response".equals(role)) {
            return VIEW_TYPE_RESPONSE;
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
     * 清除响应卡片中的工具调用列表
     * 用于状态转换：将"正在响应"转为"历史响应"时隐藏工具区
     */
    public void clearToolCallsInResponse() {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("response".equals(msg.getRole())) {
                // 清除 toolCalls 列表，这样 bind 方法会隐藏工具区
                msg.setToolCalls(null);
                notifyItemChanged(i);
                return;
            }
        }
    }

    /**
     * 用于状态转换：将"正在响应"转为"历史响应"时隐藏思考区
     */
    public void clearThinkContentInResponse() {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("response".equals(msg.getRole())) {
                // 清除 thinkContent，这样 bind 方法会隐藏思考区
                msg.setThinkContent(null);
                notifyItemChanged(i);
                return;
            }
        }
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
                // 使用 Markwon 渲染 Markdown，处理 null 情况
                String content = message.getContent();
                if (content != null) {
                    markwon.setMarkdown(tvContent, content);
                } else {
                    tvContent.setText("");
                }
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
        }

        void bind(String content) {
            tvThinking.setText(content);
        }

        void bindMessage(Message message) {
            if (tvThinking == null) {
                return;
            }
            String content = message.getContent();
            if (content == null) {
                content = "";
            }
            tvThinking.setText(content);
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

    /**
     * 错误消息 ViewHolder
     */
    static class ErrorViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvErrorCode;
        private final TextView tvErrorMessage;

        ErrorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvErrorCode = itemView.findViewById(R.id.tvErrorCode);
            tvErrorMessage = itemView.findViewById(R.id.tvErrorMessage);
        }

        void bind(String errorCode, String errorMessage) {
            tvErrorCode.setText(errorCode != null ? errorCode : "Error");
            tvErrorMessage.setText(errorMessage != null ? errorMessage : "Unknown error");
        }
    }

    /**
     * 统一响应卡片 ViewHolder：包含工具区、think区、正文区
     */
    static class ResponseCardViewHolder extends RecyclerView.ViewHolder {

        private final View toolArea;
        private final LinearLayout toolListContainer;
        private final View thinkArea;
        private final TextView tvThink;
        private final TextView tvContent;
        private final TextView tvTimestamp;
        private final TextView tvRole;
        private final SimpleDateFormat timeFormat;
        private final Markwon markwon;
        private Message currentMessage;

        ResponseCardViewHolder(@NonNull View itemView, SimpleDateFormat timeFormat, Markwon markwon) {
            super(itemView);
            this.timeFormat = timeFormat;
            this.markwon = markwon;

            toolArea = itemView.findViewById(R.id.toolArea);
            toolListContainer = itemView.findViewById(R.id.toolListContainer);
            thinkArea = itemView.findViewById(R.id.thinkArea);
            tvThink = itemView.findViewById(R.id.tvThink);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvRole = itemView.findViewById(R.id.tvRole);
        }

        void bind(Message message) {
            this.currentMessage = message;

            // 设置时间戳
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            tvRole.setText("助手");

            // 工具区：显示工具调用列表
            List<ToolCall> toolCalls = message.getToolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                showToolAreaList(toolCalls);
            } else {
                hideToolArea();
            }

            // Think 区：从 message 的扩展字段获取 think 内容
            String thinkContent = message.getThinkContent();
            if (thinkContent != null && !thinkContent.isEmpty()) {
                showThinkArea(thinkContent);
            } else {
                hideThinkArea();
            }

            // 正文区：使用 Markwon 渲染 Markdown
            String content = message.getContent();
            if (content != null) {
                markwon.setMarkdown(tvContent, content);
            }
        }

        /**
         * 显示工具区
         * @param toolName 工具名称
         * @param status 状态文本（如"工作中..."、"完成"）
         */
        /**
         * 显示工具列表
         */
        void showToolAreaList(List<ToolCall> toolCalls) {
            toolArea.setVisibility(View.VISIBLE);
            toolListContainer.removeAllViews();

            for (ToolCall toolCall : toolCalls) {
                addToolView(toolCall);
            }
        }

        /**
         * 添加单个工具视图
         */
        private void addToolView(ToolCall toolCall) {
            Context context = itemView.getContext();
            LinearLayout toolItem = new LinearLayout(context);
            toolItem.setOrientation(LinearLayout.HORIZONTAL);
            toolItem.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            toolItem.setPadding(0, 4, 0, 4);

            // 工具图标
            ImageView ivIcon = new ImageView(context);
            ivIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            ivIcon.setLayoutParams(new LinearLayout.LayoutParams(24, 24));
            toolItem.addView(ivIcon);

            // 工具状态文本
            TextView tvStatus = new TextView(context);
            String statusText;
            if ("completed".equals(toolCall.getStatus())) {
                statusText = toolCall.getName() + " 已完成调用";
            } else {
                statusText = "正在使用: " + toolCall.getName();
            }
            tvStatus.setText(statusText);
            tvStatus.setTextSize(12);
            tvStatus.setTextColor(0xFF666666);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvParams.setMargins(8, 0, 0, 0);
            tvStatus.setLayoutParams(tvParams);
            tvStatus.setTag(toolCall.getId()); // 用 ID 作为 tag，方便后续更新
            toolItem.addView(tvStatus);

            toolListContainer.addView(toolItem);
        }

        /**
         * 更新工具状态
         */
        void updateToolStatus(String toolId, String newStatus) {
            if (currentMessage == null) return;

            // 更新数据模型
            ToolCall toolCall = currentMessage.getToolCall(toolId);
            if (toolCall != null) {
                toolCall.setStatus(newStatus);
            }

            // 刷新 UI
            List<ToolCall> toolCalls = currentMessage.getToolCalls();
            showToolAreaList(toolCalls);
        }

        /**
         * 隐藏工具区
         */
        void hideToolArea() {
            toolArea.setVisibility(View.GONE);
        }

        /**
         * 显示 think 区
         * @param content think 内容
         */
        void showThinkArea(String content) {
            thinkArea.setVisibility(View.VISIBLE);
            tvThink.setText(content);
        }

        /**
         * 隐藏 think 区
         */
        void hideThinkArea() {
            thinkArea.setVisibility(View.GONE);
        }
    }

    /**
     * 添加错误消息
     * @param errorCode 错误代码
     * @param errorMessage 错误消息内容
     */
    public void addErrorMessage(String errorCode, String errorMessage) {
        Message message = new Message();
        message.setRole("error");
        message.setName(errorCode);
        message.setContent(errorMessage);
        message.setTimestamp(System.currentTimeMillis());
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    /**
     * 添加响应消息（用于流式更新）
     * @param message 响应消息对象
     */
    public void addResponseMessage(Message message) {
        this.messages.add(message);
        notifyItemInserted(this.messages.size() - 1);
    }

    /**
     * 更新响应消息（用于流式更新）
     * @param message 响应消息对象
     */
    public void updateResponseMessage(Message message) {
        // 根据时间戳查找当前正在更新的 response 消息
        long timestamp = message.getTimestamp();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("response".equals(msg.getRole()) && msg.getTimestamp() == timestamp) {
                // 更新消息内容
                msg.setContent(message.getContent());
                msg.setThinkContent(message.getThinkContent());
                msg.setToolCalls(message.getToolCalls());
                notifyItemChanged(i);
                return;
            }
        }
        // 如果没找到，添加新消息
        addResponseMessage(message);
    }

    /**
     * 移除所有 AI 相关消息（thinking, tool_use, tool_result, assistant）
     * 保留用户消息
     */
    public void removeAiMessages() {
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            String role = msg.getRole();
            if ("thinking".equals(role) || "tool_use".equals(role) ||
                "tool_result".equals(role) || "assistant".equals(role)) {
                messages.remove(i);
                removed = true;
            }
        }
        if (removed) {
            notifyDataSetChanged();
        }
    }
}
