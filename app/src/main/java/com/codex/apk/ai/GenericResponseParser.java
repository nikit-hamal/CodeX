package com.codex.apk.ai;

import com.codex.apk.ChatMessage;
import com.codex.apk.QwenResponseParser;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Collections;

public class GenericResponseParser implements ResponseParser {
    private final Gson gson = new Gson();

    @Override
    public QwenResponseParser.ParsedResponse parse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return createFallbackResponse("");
        }

        try {
            // First, try to parse the entire string as a JSON object
            QwenResponseParser.ParsedResponse parsed = gson.fromJson(rawResponse, QwenResponseParser.ParsedResponse.class);
            if (parsed != null && (parsed.explanation != null || parsed.planSteps != null || parsed.proposedFileChanges != null)) {
                parsed.isValid = true;
                return parsed;
            }
        } catch (JsonSyntaxException e) {
            // Not a valid JSON object, proceed to fallback
        }

        return createFallbackResponse(rawResponse);
    }

    private QwenResponseParser.ParsedResponse createFallbackResponse(String text) {
        QwenResponseParser.ParsedResponse response = new QwenResponseParser.ParsedResponse();
        response.action = "message";
        response.explanation = text;
        response.planSteps = Collections.emptyList();
        response.proposedFileChanges = Collections.emptyList();
        response.suggestions = Collections.emptyList();
        response.isValid = true;
        response.rawResponse = text;
        return response;
    }
}
