package com.codex.apk;

import android.content.Context;
import com.codex.apk.ai.AIModel;
import com.codex.apk.tools.Tool;
import com.codex.apk.tools.ToolRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AIAssistant {

    private final ApiClient apiClient;
    private AIModel currentModel;
    private boolean agentModeEnabled = true;
    private final AIAssistant.AIActionListener actionListener;
    private final File projectDir;
    private final ExecutorService executorService;
    private final Gson gson = new Gson();

    public AIAssistant(Context context, ExecutorService executorService, AIActionListener actionListener, File projectDir) {
        this.actionListener = actionListener;
        this.currentModel = AIModel.fromModelId("qwen3-coder-plus");
        this.projectDir = projectDir;
        this.executorService = executorService;
        this.apiClient = new QwenApiClient(context, actionListener, projectDir);
    }

    public void startAgenticLoop(String userPrompt, List<ChatMessage> chatHistory, QwenConversationState qwenState) {
        executorService.submit(() -> {
            runAgenticLoop(userPrompt, chatHistory, qwenState);
        });
    }

    private void runAgenticLoop(String message, List<ChatMessage> chatHistory, QwenConversationState qwenState) {
        if (!(apiClient instanceof StreamingApiClient)) {
            actionListener.onAiError("API client is not a StreamingApiClient.");
            return;
        }

        StreamingApiClient streamingApiClient = (StreamingApiClient) apiClient;
        boolean taskComplete = false;

        // Add the initial user message to the history
        chatHistory.add(new ChatMessage(message, ChatMessage.SENDER_USER));
        actionListener.onHistoryUpdate(chatHistory);

        while (!taskComplete) {
            StreamingApiClient.MessageRequest request = new StreamingApiClient.MessageRequest.Builder()
                .message(message)
                .history(chatHistory)
                .model(currentModel)
                .conversationState(qwenState)
                .build();

            // This is a synchronous call for simplicity in the loop.
            // The actual implementation would need to handle the streaming response.
            String rawResponse = streamingApiClient.sendMessageSynchronous(request); // Assuming this method exists for simplicity

            if (rawResponse == null || rawResponse.isEmpty()) {
                actionListener.onAiError("Received an empty response from the AI.");
                break;
            }

            try {
                JsonArray toolCalls = JsonParser.parseString(rawResponse).getAsJsonArray();
                List<JsonObject> toolResults = new ArrayList<>();

                for (JsonElement toolCallElement : toolCalls) {
                    JsonObject toolCall = toolCallElement.getAsJsonObject();
                    String toolName = toolCall.get("name").getAsString();
                    JsonObject args = toolCall.getAsJsonObject("args");

                    Tool tool = ToolRegistry.getTool(toolName);
                    if (tool == null) {
                        actionListener.onAiError("Unknown tool: " + toolName);
                        continue;
                    }

                    // Add a message to the UI to show the tool call
                    actionListener.onToolCall(toolName, args.toString());

                    // Implement Agent Mode logic
                    if (!agentModeEnabled && isFileSystemTool(toolName)) {
                        boolean approved = actionListener.requestUserApproval(toolName, args.toString());
                        if (!approved) {
                            actionListener.onToolSkipped(toolName);
                            continue; // Skip this tool call
                        }
                    }

                    JsonObject result = tool.execute(projectDir, args);
                    toolResults.add(result);

                    // Add a message to the UI to show the tool result
                    actionListener.onToolResult(toolName, result.toString());

                    if ("attempt_completion".equals(toolName)) {
                        taskComplete = true;
                        break;
                    }
                    if ("ask_followup_question".equals(toolName)) {
                        // The loop will break and wait for the next user message
                        taskComplete = true;
                        break;
                    }
                }

                if (!taskComplete) {
                    // Prepare the next message with the tool results
                    message = gson.toJson(toolResults);
                    chatHistory.add(new ChatMessage(message, ChatMessage.SENDER_AI_TOOL_RESULT));
                    actionListener.onHistoryUpdate(chatHistory);
                }

            } catch (Exception e) {
                actionListener.onAiError("Error processing AI response: " + e.getMessage());
                taskComplete = true;
            }
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

    public interface AIActionListener extends StreamingApiClient.StreamListener {
        void onHistoryUpdate(List<ChatMessage> chatHistory);
        boolean requestUserApproval(String toolName, String args);
        void onToolCall(String toolName, String args);
        void onToolResult(String toolName, String result);
        void onToolSkipped(String toolName);
        void onAiError(String errorMessage);
        void onAiRequestStarted();
        void onAiRequestCompleted();
    }

    // Getters and Setters
    public void setAgentModeEnabled(boolean enabled) { this.agentModeEnabled = enabled; }
    public boolean isAgentModeEnabled() { return agentModeEnabled; }
    public void setCurrentModel(AIModel model) { this.currentModel = model; }
    public AIModel getCurrentModel() { return currentModel; }
}
