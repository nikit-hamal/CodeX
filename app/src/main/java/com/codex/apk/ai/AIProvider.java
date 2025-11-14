package com.codex.apk.ai;

public enum AIProvider {
    QWEN("Qwen");

    private final String displayName;

    AIProvider(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
