package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import java.io.File;
import org.json.JSONException;
import org.json.JSONObject;

public class DeleteFileTool implements Tool {

    @Override
    public String getName() {
        return "deleteFile";
    }

    @Override
    public String getDescription() {
        return "Deletes a file or directory.";
    }

    @Override
    public JSONObject getParameterSchema() {
        try {
            return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("path", new JSONObject()
                        .put("type", "string")
                        .put("description", "The path to the file or directory.")))
                .put("required", new org.json.JSONArray().put("path"));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public ToolResult execute(File projectDir, JSONObject parameters) {
        try {
            String path = parameters.getString("path");
            boolean deleted = FileOps.deleteRecursively(new File(projectDir, path));
            JSONObject result = new JSONObject();
            result.put("message", "Deleted: " + path);
            result.put("success", deleted);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
