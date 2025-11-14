package com.codex.apk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class StormyResponseParser {

    private static final Gson gson = new Gson();

    public static class StormyAction {
        public String toolCode;
        public JsonObject parameters;
    }

    public static StormyAction parse(String jsonResponse) {
        try {
            return gson.fromJson(jsonResponse, StormyAction.class);
        } catch (JsonSyntaxException e) {
            // Handle cases where the response is not valid JSON
            return null;
        }
    }
}
