package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonObject;
import java.io.File;

public class WriteToFileTool implements Tool {

    @Override
    public String getName() {
        return "write_to_file";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String path = args.get("path").getAsString();
            String content = args.get("content").getAsString();
            FileOps.createFile(projectDir, path, content);
            result.addProperty("ok", true);
            result.addProperty("message", "File written successfully: " + path);
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
