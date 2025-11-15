package com.codex.apk.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StormyResponseParser {

    private static final Gson gson = new Gson();

    public static class ToolCall {
        private final String name;
        private final Map<String, Object> args;

        public ToolCall(String name, Map<String, Object> args) {
            this.name = name;
            this.args = args;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArgs() {
            return args;
        }
    }

    public static List<ToolCall> parse(String json) {
        List<ToolCall> toolCalls = new ArrayList<>();
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("tool_calls")) {
                JsonArray toolCallsArray = jsonObject.getAsJsonArray("tool_calls");
                for (JsonElement toolCallElement : toolCallsArray) {
                    JsonObject toolCallObject = toolCallElement.getAsJsonObject();
                    String name = toolCallObject.get("name").getAsString();
                    Map<String, Object> args = gson.fromJson(toolCallObject.get("args"), Map.class);
                    toolCalls.add(new ToolCall(name, args));
                }
            }
        } catch (Exception e) {
            // Log the exception or handle it as needed
        }
        return toolCalls;
    }
}