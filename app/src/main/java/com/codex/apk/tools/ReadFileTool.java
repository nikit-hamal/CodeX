package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class ReadFileTool implements Tool {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String path = args.get("path").getAsString();
            String content = FileOps.readFile(projectDir, path);
            if (content == null) {
                result.addProperty("ok", false);
                result.addProperty("error", "File not found: " + path);
            } else {
                result.addProperty("ok", true);
                result.addProperty("content", content);
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
