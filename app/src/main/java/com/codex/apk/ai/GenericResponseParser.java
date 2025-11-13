package com.codex.apk.ai;

import com.codex.apk.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;

public class GenericResponseParser implements ResponseParser {
    @Override
    public ParsedResponse parse(String json) {
        ParsedResponse response = new ParsedResponse();
        response.rawResponse = json;
        try {
            Gson gson = new Gson();
            ResponseData data = gson.fromJson(json, ResponseData.class);

            if (data != null && (data.action != null || data.explanation != null)) { // Basic validation
                response.action = data.action;
                response.explanation = data.explanation;
                response.suggestions = data.suggestions;
                response.fileChanges = data.fileChanges;
                response.planSteps = data.planSteps;
                response.isValid = true;
            } else {
                // Not valid JSON for our purposes, treat as plain text
                response.explanation = json;
                response.isValid = true;
            }
        } catch (JsonSyntaxException e) {
            // Not JSON, treat as plain text
            response.explanation = json;
            response.isValid = true;
        }
        return response;
    }

    private static class ResponseData {
        String action;
        String explanation;
        ArrayList<String> suggestions;
        ArrayList<ChatMessage.FileActionDetail> fileChanges;
        ArrayList<ChatMessage.PlanStep> planSteps;
    }
}
