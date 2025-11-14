
package com.codex.apk;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A generic response parser that attempts to parse structured JSON but falls back to plain text.
 * It handles plans, file operations, and simple messages.
 */
public class GenericResponseParser {
    private static final String TAG = "GenericResponseParser";
    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ParseResultListener {
        void onParseSuccess(QwenResponseParser.ParsedResponse response);
        void onParseFailed(String rawText);
    }

    public static void parseResponseAsync(String responseText, String rawSse, ParseResultListener listener) {
        backgroundExecutor.execute(() -> {
            try {
                // First, try to extract JSON from markdown code blocks
                String jsonToParse = com.codex.apk.util.JsonUtils.extractJsonFromCodeBlock(responseText);
                if (jsonToParse == null && com.codex.apk.util.JsonUtils.looksLikeJson(responseText)) {
                    jsonToParse = responseText;
                }

                if (jsonToParse != null) {
                    QwenResponseParser.ParsedResponse parsed = QwenResponseParser.parseResponse(jsonToParse);
                    if (parsed != null && parsed.isValid) {
                        parsed.rawResponse = rawSse;
                        mainHandler.post(() -> listener.onParseSuccess(parsed));
                        return;
                    }
                }

                // Fallback to treating the whole response as a simple message
                mainHandler.post(() -> listener.onParseFailed(responseText));

            } catch (Exception e) {
                Log.e(TAG, "Generic parsing failed, falling back to raw text", e);
                mainHandler.post(() -> listener.onParseFailed(responseText));
            }
        });
    }
}