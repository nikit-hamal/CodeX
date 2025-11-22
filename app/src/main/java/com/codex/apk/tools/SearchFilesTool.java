package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;

public class SearchFilesTool implements Tool {

    @Override
    public String getName() {
        return "search_files";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String directory = args.get("directory").getAsString();
            String regexPattern = args.get("regex_pattern").getAsString();
            File searchDir = new File(projectDir, directory);

            JsonArray searchResults = FileOps.searchInFilesOffsets(searchDir, regexPattern, true, true, new ArrayList<>(), 500);

            result.addProperty("ok", true);
            result.add("results", searchResults);
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
