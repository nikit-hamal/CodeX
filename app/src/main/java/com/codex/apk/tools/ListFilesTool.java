package com.codex.apk.tools;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ListFilesTool implements Tool {

    @Override
    public String getName() {
        return "listFiles";
    }

    @Override
    public String getDescription() {
        return "Lists the files and directories in a given path.";
    }

    @Override
    public JSONObject getParameterSchema() {
        try {
            return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                    .put("path", new JSONObject()
                        .put("type", "string")
                        .put("description", "The path to the directory.")))
                .put("required", new org.json.JSONArray().put("path"));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public ToolResult execute(File projectDir, JSONObject parameters) {
        try {
            String path = parameters.getString("path");
            File dir = new File(projectDir, path);
            if (!dir.exists() || !dir.isDirectory()) {
                return ToolResult.error("Directory not found: " + path);
            } else {
                JSONArray files = new JSONArray();
                File[] fileList = dir.listFiles();
                if (fileList != null) {
                    for (File f : fileList) {
                        JSONObject fileInfo = new JSONObject();
                        fileInfo.put("name", f.getName());
                        fileInfo.put("type", f.isDirectory() ? "directory" : "file");
                        fileInfo.put("size", f.length());
                        files.put(fileInfo);
                    }
                }
                JSONObject result = new JSONObject();
                result.put("files", files);
                result.put("message", "Directory listed: " + path);
                return ToolResult.success(result);
            }
        } catch (JSONException e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
