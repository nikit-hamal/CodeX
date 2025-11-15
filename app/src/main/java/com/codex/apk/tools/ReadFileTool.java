package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import java.io.File;
import org.json.JSONException;
import org.json.JSONObject;

public class ReadFileTool implements Tool {

    @Override
    public String getName() {
        return "readFile";
    }

    @Override
    public String getDescription() {
        return "Reads the content of a file.";
    }

    @Override
    public JSONObject getParameterSchema() {
        try {
            return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("path", new JSONObject()
                        .put("type", "string")
                        .put("description", "The path to the file.")))
                .put("required", new org.json.JSONArray().put("path"));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public ToolResult execute(File projectDir, JSONObject parameters) {
        try {
            String path = parameters.getString("path");
            String content = FileOps.readFile(projectDir, path);
            if (content == null) {
                return ToolResult.error("File not found: " + path);
            } else {
                int maxLength = 20000;
                String message;
                if (content.length() > maxLength) {
                    content = content.substring(0, maxLength);
                    message = "File read (truncated): " + path;
                } else {
                    message = "File read: " + path;
                }
                JSONObject result = new JSONObject();
                result.put("content", content);
                result.put("message", message);
                return ToolResult.success(result);
            }
        } catch (JSONException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
