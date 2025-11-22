package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class DeleteFileTool implements Tool {

    @Override
    public String getName() {
        return "delete_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String path = args.get("path").getAsString();
            boolean success = FileOps.deleteRecursively(new File(projectDir, path));
            if (success) {
                result.addProperty("ok", true);
                result.addProperty("message", "File deleted successfully.");
            } else {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to delete file.");
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
