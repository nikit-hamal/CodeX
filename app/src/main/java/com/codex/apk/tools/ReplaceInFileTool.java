package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class ReplaceInFileTool implements Tool {

    @Override
    public String getName() {
        return "replace_in_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String path = args.get("path").getAsString();
            String diff = args.get("diff").getAsString();

            String originalContent = FileOps.readFile(projectDir, path);
            if (originalContent == null) {
                throw new java.io.FileNotFoundException("File not found: " + path);
            }

            String newContent = applyDiff(originalContent, diff);
            FileOps.updateFile(projectDir, path, newContent);

            result.addProperty("ok", true);
            result.addProperty("message", "File updated successfully: " + path);
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }

    private String applyDiff(String original, String diff) {
        String[] lines = diff.split("\n", -1);
        StringBuilder searchBlock = new StringBuilder();
        StringBuilder replaceBlock = new StringBuilder();
        boolean inSearchBlock = false;
        boolean inReplaceBlock = false;

        for (String line : lines) {
            if (line.equals("<<<<<<< SEARCH")) {
                inSearchBlock = true;
                continue;
            }
            if (line.equals("=======")) {
                inSearchBlock = false;
                inReplaceBlock = true;
                continue;
            }
            if (line.equals(">>>>>>> REPLACE")) {
                inReplaceBlock = false;
                continue;
            }

            if (inSearchBlock) {
                searchBlock.append(line).append("\n");
            } else if (inReplaceBlock) {
                replaceBlock.append(line).append("\n");
            }
        }

        // Trim trailing newlines
        String search = searchBlock.toString().replaceAll("\\n$", "");
        String replace = replaceBlock.toString().replaceAll("\\n$", "");

        return original.replace(search, replace);
    }
}
