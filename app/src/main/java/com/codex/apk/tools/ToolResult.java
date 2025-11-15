package com.codex.apk.tools;

import org.json.JSONObject;

public class ToolResult {
    private final boolean isSuccess;
    private final JSONObject result;
    private final String error;

    public ToolResult(boolean isSuccess, JSONObject result, String error) {
        this.isSuccess = isSuccess;
        this.result = result;
        this.error = error;
    }

    public static ToolResult success(JSONObject result) {
        return new ToolResult(true, result, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public JSONObject getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
