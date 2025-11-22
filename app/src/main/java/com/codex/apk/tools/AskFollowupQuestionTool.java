package com.codex.apk.tools;

import com.google.gson.JsonObject;
import java.io.File;

public class AskFollowupQuestionTool implements Tool {

    @Override
    public String getName() {
        return "ask_followup_question";
    }

    @Override
    public JsonObject execute(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("question", args.get("question").getAsString());
        return result;
    }
}
