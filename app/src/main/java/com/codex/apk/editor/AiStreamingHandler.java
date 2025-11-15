package com.codex.apk.editor;

import com.codex.apk.AIChatFragment;
import com.codex.apk.AIAssistant;
import com.codex.apk.ChatMessage;
import com.codex.apk.EditorActivity;
import com.codex.apk.util.AdaptiveUIThrottler;

/**
 * Handles the lifecycle of streaming chat messages (showing and clearing the
 * "thinking" placeholder) while the AI generates responses.
 */
public class AiStreamingHandler {
    private final EditorActivity activity;
    private final AiAssistantManager manager;
    private final AdaptiveUIThrottler throttler;

    public AiStreamingHandler(EditorActivity activity, AiAssistantManager manager) {
        this.activity = activity;
        this.manager = manager;
        this.throttler = new AdaptiveUIThrottler(activity);
    }

    public void handleRequestStarted(AIChatFragment chatFragment,
                                     AIAssistant aiAssistant,
                                     boolean suppressThinkingMessage) {
        if (suppressThinkingMessage) {
            if (chatFragment != null) {
                chatFragment.hideThinkingMessage();
            }
            manager.setCurrentStreamingMessagePosition(null);
            return;
        }

        if (chatFragment != null && aiAssistant != null) {
            ChatMessage aiMsg = new ChatMessage(
                    ChatMessage.SENDER_AI,
                    activity.getString(com.codex.apk.R.string.ai_is_thinking),
                    null, null,
                    aiAssistant.getCurrentModel().getDisplayName(),
                    System.currentTimeMillis(),
                    null, null,
                    ChatMessage.STATUS_NONE
            );
            manager.setCurrentStreamingMessagePosition(chatFragment.addMessage(aiMsg));
        }
    }

    public void handleRequestCompleted(AIChatFragment chatFragment) {
        if (chatFragment != null) {
            chatFragment.hideThinkingMessage();
        }
        manager.setCurrentStreamingMessagePosition(null);
    }

    public void handleStreamUpdate(AIChatFragment chatFragment,
                                   int messagePosition,
                                   String partialResponse,
                                   boolean isThinking) {
        if (chatFragment == null || partialResponse == null || partialResponse.isEmpty()) {
            return;
        }

        ChatMessage existing = chatFragment.getMessageAt(messagePosition);
        if (existing == null) {
            return;
        }

        // The streaming response can contain multiple JSON objects, so we need to parse them.
        // We replace `}{` with `}\n{` to create a delimiter and then split by the newline.
        String[] chunks = partialResponse.replace("}{", "}\n{").split("\n");
        StringBuilder contentBuilder = new StringBuilder(existing.getContent());

        for (String chunk : chunks) {
            try {
                // Ensure the chunk is a valid JSON object before parsing.
                String jsonChunk = chunk.endsWith("}") ? chunk : chunk + "}";
                org.json.JSONObject json = new org.json.JSONObject(jsonChunk);
                org.json.JSONArray choices = json.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    org.json.JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta != null && delta.has("content")) {
                        contentBuilder.append(delta.getString("content"));
                    }
                }
            } catch (org.json.JSONException e) {
                // In case of a parsing error, we can log it or handle it gracefully.
                // For now, we'll just skip the malformed chunk.
            }
        }

        String newContent = contentBuilder.toString();
        // The "thinking" message should be replaced by the first bit of content.
        if (activity.getString(com.codex.apk.R.string.ai_is_thinking).equals(existing.getContent())) {
            existing.setContent(newContent);
        } else {
            existing.setContent(newContent);
        }

        if (!isThinking) {
            existing.setThinkingContent(null);
        }

        throttler.scheduleUpdate(() -> chatFragment.updateMessage(messagePosition, existing), 15);
    }
}
