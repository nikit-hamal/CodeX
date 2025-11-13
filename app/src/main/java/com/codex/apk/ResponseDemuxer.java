package com.codex.apk;

import com.codex.apk.util.JsonUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized demux for provider completions: extracts JSON from text,
 * parses into plan or file operations, and notifies the UI uniformly.
 *
 * Tool-calls are handled upstream for Qwen (since it supports continuation);
 * for generic providers we only surface plan/file ops or plain text.
 */
public final class ResponseDemuxer {
    private ResponseDemuxer() {}

    public static void handleGeneric(
            AIAssistant.AIActionListener listener,
            String modelDisplayName,
            String rawResponse,
            String explanation,
            String thinking
    ) {
        if (listener == null) return;
        com.codex.apk.ai.GenericResponseParser parser = new com.codex.apk.ai.GenericResponseParser();
        com.codex.apk.ai.ParsedResponse parsed = parser.parse(explanation);
        listener.onAiActionsProcessed(parsed, modelDisplayName);
    }
}
