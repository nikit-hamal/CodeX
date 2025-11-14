package com.codex.apk;

import android.util.Log;
import com.codex.apk.ai.AIModel;
import com.codex.apk.ai.WebSource;
import com.codex.apk.ChatMessage;
import com.codex.apk.ai.ToolExecutor;
import com.codex.apk.editor.AiAssistantManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.Response;

public class QwenStreamProcessor {

    @FunctionalInterface
    public interface PartialUpdateCallback {
        void onUpdate(String partialResult, boolean isThinking);
    }

    private static final String TAG = "QwenStreamProcessor";

    private final AIAssistant.AIActionListener actionListener;
    private final QwenConversationState conversationState;
    private final AIModel model;
    private final File projectDir;
    private final ToolExecutor toolExecutor;

    public QwenStreamProcessor(AIAssistant.AIActionListener actionListener, QwenConversationState conversationState, AIModel model, File projectDir) {
        this.actionListener = actionListener;
        this.conversationState = conversationState;
        this.model = model;
        this.projectDir = projectDir;
        this.toolExecutor = new ToolExecutor(null, projectDir.getAbsolutePath());
    }

    public static class StreamProcessingResult {
        public final boolean isContinuation;
        public final String continuationJson;

        public StreamProcessingResult(boolean isContinuation, String continuationJson) {
            this.isContinuation = isContinuation;
            this.continuationJson = continuationJson;
        }
    }

    private ChatMessage.ToolUsage buildToolUsage(String name, JsonObject args) {
        ChatMessage.ToolUsage usage = new ChatMessage.ToolUsage(name != null ? name : "tool");
        if (args != null) {
            usage.argsJson = args.toString();
            if (args.has("path")) {
                usage.filePath = args.get("path").getAsString();
            } else if (args.has("oldPath")) {
                usage.filePath = args.get("oldPath").getAsString();
            }
        }
        usage.status = "running";
        return usage;
    }

    private void updateToolUsageFromResult(ChatMessage.ToolUsage usage,
                                           String name,
                                           JsonObject args,
                                           JsonObject result,
                                           long durationMs) {
        if (usage == null) return;
        usage.durationMs = durationMs;
        usage.ok = result != null && result.has("ok") && result.get("ok").getAsBoolean();
        usage.status = usage.ok ? "completed" : "failed";
        usage.resultJson = result != null ? result.toString() : null;

        if (args != null && ("readFile".equals(name) || "listFiles".equals(name) ||
                "searchInProject".equals(name) || "grepSearch".equals(name))) {
            if (args.has("path") && (usage.filePath == null || usage.filePath.isEmpty())) {
                usage.filePath = args.get("path").getAsString();
            }
        }
    }

