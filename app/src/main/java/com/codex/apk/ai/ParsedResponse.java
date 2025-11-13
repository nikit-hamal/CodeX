package com.codex.apk.ai;

import com.codex.apk.ChatMessage;
import java.util.List;

public class ParsedResponse {
    public String action;
    public String explanation;
    public List<String> suggestions;
    public List<ChatMessage.FileActionDetail> fileChanges;
    public List<ChatMessage.PlanStep> planSteps;
    public String rawResponse;
    public boolean isValid;
}
