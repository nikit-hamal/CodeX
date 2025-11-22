package com.codex.apk.tools;

import com.google.gson.JsonObject;
import java.io.File;

public interface Tool {
    String getName();
    JsonObject execute(File projectDir, JsonObject args);
}
