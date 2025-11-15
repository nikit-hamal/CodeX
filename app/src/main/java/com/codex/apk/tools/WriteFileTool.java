package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import java.io.File;
import org.json.JSONException;
import org.json.JSONObject;

public class WriteFileTool implements Tool {

    @Override
    public String getName() {
        return "writeFile";
    }

    @Override
    public String getDescription() {
        return "Writes content to a file, creating it if it doesn't exist.";
    }

    @Override
    public JSONObject getParameterSchema() {
        try {
            return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("path", new JSONObject()
                        .put("type", "string")
                        .put("description", "The path to the file."))
                    .put("content", new JSONObject()
                        .put("type", "string")
                        .put("description", "The content to write to the file.")))
                .put("required", new org.json.JSONArray().put("path").put("content"));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public ToolResult execute(File projectDir, JSONObject parameters) {
        try {
            String path = parameters.getString("path");
            String content = parameters.getString("content");
            FileOps.updateFile(projectDir, path, content);
            JSONObject result = new JSONObject();
            result.put("message", "File written successfully: " + path);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
