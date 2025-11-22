package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class RenameFileTool implements Tool {

    @Override
    public String getName() {
        return "rename_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String oldPath = args.get("old_path").getAsString();
            String newPath = args.get("new_path").getAsString();
            boolean success = FileOps.renameFile(projectDir, oldPath, newPath);
            if (success) {
                result.addProperty("ok", true);
                result.addProperty("message", "File renamed successfully.");
            } else {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to rename file.");
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
