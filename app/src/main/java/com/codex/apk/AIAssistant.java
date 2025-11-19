package com.codex.apk;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import com.codex.apk.ai.AIModel;

public class AIAssistant implements StreamingApiClient.StreamListener {

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
        sendMessage(userPrompt, chatHistory, qwenState, new ArrayList<>(), fileName, fileContent);
    }

    public void sendMessage(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments) {
        sendMessage(message, chatHistory, qwenState, attachments, null, null);
    }

    public void sendMessage(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments, String fileName, String fileContent) {
        sendMessageStreaming(message, chatHistory, qwenState, attachments, fileName, fileContent);
    }

    public void sendMessageStreaming(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState, List<File> attachments, String fileName, String fileContent) {
        if (apiClient instanceof StreamingApiClient) {
             String finalMessage = message;
            if (fileContent != null && !fileContent.isEmpty()) {
                finalMessage = "File `" + fileName + "`:\n```\n" + fileContent + "\n```\n\n" + message;
            }

            String system = agentModeEnabled ? PromptManager.getDefaultFileOpsPrompt() : PromptManager.getDefaultGeneralPrompt();
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

            ((StreamingApiClient) apiClient).sendMessageStreaming(request, this);
        } else {
            if (actionListener != null) {
                actionListener.onAiError("API client for " + currentModel.getProvider() + " not found.");
            }
        }
    }

    // --- StreamListener Implementation ---

    @Override
    public void onStreamStarted(String requestId) {
        if (actionListener != null) actionListener.onAiRequestStarted();
    }

    @Override
    public void onStreamPartialUpdate(String requestId, String partialResponse, boolean isThinking) {
        if (actionListener != null) actionListener.onAiStreamUpdate(partialResponse, isThinking);
    }

    @Override
    public void onStreamCompleted(String requestId, QwenResponseParser.ParsedResponse response) {
        if (actionListener != null) {
            if (response.isValid) {
                // For now, we pass empty lists for legacy arguments as the new flow uses tool calls
                actionListener.onAiActionsProcessed(response.rawResponse, response.explanation, new ArrayList<>(), new ArrayList<>(), currentModel.getDisplayName());
            } else {
                actionListener.onAiActionsProcessed(response.rawResponse, response.explanation, new ArrayList<>(), new ArrayList<>(), currentModel.getDisplayName());
            }
            actionListener.onAiRequestCompleted();
        }
    }

    @Override
    public void onStreamError(String requestId, String errorMessage, Throwable throwable) {
        if (actionListener != null) actionListener.onAiError(errorMessage);
    }

    @Override
    public void onToolExecutionRequest(String requestId, List<ChatMessage.ToolUsage> toolUsages, StreamingApiClient.ToolExecutionCallback callback) {
        if (agentModeEnabled) {
            // Full autonomy: proceed immediately
            callback.onProceed();
        } else {
            // Approval mode: check for unsafe tools
            boolean hasUnsafeTools = false;
            for (ChatMessage.ToolUsage usage : toolUsages) {
                if (isUnsafeTool(usage.toolName)) {
                    hasUnsafeTools = true;
                    break;
                }
            }

            if (hasUnsafeTools) {
                // Request user approval via UI listener
                if (actionListener instanceof OnToolApprovalListener) {
                    ((OnToolApprovalListener) actionListener).onToolApprovalRequest(toolUsages, callback);
                } else {
                    // Fallback if listener doesn't support approval: proceed
                    callback.onProceed(); 
                }
            } else {
                // Safe tools only: proceed
                callback.onProceed();
            }
        }
    }

    private boolean isUnsafeTool(String toolName) {
        // Define tools that modify the file system
        return "write_to_file".equals(toolName) ||
               "replace_in_file".equals(toolName) ||
               "delete_file".equals(toolName) ||
               "rename_file".equals(toolName) ||
               "copy_file".equals(toolName) ||
               "move_file".equals(toolName);
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

    // New interface for tool approval
    public interface OnToolApprovalListener extends AIActionListener {
        void onToolApprovalRequest(List<ChatMessage.ToolUsage> toolUsages, StreamingApiClient.ToolExecutionCallback callback);
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
