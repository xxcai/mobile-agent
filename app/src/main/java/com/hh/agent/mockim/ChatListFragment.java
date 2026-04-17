package com.hh.agent.mockim;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hh.agent.ChatDetailActivity;
import com.hh.agent.R;
import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.viewcontext.ObservationTargetResolver;
import com.hh.agent.mockim.adapter.ChatConversationAdapter;
import com.hh.agent.mockim.model.ChatConversation;

import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListFragment extends Fragment {

    private static final Pattern NODE_MATCH_PATTERN = Pattern.compile(
            "<node[^>]*index=\\\"([^\\\"]+)\\\"[^>]*text=\\\"([^\\\"]*)\\\"[^>]*bounds=\\\"([^\\\"]+)\\\"[^>]*/?>");

    public static ChatListFragment newInstance() {
        return new ChatListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText searchInput = view.findViewById(R.id.chatSearchInput);
        ListView listView = view.findViewById(R.id.chatListView);
        TextView testTapConversationButton = view.findViewById(R.id.testTapConversationButton);
        TextView testScrollListButton = view.findViewById(R.id.testScrollListButton);
        TextView testInputSearchButton = view.findViewById(R.id.testInputSearchButton);
        List<ChatConversation> conversations = MockChatRepository.getConversations();
        listView.setAdapter(new ChatConversationAdapter(requireContext(), conversations));
        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            ChatConversation conversation = conversations.get(position);
            Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
            intent.putExtra(ChatDetailActivity.EXTRA_CONVERSATION_ID, conversation.getId());
            startActivity(intent);
        });
        testTapConversationButton.setOnClickListener(v -> runTapConversationCase());
        testScrollListButton.setOnClickListener(v -> runScrollListCase());
        testInputSearchButton.setOnClickListener(v -> runInputSearchCase(searchInput));
    }

    private void runTapConversationCase() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final String targetHint = "\u5f20\u4e09";
        showResultDialog(activity,
                "Tap Conversation",
                "# Tap Conversation\nstep_1=view_context\nstep_2=gesture_tap\nnote=waiting...");

        Thread worker = new Thread(() -> {
            String report;
            try {
                AndroidToolManager manager = buildToolManager(activity);
                String viewContextResult = manager.callTool(
                        "android_view_context_tool",
                        "{\"targetHint\":\"" + targetHint + "\"}");
                JSONObject viewContextJson = new JSONObject(viewContextResult);
                if (!viewContextJson.optBoolean("success", false)) {
                    report = "# Tap Conversation\nview_context_result=" + viewContextResult;
                } else {
                    String snapshotId = viewContextJson.optString("snapshotId", "");
                    ObservationTargetResolver.TargetReference nodeReference = ObservationTargetResolver.resolve(
                            viewContextJson,
                            viewContextJson.optString("targetHint", null));
                    String gestureArgs = buildTapGestureArgs(snapshotId, targetHint, 200, 420, nodeReference);
                    String gestureResult = manager.callTool("android_gesture_tool", gestureArgs);
                    report = "# Tap Conversation\n"
                            + "view_context_result=" + viewContextResult + "\n\n"
                            + "gesture_input=" + gestureArgs + "\n"
                            + "gesture_result=" + gestureResult;
                }
            } catch (Exception e) {
                report = "# Tap Conversation\nerror=" + e.getMessage();
            }
            String finalReport = report;
            mainHandler.post(() -> showResultDialog(activity, "Tap Conversation", finalReport));
        });
        worker.start();
    }

    private void runScrollListCase() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        showResultDialog(activity,
                "Scroll List",
                "# Scroll List\nstep_1=gesture_swipe_as_scroll\nnote=waiting...");

        Thread worker = new Thread(() -> {
            String report;
            try {
                AndroidToolManager manager = buildToolManager(activity);
                String gestureArgs = "{\"action\":\"swipe\",\"direction\":\"down\","
                        + "\"scope\":\"feed\",\"amount\":\"medium\",\"duration\":400}";
                String gestureResult = manager.callTool("android_gesture_tool", gestureArgs);
                report = "# Scroll List\n"
                        + "gesture_input=" + gestureArgs + "\n"
                        + "gesture_result=" + gestureResult;
            } catch (Exception e) {
                report = "# Scroll List\nerror=" + e.getMessage();
            }
            String finalReport = report;
            mainHandler.post(() -> showResultDialog(activity, "Scroll List", finalReport));
        });
        worker.start();
    }

    private void runInputSearchCase(EditText searchInput) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.runOnUiThread(() -> {
            searchInput.requestFocus();
            searchInput.setText("\u5f20\u4e09");
            searchInput.setSelection(searchInput.getText().length());
            showResultDialog(activity,
                    "Fill Search",
                    "# Fill Search\n"
                            + "input_view=chatSearchInput\n"
                            + "expected_text=\u5f20\u4e09\n"
                            + "actual_text=" + searchInput.getText());
        });
    }

    private AndroidToolManager buildToolManager(AppCompatActivity activity) {
        AndroidToolManager manager = new AndroidToolManager(activity);
        manager.initialize();
        return manager;
    }

    private String buildTapGestureArgs(String snapshotId,
                                       String targetHint,
                                       int fallbackX,
                                       int fallbackY,
                                       ObservationTargetResolver.TargetReference nodeReference) {
        StringBuilder gestureArgs = new StringBuilder();
        gestureArgs.append("{\"action\":\"tap\",\"x\":").append(fallbackX)
                .append(",\"y\":").append(fallbackY)
                .append(",\"observation\":{\"snapshotId\":\"").append(escape(snapshotId)).append("\"")
                .append(",\"targetDescriptor\":\"").append(escape(targetHint)).append("\"");
        if (nodeReference != null) {
            if (nodeReference.nodeIndex != null) {
                gestureArgs.append(",\"targetNodeIndex\":").append(nodeReference.nodeIndex);
            }
            if (nodeReference.bounds != null && !nodeReference.bounds.isEmpty()) {
                gestureArgs.append(",\"referencedBounds\":\"").append(escape(nodeReference.bounds)).append("\"");
            }
        }
        gestureArgs.append("}}");
        return gestureArgs.toString();
    }

    private NodeReference findNodeReference(String nativeViewXml, String targetHint) {
        Matcher matcher = NODE_MATCH_PATTERN.matcher(nativeViewXml);
        while (matcher.find()) {
            String nodeText = matcher.group(2);
            if (!targetHint.equals(nodeText)) {
                continue;
            }
            try {
                return new NodeReference(Integer.parseInt(matcher.group(1)), matcher.group(3));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void showResultDialog(AppCompatActivity activity, String title, String report) {
        TextView contentView = new TextView(activity);
        int padding = dp(16);
        contentView.setPadding(padding, padding, padding, padding);
        contentView.setTextIsSelectable(true);
        contentView.setMovementMethod(new ScrollingMovementMethod());
        contentView.setText(report);
        contentView.setTextSize(13f);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(contentView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class NodeReference {
        private final int nodeIndex;
        private final String bounds;

        private NodeReference(int nodeIndex, String bounds) {
            this.nodeIndex = nodeIndex;
            this.bounds = bounds;
        }
    }
}
