package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class MoveFileTool implements Tool {

    @Override
    public String getName() {
        return "move_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String sourcePath = args.get("source_path").getAsString();
            String destinationPath = args.get("destination_path").getAsString();
            FileOps.moveFile(projectDir, sourcePath, destinationPath);
            result.addProperty("ok", true);
            result.addProperty("message", "File moved successfully.");
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
