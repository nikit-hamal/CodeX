package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import java.io.File;
import org.json.JSONException;
import org.json.JSONObject;

public class RenameFileTool implements Tool {

    @Override
    public String getName() {
        return "renameFile";
    }

    @Override
    public String getDescription() {
        return "Renames a file or directory.";
    }

    @Override
    public JSONObject getParameterSchema() {
        try {
            return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("oldPath", new JSONObject()
                        .put("type", "string")
                        .put("description", "The original path of the file or directory."))
                    .put("newPath", new JSONObject()
                        .put("type", "string")
                        .put("description", "The new path for the file or directory.")))
                .put("required", new org.json.JSONArray().put("oldPath").put("newPath"));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public ToolResult execute(File projectDir, JSONObject parameters) {
        try {
            String oldPath = parameters.getString("oldPath");
            String newPath = parameters.getString("newPath");
            boolean renamed = FileOps.renameFile(projectDir, oldPath, newPath);
            JSONObject result = new JSONObject();
            result.put("message", "Renamed to: " + newPath);
            result.put("success", renamed);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
