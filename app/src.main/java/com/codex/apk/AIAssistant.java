package com.codex.apk;

import android.content.Context;
import com.codex.apk.ai.AIModel;
import com.codex.apk.ai.StormyPromptManager;
import com.codex.apk.ai.StormyResponseParser;
import com.codex.apk.ai.ToolExecutor;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class AIAssistant {

    private ToolExecutor toolExecutor;
    private final StormyResponseParser responseParser;
    private ApiClient apiClient;
    private AIModel currentModel;
    private boolean agentModeEnabled = true; // New agent mode flag
    private AIActionListener actionListener;
    private File projectDir; // Track project directory for tool operations
    private Context context;

    public AIAssistant(Context context, ExecutorService executorService, AIActionListener actionListener) {
        this.context = context;
        this.actionListener = actionListener;
        this.currentModel = AIModel.fromModelId("qwen3-coder-plus");
        this.responseParser = new StormyResponseParser();
        initializeApiClient(context, null);
    }

    // Legacy constructor for compatibility
    public AIAssistant(Context context, String apiKey, File projectDir, String projectName,
        ExecutorService executorService, AIActionListener actionListener) {
        this.context = context;
        this.actionListener = actionListener;
        this.currentModel = AIModel.fromModelId("qwen3-coder-plus");
        this.projectDir = projectDir;
        this.responseParser = new StormyResponseParser();
        initializeApiClient(context, projectDir);
    }

    private ToolExecutor getToolExecutor() {
        if (toolExecutor == null && projectDir != null) {
            toolExecutor = new ToolExecutor(context, projectDir.getAbsolutePath());
        }
        return toolExecutor;
    }

    private void initializeApiClient(Context context, File projectDir) {
        apiClient = new QwenApiClient(context, actionListener, projectDir);
    }

    public void sendMessageStreaming(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments, String fileName, String fileContent) {
        if (apiClient instanceof StreamingApiClient) {
            String finalMessage = message;
            if (fileContent != null && !fileContent.isEmpty()) {
                finalMessage = "File `" + fileName + "`:\n```\n" + fileContent + "\n```\n\n" + message;
            }

            JsonObject systemMessage = StormyPromptManager.createSystemMessage();
            finalMessage = systemMessage.get("content").getAsString() + "\n\n" + finalMessage;

            StreamingApiClient.MessageRequest request = new StreamingApiClient.MessageRequest.Builder()
                .message(finalMessage)
                .history(chatHistory)
                .model(currentModel)
                .conversationState(qwenState)
                .attachments(attachments)
                .build();

            ((StreamingApiClient) apiClient).sendMessageStreaming(request, new StreamingApiClient.StreamListener() {
                private StringBuilder fullResponse = new StringBuilder();

                @Override
                public void onAiStreamUpdate(String partialResponse, boolean isThinking) {
                    fullResponse.append(partialResponse);
                    if (actionListener != null) {
                        actionListener.onAiStreamUpdate(partialResponse, isThinking);
                    }
                }

                @Override
                public void onAiRequestCompleted() {
                    if (actionListener != null) {
                        actionListener.onAiRequestCompleted();
                    }
                    processResponse(fullResponse.toString(), chatHistory, qwenState, attachments);
                }

                @Override
                public void onAiError(String message) {
                    if(actionListener != null) {
                        actionListener.onAiError(message);
                    }
                }
                 @Override
                public void onQwenConversationStateUpdated(QwenConversationState state) {
                    if (actionListener != null) {
                        actionListener.onQwenConversationStateUpdated(state);
                    }
                }
            });
        } else {
            if (actionListener != null) {
                actionListener.onAiError("API client for " + currentModel.getProvider() + " not found.");
            }
        }
    }

    private void processResponse(String response, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments) {
        List<StormyResponseParser.ToolCall> toolCalls = responseParser.parse(response);

        if (toolCalls.isEmpty()) {
            // No tool calls, we are done with this iteration. The response is final.
            return;
        }

        chatHistory.add(new ChatMessage("assistant", response));

        for (StormyResponseParser.ToolCall toolCall : toolCalls) {
            switch (toolCall.getName()) {
                case "ask_followup_question":
                    if (actionListener != null) {
                        actionListener.onFollowupQuestion(toolCall.getArgs().get("question").toString());
                    }
                    return;
                case "attempt_completion":
                    if (actionListener != null) {
                        actionListener.onCompletion(toolCall.getArgs().get("summary").toString());
                    }
                    return;
                default:
                    if (!agentModeEnabled && isFileSystemTool(toolCall.getName())) {
                        if (actionListener != null) {
                            actionListener.onApprovalRequired(toolCall.getName(), toolCall.getArgs());
                        }
                        // Stop processing further tools until approval is given.
                        // The UI layer would then call a method to resume with the approved tool execution.
                        return;
                    } else {
                         if (actionListener != null) {
                            actionListener.onToolCall(toolCall.getName(), toolCall.getArgs());
                        }
                        String result = getToolExecutor().execute(toolCall.getName(), toolCall.getArgs());
                        chatHistory.add(new ChatMessage("tool", result));
                    }
            }
        }

        // After executing tools, send the history back to the model.
        sendMessageStreaming("", chatHistory, qwenState, attachments, null, null);
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


    public interface RefreshCallback {
        void onRefreshComplete(boolean success, String message);
    }

    public interface AIActionListener extends StreamingApiClient.StreamListener {
        void onAiError(String errorMessage);
        void onAiRequestStarted();
        void onAiRequestCompleted();
        void onQwenConversationStateUpdated(QwenConversationState state);
        void onToolCall(String toolName, Map<String, Object> args);
        void onApprovalRequired(String toolName, Map<String, Object> args);
        void onFollowupQuestion(String question);
        void onCompletion(String summary);
    }

    // Getters and Setters
    public AIModel getCurrentModel() { return currentModel; }
    public void setCurrentModel(AIModel model) { this.currentModel = model; }
    public boolean isAgentModeEnabled() { return agentModeEnabled; }
    public void setAgentModeEnabled(boolean enabled) { this.agentModeEnabled = enabled; }
    public void setActionListener(AIActionListener listener) { this.actionListener = listener; }
    public void shutdown() {}
}
