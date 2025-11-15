package com.codex.apk.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.codex.apk.AIChatFragment;
import com.codex.apk.AIAssistant;
import com.codex.apk.ai.AIModel;
import com.codex.apk.ChatMessage;
import com.codex.apk.EditorActivity;
import com.codex.apk.FileManager;
import com.codex.apk.TabItem;
import com.codex.apk.ai.ToolExecutor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class AiAssistantManager implements AIAssistant.AIActionListener {

    private static final String TAG = "AiAssistantManager";
    private final EditorActivity activity; // Reference to the hosting activity
    private final AIAssistant aiAssistant;
    private final FileManager fileManager;
    private final ExecutorService executorService;
    private final ToolExecutor toolExecutor;

    public AiAssistantManager(EditorActivity activity, File projectDir, String projectName,
                              FileManager fileManager, ExecutorService executorService) {
        this.activity = activity;
        this.fileManager = fileManager;
        this.executorService = executorService;
        this.toolExecutor = new ToolExecutor(activity, projectDir.getAbsolutePath());
        this.aiAssistant = new AIAssistant(activity, executorService, this);
        aiAssistant.setProjectDir(projectDir);
        // Model selection: prefer per-project last-used, else global default, else fallback
        SharedPreferences settingsPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences modelPrefs = activity.getSharedPreferences("model_settings", Context.MODE_PRIVATE);
        String projectKey = "project_" + (projectName != null ? projectName : "default") + "_last_model";
        String lastUsed = settingsPrefs.getString(projectKey, null);
        String defaultModelName = modelPrefs.getString("default_model", null);
        String initialName = lastUsed != null ? lastUsed : (defaultModelName != null ? defaultModelName : AIModel.fromModelId("qwen3-coder-plus").getDisplayName());
        AIModel initialModel = AIModel.fromDisplayName(initialName);
        if (initialModel != null) {
            this.aiAssistant.setCurrentModel(initialModel);
        }
    }

    public AIAssistant getAIAssistant() { return aiAssistant; }

    public void sendAiPrompt(String userPrompt, List<ChatMessage> chatHistory, com.codex.apk.QwenConversationState qwenState, TabItem activeTabItem) {
        String currentFileContent = "";
        String currentFileName = "";
        if (activeTabItem != null) {
            currentFileContent = activeTabItem.getContent();
            currentFileName = activeTabItem.getFileName();
        }

        if (aiAssistant == null) {
            Log.e(TAG, "sendAiPrompt: AIAssistant not initialized!");
            return;
        }

        try {
            aiAssistant.sendMessageStreaming(userPrompt, chatHistory, qwenState, null, currentFileName, currentFileContent);
        } catch (Exception e) {
            Log.e(TAG, "AI processing error", e);
        }
    }

    public void shutdown() {
        if (aiAssistant != null) {
            aiAssistant.shutdown();
        }
    }

    @Override
    public void onAiError(String errorMessage) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onAiError(errorMessage);
            }
        });
    }

    @Override
    public void onAiRequestStarted() {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onAiRequestStarted();
            }
        });
    }

    @Override
    public void onAiStreamUpdate(String partialResponse, boolean isThinking) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onAiStreamUpdate(partialResponse, isThinking);
            }
        });
    }

    @Override
    public void onAiRequestCompleted() {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onAiRequestCompleted();
            }
        });
    }

    @Override
    public void onQwenConversationStateUpdated(com.codex.apk.QwenConversationState state) {
        activity.runOnUiThread(() -> {
            if (activity != null) {
                activity.onQwenConversationStateUpdated(state);
            }
        });
    }

    @Override
    public void onToolCall(String toolName, Map<String, Object> args) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onToolCall(toolName, args);
            }
        });
    }

    @Override
    public void onApprovalRequired(String toolName, Map<String, Object> args) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onApprovalRequired(toolName, args);
            }
        });
    }

    @Override
    public void onFollowupQuestion(String question) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onFollowupQuestion(question);
            }
        });
    }

    @Override
    public void onCompletion(String summary) {
        activity.runOnUiThread(() -> {
            AIChatFragment uiFrag = activity.getAiChatFragment();
            if (uiFrag != null) {
                uiFrag.onCompletion(summary);
            }
        });
    }

    @Override
    public void onStreamError(String requestId, String errorMessage, Throwable throwable) {
        onAiError(errorMessage);
    }

    @Override
    public void onStreamStarted(String requestId) {
    }

    @Override
    public void onStreamPartialUpdate(String requestId, String partialResponse, boolean isThinking) {
    }

    @Override
    public void onStreamCompleted(String requestId, String response) {
    }
}
