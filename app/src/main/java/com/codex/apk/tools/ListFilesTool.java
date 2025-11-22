package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;

public class ListFilesTool implements Tool {

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String path = args.get("path").getAsString();
            boolean recursive = args.get("recursive").getAsBoolean();

            if (recursive) {
                String tree = FileOps.recursiveListFiles(projectDir, path);
                result.addProperty("ok", true);
                result.addProperty("files", tree);
            } else {
                File[] files = FileOps.listFiles(projectDir, path);
                if (files == null) {
                    result.addProperty("ok", false);
                    result.addProperty("error", "Directory not found: " + path);
                } else {
                    JsonArray fileList = new JsonArray();
                    for (File file : files) {
                        fileList.add(file.getName());
                    }
                    result.addProperty("ok", true);
                    result.add("files", fileList);
                }
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
