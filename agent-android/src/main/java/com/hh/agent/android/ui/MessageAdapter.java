package com.hh.agent.android.ui;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.hh.agent.android.R;
import com.hh.agent.core.model.Message;
import com.hh.agent.core.model.ToolCall;

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
    private static String TAG = "MessageAdapter";

    public static final int VIEW_TYPE_USER = 0;
    public static final int VIEW_TYPE_THINKING = 1;
    public static final int VIEW_TYPE_ERROR = 2;
    public static final int VIEW_TYPE_RESPONSE = 3;

    private List<Message> messages = new ArrayList<>();
    private Long activeToolUiResponseTimestamp = null;
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
        } else if (holder instanceof ErrorViewHolder) {
            ((ErrorViewHolder) holder).bind(message.getName(), message.getContent());
        } else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(message);
        } else if (holder instanceof ResponseCardViewHolder) {
            ((ResponseCardViewHolder) holder).bind(message, shouldShowToolUi(message));
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
        } else if ("error".equals(role)) {
            return VIEW_TYPE_ERROR;
        } else if ("response".equals(role) || "assistant".equals(role)) {
            // response: 流式响应消息
            // assistant: 历史消息（需要用 ResponseCardViewHolder 统一展示）
            return VIEW_TYPE_RESPONSE;
        } else {
            return VIEW_TYPE_USER; // 默认使用用户消息样式
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
        activeToolUiResponseTimestamp = null;
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
     * 清除响应卡片中的思考内容
     * 用于状态转换：将"正在响应"转为"历史响应"时隐藏思考区
     * @param timestamp 消息时间戳，用于精确匹配
     */
    public void clearThinkContentInResponse(long timestamp) {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("response".equals(msg.getRole()) && msg.getTimestamp() == timestamp) {
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
        private final LayoutInflater layoutInflater;
        private Message currentMessage;

        ResponseCardViewHolder(@NonNull View itemView, SimpleDateFormat timeFormat, Markwon markwon) {
            super(itemView);
            this.timeFormat = timeFormat;
            this.markwon = markwon;
            this.layoutInflater = LayoutInflater.from(itemView.getContext());

            toolArea = itemView.findViewById(R.id.toolArea);
            toolListContainer = itemView.findViewById(R.id.toolListContainer);
            thinkArea = itemView.findViewById(R.id.thinkArea);
            tvThink = itemView.findViewById(R.id.tvThink);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvRole = itemView.findViewById(R.id.tvRole);
        }

        void bind(Message message, boolean shouldShowToolUi) {
            this.currentMessage = message;

            // 设置时间戳
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            tvRole.setText("助手");

            // 工具区：显示工具调用列表
            List<ToolCall> visibleToolCalls = getVisibleToolCalls(message);
            if (shouldShowToolUi && !visibleToolCalls.isEmpty()) {
                showToolAreaList(visibleToolCalls);
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
                Log.d(TAG, "ResponseCardViewHolder bind content: text = " + content);
                markwon.setMarkdown(tvContent, content);
            } else {
                markwon.setMarkdown(tvContent, "");
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
            String toolTitle = getToolTitle(toolCall);
            View toolItem = layoutInflater.inflate(R.layout.item_tool_status, toolListContainer, false);
            LinearLayout toolItemRoot = toolItem.findViewById(R.id.toolItemRoot);
            FrameLayout toolIconContainer = toolItem.findViewById(R.id.toolIconContainer);
            ImageView ivStatusIcon = toolItem.findViewById(R.id.ivToolStatusIcon);
            TextView tvStatus = toolItem.findViewById(R.id.tvToolStatus);
            TextView tvTitle = toolItem.findViewById(R.id.tvToolTitle);
            TextView tvDescription = toolItem.findViewById(R.id.tvToolDescription);

            boolean completed = "completed".equals(toolCall.getStatus());
            tvStatus.setTag(toolCall.getId());
            tvStatus.setText(completed ? "已完成" : "执行中");
            tvTitle.setText(toolTitle);

            String description = getToolDescription(toolCall);
            if (description.isEmpty()) {
                tvDescription.setVisibility(View.GONE);
            } else {
                tvDescription.setVisibility(View.VISIBLE);
                tvDescription.setText(description);
            }

            bindToolVisualState(context, toolItemRoot, toolIconContainer, ivStatusIcon, tvStatus, completed);
            toolListContainer.addView(toolItem);
        }

        private void bindToolVisualState(
                Context context,
                LinearLayout toolItemRoot,
                FrameLayout toolIconContainer,
                ImageView ivStatusIcon,
                TextView tvStatus,
                boolean completed
        ) {
            toolIconContainer.clearAnimation();
            toolIconContainer.setAlpha(1f);
            toolIconContainer.setScaleX(1f);
            toolIconContainer.setScaleY(1f);
            if (completed) {
                toolItemRoot.setBackgroundResource(R.drawable.bg_tool_result);
                toolIconContainer.setBackgroundResource(R.drawable.bg_tool_icon_result);
                ivStatusIcon.setImageResource(R.drawable.ic_result);
                tvStatus.setTextColor(0xFF5F6B76);
                return;
            }

            toolItemRoot.setBackgroundResource(R.drawable.bg_tool_use);
            toolIconContainer.setBackgroundResource(R.drawable.bg_tool_icon_active);
            ivStatusIcon.setImageResource(R.drawable.ic_tool);
            tvStatus.setTextColor(0xFF1E6FB8);
            Animation activeAnimation = AnimationUtils.loadAnimation(context, R.anim.tool_status_active);
            toolIconContainer.startAnimation(activeAnimation);
        }

        private String getToolTitle(ToolCall toolCall) {
            if (toolCall == null) {
                return "";
            }
            String title = toolCall.getTitle();
            if (title != null && !title.trim().isEmpty()) {
                return title.trim();
            }
            return "";
        }

        private String getToolDescription(ToolCall toolCall) {
            if (toolCall == null) {
                return "";
            }
            String description = toolCall.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                return description.trim();
            }
            return "";
        }

        private List<ToolCall> getVisibleToolCalls(Message message) {
            List<ToolCall> visibleToolCalls = new ArrayList<>();
            if (message == null) {
                return visibleToolCalls;
            }
            List<ToolCall> toolCalls = message.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return visibleToolCalls;
            }
            for (ToolCall toolCall : toolCalls) {
                if (toolCall != null && toolCall.isVisibleInToolUi()) {
                    visibleToolCalls.add(toolCall);
                }
            }
            return visibleToolCalls;
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
            List<ToolCall> visibleToolCalls = getVisibleToolCalls(currentMessage);
            if (!visibleToolCalls.isEmpty()) {
                showToolAreaList(visibleToolCalls);
            } else {
                hideToolArea();
            }
        }

        /**
         * 隐藏工具区
         */
        void hideToolArea() {
            toolArea.setVisibility(View.GONE);
            toolListContainer.removeAllViews();
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
                msg.setShowToolUi(message.isShowToolUi());
                msg.setToolCalls(message.getToolCalls());
                notifyItemChanged(i);
                return;
            }
        }
        // 如果没找到，添加新消息
        addResponseMessage(message);
    }

    /**
     * 移除所有 AI 相关消息（thinking, response）
     * 保留用户消息
     */
    public void removeAiMessages() {
        boolean removed = false;
        activeToolUiResponseTimestamp = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            String role = msg.getRole();
            if ("thinking".equals(role) || "response".equals(role)) {
                messages.remove(i);
                removed = true;
            }
        }
        if (removed) {
            notifyDataSetChanged();
        }
    }

    public void setActiveToolUiResponse(long timestamp) {
        if (activeToolUiResponseTimestamp != null && activeToolUiResponseTimestamp == timestamp) {
            return;
        }
        activeToolUiResponseTimestamp = timestamp;
        notifyDataSetChanged();
    }

    public void clearActiveToolUiResponse() {
        if (activeToolUiResponseTimestamp == null) {
            return;
        }
        activeToolUiResponseTimestamp = null;
        notifyDataSetChanged();
    }

    private boolean shouldShowToolUi(Message message) {
        if (message == null || !message.isShowToolUi()) {
            return false;
        }
        if (!"response".equals(message.getRole())) {
            return false;
        }
        return activeToolUiResponseTimestamp != null
                && activeToolUiResponseTimestamp == message.getTimestamp();
    }

}
