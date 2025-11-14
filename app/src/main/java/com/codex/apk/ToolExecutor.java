package com.codex.apk;

import com.google.gson.JsonObject;
import com.codex.apk.util.FileOps;
import java.io.File;
import java.io.IOException;

public class ToolExecutor {

    public static JsonObject execute(File projectDir, String name, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            switch (name) {
                case "write_to_file": {
                    String path = args.get("path").getAsString();
                    String content = args.get("content").getAsString();
                    FileOps.createFile(projectDir, path, content);
                    result.addProperty("ok", true);
                    result.addProperty("message", "File written: " + path);
                    break;
                }
                case "replace_in_file": {
                    String path = args.get("path").getAsString();
                    String diff = args.get("diff").getAsString();
                    try {
                        String originalContent = FileOps.readFile(projectDir, path);
                        if (originalContent == null) {
                            throw new IOException("File not found: " + path);
                        }
                        String newContent = applyDiff(originalContent, diff);
                        FileOps.updateFile(projectDir, path, newContent);
                        result.addProperty("ok", true);
                        result.addProperty("message", "File updated: " + path);
                    } catch (IOException e) {
                        result.addProperty("ok", false);
                        result.addProperty("error", e.getMessage());
                    }
                    break;
                }
                default: {
                    result.addProperty("ok", false);
                    result.addProperty("error", "Unknown tool: " + name);
                }
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }

    private static String applyDiff(String original, String diff) {
        // Basic implementation of the custom diff format
        // This can be made more robust with proper error handling and parsing
        String[] parts = diff.split("=======");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid diff format");
        }
        String search = parts[0].replace("------- SEARCH", "").trim();
        String replace = parts[1].replace("+++++++ REPLACE", "").trim();
        return original.replaceFirst(search, replace);
    }
}
