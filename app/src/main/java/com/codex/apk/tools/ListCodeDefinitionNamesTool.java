package com.codex.apk.tools;

import com.codex.apk.util.FileOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;

public class ListCodeDefinitionNamesTool implements Tool {

    @Override
    public String getName() {
        return "list_code_definition_names";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            String directory = args.get("directory").getAsString();
            File searchDir = new File(projectDir, directory);
            JsonArray definitions = FileOps.listCodeDefinitionNames(searchDir);
            result.addProperty("ok", true);
            result.add("definitions", definitions);
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }
        return result;
    }
}
