package com.codex.apk.tools;

import org.json.JSONObject;

import java.io.File;

public interface Tool {
    String getName();
    String getDescription();
    JSONObject getParameterSchema();
    ToolResult execute(File projectDir, JSONObject parameters);
}