    public StreamProcessingResult process(Response response) throws IOException {
        StringBuilder thinkingContent = new StringBuilder();
        StringBuilder answerContent = new StringBuilder();
        List<com.codex.apk.ai.WebSource> webSources = new ArrayList<>();
        Set<String> seenWebUrls = new HashSet<>();
        StringBuilder rawStreamData = new StringBuilder();

        String line;
        boolean firstLineChecked = false;
        while ((line = response.body().source().readUtf8Line()) != null) {
            rawStreamData.append(line).append("\n");
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (!firstLineChecked) {
                firstLineChecked = true;
                // Some servers send an initial JSON line before SSE data:
                if (t.startsWith("{") || t.startsWith("[")) {
                    try {
                        JsonObject data = JsonParser.parseString(t).getAsJsonObject();
                        if (data.has("response.created")) {
                            JsonObject created = data.getAsJsonObject("response.created");
                            if (created.has("chat_id")) conversationState.setConversationId(created.get("chat_id").getAsString());
                            if (created.has("response_id")) conversationState.setLastParentId(created.get("response_id").getAsString());
                            if (actionListener != null) actionListener.onQwenConversationStateUpdated(conversationState);
                            continue;
                        }
                    } catch (Exception ignore) {}
                }
            }
            if ("data: [DONE]".equals(t) || "[DONE]".equals(t)) {
                String finalContentDone = answerContent.length() > 0 ? answerContent.toString() : thinkingContent.toString();
                notifyListener(rawStreamData.toString(), finalContentDone, thinkingContent.toString(), webSources);
                if (actionListener != null) actionListener.onQwenConversationStateUpdated(conversationState);
                break;
            }
            if (t.startsWith("data: ")) {
                String jsonData = t.substring(6);
                if (jsonData.trim().isEmpty()) continue;
                String trimmedJson = jsonData.trim();
                if (!(trimmedJson.startsWith("{") || trimmedJson.startsWith("["))) continue;
                try {
                    JsonObject data = JsonParser.parseString(trimmedJson).getAsJsonObject();
                    if (data.has("response.created")) {
                        JsonObject created = data.getAsJsonObject("response.created");
                        if (created.has("chat_id")) conversationState.setConversationId(created.get("chat_id").getAsString());
                        if (created.has("response_id")) conversationState.setLastParentId(created.get("response_id").getAsString());
                        if (actionListener != null) actionListener.onQwenConversationStateUpdated(conversationState);
                        continue;
                    }

                    if (data.has("choices")) {
                        JsonArray choices = data.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject choice = choices.get(0).getAsJsonObject();
                            JsonObject delta = choice.getAsJsonObject("delta");
                            String status = delta.has("status") ? delta.get("status").getAsString() : "";
                            String content = delta.has("content") ? delta.get("content").getAsString() : "";
                            String phase = delta.has("phase") ? delta.get("phase").getAsString() : "";
                            JsonObject extra = delta.has("extra") && delta.get("extra").isJsonObject() ? delta.getAsJsonObject("extra") : null;

                            if ("think".equals(phase)) {
                                thinkingContent.append(content);
                                if (actionListener != null) actionListener.onAiStreamUpdate(thinkingContent.toString(), true);
                            } else if ("answer".equals(phase)) {
                                answerContent.append(content);
                                if (actionListener != null) actionListener.onAiStreamUpdate(answerContent.toString(), false);
                            }

                            // Collect web sources when present in extra
                            if (extra != null && extra.has("sources") && extra.get("sources").isJsonArray()) {
                                try {
                                    JsonArray srcArr = extra.getAsJsonArray("sources");
                                    for (int i = 0; i < srcArr.size(); i++) {
                                        JsonObject s = srcArr.get(i).getAsJsonObject();
                                        String url = s.has("url") ? s.get("url").getAsString() : null;
                                        String title = s.has("title") ? s.get("title").getAsString() : null;
                                        String snippet = s.has("snippet") ? s.get("snippet").getAsString() : null;
                                        String favicon = s.has("favicon") ? s.get("favicon").getAsString() : null;
                                        if (url != null && !seenWebUrls.contains(url)) {
                                            seenWebUrls.add(url);
                                            webSources.add(new WebSource(url, title, snippet, favicon));
                                        }
                                    }
                                } catch (Exception ignore) {}
                            }

                            if ("finished".equals(status)) {
                                String finalContent = answerContent.length() > 0 ? answerContent.toString() : thinkingContent.toString();
                                // Defensive: some responses end with empty content but valid fenced JSON earlier
                                String jsonToParse = extractJsonFromCodeBlock(finalContent);
                                if (jsonToParse == null) {
                                    jsonToParse = finalContent;
                                }


                                // If still empty, try to salvage from last non-empty delta content
                                if ((finalContent == null || finalContent.trim().isEmpty())) {
                                    finalContent = recoverContentFromRaw(rawStreamData.toString());
                                }
                                notifyListener(rawStreamData.toString(), finalContent, thinkingContent.toString(), webSources);
                                if (actionListener != null) actionListener.onQwenConversationStateUpdated(conversationState);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error processing stream data chunk", e);
                    if (actionListener != null) actionListener.onAiError("Stream error: " + e.getMessage());
                    break;
                }
            }
        }
        if (actionListener != null) actionListener.onAiRequestCompleted();
        return new StreamProcessingResult(false, null);
    }

    private void notifyListener(String rawResponse, String finalContent, String thinkingContent, List<com.codex.apk.ai.WebSource> webSources) {
        actionListener.onAiRequestCompleted();
    }

    public String recoverContentFromRaw(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        // Heuristic: concatenate all answer-phase content fragments (delta.content) between code fences
        StringBuilder sb = new StringBuilder();
        try {
            String[] lines = raw.split("\n");
            boolean inside = false;
            for (String l : lines) {
                String t = l.trim();
                if (!t.startsWith("data: ")) continue;
                String json = t.substring(6).trim();
                if (!(json.startsWith("{") || json.startsWith("["))) continue;
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (!obj.has("choices")) continue;
                JsonArray choices = obj.getAsJsonArray("choices");
                if (choices.size() == 0) continue;
                JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                if (delta == null) continue;
                String phase = delta.has("phase") ? delta.get("phase").getAsString() : "";
                String content = delta.has("content") && !delta.get("content").isJsonNull() ? delta.get("content").getAsString() : "";
                if (content == null) content = "";
                // Track code fence blocks; if the model emitted ```json ... ``` chunks, try to reassemble
                if ("answer".equals(phase)) {
                    if (content.startsWith("```")) inside = true;
                    if (inside) sb.append(content);
                    if (content.endsWith("```")) inside = false;
                }
            }
        } catch (Exception ignore) {}
        return sb.toString();
    }

    private String extractJsonFromCodeBlock(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String jsonPattern = "```json\\s*([\\s\\S]*?)```";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(jsonPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String genericPattern = "```\\s*([\\s\\S]*?)```";
        pattern = java.util.regex.Pattern.compile(genericPattern);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            return extracted;
        }
        return null;
    }

    public static boolean isErrorChunk(JsonObject chunk) {
        // Simple check for now, can be expanded
        return chunk.has("error");
    }

    public static void processChunk(JsonObject data, QwenConversationState state, StringBuilder finalText, PartialUpdateCallback callback, AIAssistant.AIActionListener listener) {
        try {
            if (data.has("response.created")) {
                JsonObject created = data.getAsJsonObject("response.created");
                if (created.has("chat_id")) state.setConversationId(created.get("chat_id").getAsString());
                if (created.has("response_id")) state.setLastParentId(created.get("response_id").getAsString());
                if (listener != null) listener.onQwenConversationStateUpdated(state);
                return;
            }

            if (data.has("choices")) {
                JsonArray choices = data.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("delta")) {
                        JsonObject delta = choice.getAsJsonObject("delta");
                        String content = delta.has("content") ? delta.get("content").getAsString() : "";
                        String phase = delta.has("phase") ? delta.get("phase").getAsString() : "answer";
                        finalText.append(content);
                        if (callback != null) {
                            callback.onUpdate(finalText.toString(), "think".equals(phase));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error processing stream chunk in QwenStreamProcessor", e);
        }
    }
}
