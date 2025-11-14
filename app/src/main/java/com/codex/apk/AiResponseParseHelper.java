package com.codex.apk;

import androidx.annotation.Nullable;
import com.codex.apk.QwenResponseParser.ParseResultListener;
import com.codex.apk.QwenResponseParser.ParsedResponse;

/**
 * Utility to normalize how provider clients hand completed responses back to the UI.
 * Ensures every provider benefits from the rich JSON parsing used for Qwen so plan/file
 * actions render consistently.
 */
public final class AiResponseParseHelper {

    private AiResponseParseHelper() {}

    public static void deliverParsedCompletion(
            String requestId,
            String finalText,
            @Nullable String rawPayload,
            StreamingApiClient.StreamListener listener
    ) {
        final String safeText = finalText != null ? finalText : "";
        final String safeRaw = rawPayload != null ? rawPayload : safeText;

        QwenResponseParser.parseResponseAsync(safeText, safeRaw, new ParseResultListener() {
            @Override
            public void onParseSuccess(ParsedResponse response) {
                listener.onStreamCompleted(requestId, response);
            }

            @Override
            public void onParseFailed() {
                ParsedResponse fallback = new ParsedResponse();
                fallback.action = "message";
                fallback.explanation = safeText;
                fallback.rawResponse = safeRaw;
                fallback.isValid = !safeText.isEmpty();
                listener.onStreamCompleted(requestId, fallback);
            }
        });
    }
}
