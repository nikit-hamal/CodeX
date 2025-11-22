package com.codex.apk.tools;

import com.google.gson.JsonObject;
import java.io.File;

public class AttemptCompletionTool implements Tool {

    @Override
    public String getName() {
        return "attempt_completion";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("summary", args.get("summary").getAsString());
        return result;
    }
}
