package com.codex.apk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.codex.apk.ai.StormyResponseParser;
import java.util.List;
import java.util.Map;
import io.github.kbiakov.codeview.CodeView;
import io.github.kbiakov.codeview.adapters.Options;
import io.github.kbiakov.codeview.highlight.ColorTheme;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages;
    private final Context context;
    private OnAiActionInteractionListener aiActionInteractionListener;
    private final boolean agentModeEnabled;

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;

    public interface OnAiActionInteractionListener {
        void onApprovalResponse(boolean approved, String toolName, Map<String, Object> args);
    }

    public ChatMessageAdapter(Context context, List<ChatMessage> messages, boolean agentModeEnabled) {
        this.context = context;
        this.messages = messages;
        this.agentModeEnabled = agentModeEnabled;
    }

    public void setOnAiActionInteractionListener(OnAiActionInteractionListener listener) {
        this.aiActionInteractionListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getSender() == ChatMessage.SENDER_USER ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_message, parent, false);
            return new AiMessageViewHolder(view, aiActionInteractionListener);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder.getItemViewType() == VIEW_TYPE_USER) {
            ((UserMessageViewHolder) holder).bind(message);
        } else {
            ((AiMessageViewHolder) holder).bind(message, position);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message_content);
        }

        void bind(ChatMessage message) {
            textMessage.setText(message.getContent());
        }
    }

    class AiMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        RecyclerView toolCallsContainer;
        private final OnAiActionInteractionListener listener;
        private final Context context;

        AiMessageViewHolder(View itemView, OnAiActionInteractionListener listener) {
            super(itemView);
            this.listener = listener;
            this.context = itemView.getContext();
            textMessage = itemView.findViewById(R.id.text_message);
            toolCallsContainer = itemView.findViewById(R.id.tool_calls_container);
        }

        void bind(ChatMessage message, int messagePosition) {
            textMessage.setText(message.getContent());
            List<StormyResponseParser.ToolCall> toolCalls = new StormyResponseParser().parse(message.getRawAiResponseJson());

            if (toolCalls.isEmpty()) {
                toolCallsContainer.setVisibility(View.GONE);
            } else {
                toolCallsContainer.setVisibility(View.VISIBLE);
                toolCallsContainer.setLayoutManager(new LinearLayoutManager(context));
                toolCallsContainer.setAdapter(new ToolCallAdapter(context, toolCalls, listener, agentModeEnabled));
            }
        }
    }

    static class ToolCallAdapter extends RecyclerView.Adapter<ToolCallAdapter.ToolCallViewHolder> {
        private final List<StormyResponseParser.ToolCall> toolCalls;
        private final OnAiActionInteractionListener listener;
        private final Context context;
        private final boolean agentModeEnabled;

        ToolCallAdapter(Context context, List<StormyResponseParser.ToolCall> toolCalls, OnAiActionInteractionListener listener, boolean agentModeEnabled) {
            this.context = context;
            this.toolCalls = toolCalls;
            this.listener = listener;
            this.agentModeEnabled = agentModeEnabled;
        }

        @NonNull
        @Override
        public ToolCallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool_call, parent, false);
            return new ToolCallViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ToolCallViewHolder holder, int position) {
            holder.bind(toolCalls.get(position));
        }

        @Override
        public int getItemCount() {
            return toolCalls.size();
        }

        class ToolCallViewHolder extends RecyclerView.ViewHolder {
            TextView toolName;
            CodeView codeView;
            View approvalLayout;
            Button approveButton;
            Button denyButton;

            ToolCallViewHolder(View itemView) {
                super(itemView);
                toolName = itemView.findViewById(R.id.tool_name);
                codeView = itemView.findViewById(R.id.code_view);
                approvalLayout = itemView.findViewById(R.id.approval_layout);
                approveButton = itemView.findViewById(R.id.approve_button);
                denyButton = itemView.findViewById(R.id.deny_button);
            }

            void bind(StormyResponseParser.ToolCall toolCall) {
                toolName.setText(toolCall.getName());
                String content = getContent(toolCall.getArgs());

                codeView.setOptions(Options.Default.get(context)
                        .withLanguage("diff")
                        .withCode(content)
                        .withTheme(ColorTheme.MONOKAI));

                if (!agentModeEnabled && isFileSystemTool(toolCall.getName())) {
                    approvalLayout.setVisibility(View.VISIBLE);
                    approveButton.setOnClickListener(v -> {
                        listener.onApprovalResponse(true, toolCall.getName(), toolCall.getArgs());
                        approvalLayout.setVisibility(View.GONE);
                    });
                    denyButton.setOnClickListener(v -> {
                        listener.onApprovalResponse(false, toolCall.getName(), toolCall.getArgs());
                        approvalLayout.setVisibility(View.GONE);
                    });
                } else {
                    approvalLayout.setVisibility(View.GONE);
                }
            }

            private String getContent(Map<String, Object> args) {
                switch (toolName.getText().toString()) {
                    case "write_to_file":
                        return (String) args.get("content");
                    case "replace_in_file":
                        return (String) args.get("diff");
                    default:
                        return args.toString();
                }
            }

            private boolean isFileSystemTool(String toolName) {
                switch (toolName) {
                    case "write_to_file":
                    case "replace_in_file":
                    case "rename_file":
                    case "delete_file":
                    case "copy_file":
                    case "move_file":
                        return true;
                    default:
                        return false;
                }
            }
        }
    }
}
