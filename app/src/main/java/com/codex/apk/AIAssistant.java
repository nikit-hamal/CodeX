package com.codex.apk;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import com.codex.apk.ai.AIModel;
import com.codex.apk.ai.AIProvider;

public class AIAssistant {

    private ApiClient apiClient;
    private AIModel currentModel;
    private boolean thinkingModeEnabled = false;
    private boolean webSearchEnabled = false;
    private boolean agentModeEnabled = true; // New agent mode flag
    private List<ToolSpec> enabledTools = new ArrayList<>();
    private AIAssistant.AIActionListener actionListener;
    private File projectDir; // Track project directory for tool operations

    public AIAssistant(Context context, ExecutorService executorService, AIActionListener actionListener) {
        this.actionListener = actionListener;
        this.currentModel = AIModel.fromModelId("qwen3-coder-plus");
        initializeApiClient(context, null);
    }

    // Legacy constructor for compatibility
    public AIAssistant(Context context, String apiKey, File projectDir, String projectName,
        ExecutorService executorService, AIActionListener actionListener) {
        this.actionListener = actionListener;
        this.currentModel = AIModel.fromModelId("qwen3-coder-plus");
        this.projectDir = projectDir;
        initializeApiClient(context, projectDir);
    }

    private void initializeApiClient(Context context, File projectDir) {
        apiClient = new QwenApiClient(context, actionListener, projectDir);
    }

    public void sendPrompt(String userPrompt, List<ChatMessage> chatHistory, QwenConversationState qwenState, String fileName, String fileContent) {
        // For now, attachments are not handled in this refactored version.
        // This would need to be threaded through if a model that uses them is selected.
        sendMessage(userPrompt, chatHistory, qwenState, new ArrayList<>(), fileName, fileContent);
    }

    public void sendMessage(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments) {
        sendMessage(message, chatHistory, qwenState, attachments, null, null);
    }

    public void sendMessage(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments, String fileName, String fileContent) {
        // This method is now deprecated, route to streaming.
        sendMessageStreaming(message, chatHistory, qwenState, attachments, fileName, fileContent);
    }

    public void sendMessageStreaming(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments, String fileName, String fileContent) {
        if (apiClient instanceof StreamingApiClient) {
             String finalMessage = message;
            if (fileContent != null && !fileContent.isEmpty()) {
                finalMessage = "File `" + fileName + "`:\n```\n" + fileContent + "\n```\n\n" + message;
            }

            String system = StormyPromptManager.getSystemPrompt();
            if (system != null && !system.isEmpty()) {
                finalMessage = system + "\n\n" + finalMessage;
            }

            StreamingApiClient.MessageRequest request = new StreamingApiClient.MessageRequest.Builder()
                .message(finalMessage)
                .history(chatHistory)
                .model(currentModel)
                .conversationState(qwenState)
                .thinkingModeEnabled(thinkingModeEnabled)
                .webSearchEnabled(webSearchEnabled)
                .enabledTools(enabledTools)
                .attachments(attachments)
                .build();

            ((StreamingApiClient) apiClient).sendMessageStreaming(request, (StreamingApiClient.StreamListener) actionListener);
        } else {
            if (actionListener != null) {
                actionListener.onAiError("API client for " + currentModel.getProvider() + " not found.");
            }
        }
    }

    public interface RefreshCallback {
        void onRefreshComplete(boolean success, String message);
    }

    public interface AIActionListener {
        void onAiActionsProcessed(String rawAiResponseJson, String explanation, List<String> suggestions,
                                 List<ChatMessage.FileActionDetail> proposedFileChanges, String aiModelDisplayName);
        void onAiActionsProcessed(String rawAiResponseJson, String explanation, List<String> suggestions,
                                 List<ChatMessage.FileActionDetail> proposedFileChanges,
                                 List<ChatMessage.PlanStep> planSteps,
                                 String aiModelDisplayName);
        void onAiError(String errorMessage);
        void onAiRequestStarted();
        void onAiStreamUpdate(String partialResponse, boolean isThinking);
        void onAiRequestCompleted();
        void onQwenConversationStateUpdated(QwenConversationState state);
    }

    // Getters and Setters
    public AIModel getCurrentModel() { return currentModel; }
    public void setCurrentModel(AIModel model) { this.currentModel = model; }
    public boolean isThinkingModeEnabled() { return thinkingModeEnabled; }
    public void setThinkingModeEnabled(boolean enabled) { this.thinkingModeEnabled = enabled; }
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public void setWebSearchEnabled(boolean enabled) { this.webSearchEnabled = enabled; }
    public boolean isAgentModeEnabled() { return agentModeEnabled; }
    public void setAgentModeEnabled(boolean enabled) { this.agentModeEnabled = enabled; }
    public void setEnabledTools(List<ToolSpec> tools) { this.enabledTools = tools; }
    public void setActionListener(AIActionListener listener) { this.actionListener = listener; }
    public void shutdown() {}
}
