# Stormy Quick Start Guide

Get the new Stormy agent system running in **30 minutes or less**.

---

## Prerequisites

- ✅ All new files have been added to your project
- ✅ Your codebase is on the `tembo/codex-agent-workflow-tools-ui-upgrade` branch
- ✅ You have a working CodeX Android app
- ✅ You understand basic Android development

---

## Step 1: Update System Prompt (5 minutes)

The Stormy system prompt is already in `PromptManager.java`. Verify it's being used:

**File**: `PromptManager.java`

```java
public static String getDefaultFileOpsPrompt() {
    return defaultFileOpsPrompt(); // This now returns Stormy's prompt
}
```

✅ **Verification**: Open the file and confirm the prompt starts with "# WHO YOU ARE" and "You are **Stormy**"

---

## Step 2: Enable Stormy Tools (5 minutes)

Find where your app provides tools to the AI and switch to Stormy's tools.

**File**: Look for `AIAssistant.java` or wherever tools are configured

**Before**:
```java
List<ToolSpec> tools = ToolSpec.defaultFileTools();
```

**After**:
```java
// Import Stormy tools
import com.codex.apk.StormyToolSpec;

// Use Stormy tools
List<StormyToolSpec> tools = StormyToolSpec.getStormyTools();
JsonArray toolsJson = StormyToolSpec.toJsonArray(tools);
```

✅ **Verification**: Run the app, send a message to AI, check logs for tool definitions being sent

---

## Step 3: Add Response Parser (10 minutes)

Find where AI responses are processed and add Stormy parsing.

**File**: Look for `AiAssistantManager.java` or similar

**Add Import**:
```java
import com.codex.apk.StormyResponseParser;
import com.codex.apk.StormyResponseParser.StormyParsedResponse;
import com.codex.apk.StormyToolExecutor;
```

**Update Response Handler**:
```java
private void handleAiResponse(String responseText) {
    // Try Stormy format first
    StormyParsedResponse stormyResponse = StormyResponseParser.parseResponse(responseText);

    if (stormyResponse != null && stormyResponse.isValid) {
        handleStormyResponse(stormyResponse);
        return;
    }

    // Fallback to legacy parsing (keep for now)
    QwenResponseParser.ParsedResponse legacyResponse = QwenResponseParser.parseResponse(responseText);
    if (legacyResponse != null) {
        handleLegacyResponse(legacyResponse);
    }
}
```

**Add Handler Method**:
```java
private void handleStormyResponse(StormyParsedResponse response) {
    switch (response.action) {
        case "tool_use":
            executeStormyTool(response);
            break;

        case "message":
            // Display message to user
            displayMessage(response.message);
            break;

        case "ask_followup_question":
            // Pause and ask user
            askUserQuestion(response.question);
            break;

        case "attempt_completion":
            // Show completion summary
            showCompletion(response.summary);
            break;
    }
}
```

**Add Tool Executor**:
```java
private void executeStormyTool(StormyParsedResponse response) {
    File projectDir = getProjectDirectory();

    // Execute the tool
    JsonObject result = StormyToolExecutor.execute(projectDir, response.tool, response.toolArgs);

    // Handle result
    if (result.get("ok").getAsBoolean()) {
        // Tool succeeded - show result
        displayToolResult(response, result);

        // For read-only tools, send result back to AI to continue
        if (!StormyResponseParser.requiresApproval(response, true)) {
            sendToolResultToAI(response, result);
        }
    } else {
        // Tool failed - show error
        displayToolError(response, result);
    }
}
```

✅ **Verification**:
- Send request: "Read the index.html file"
- Check if `read_file` tool is executed
- Check if file content is displayed

---

## Step 4: Test Basic Tool Execution (5 minutes)

Test that tools are working:

### Test 1: Read File
**User message**: "Read the index.html file"

**Expected**:
- Stormy uses `read_file` tool
- File content is displayed
- No errors in logs

### Test 2: List Files
**User message**: "List all files in the current directory"

**Expected**:
- Stormy uses `list_files` tool
- File list is displayed

### Test 3: Search Files
**User message**: "Search for the word 'function' in all JavaScript files"

**Expected**:
- Stormy uses `search_files` tool
- Search results are displayed

✅ **All tests pass?** You're now running Stormy with basic tool support!

---

## Step 5: Enable Agent Mode Toggle (5 minutes)

**OPTIONAL**: Add a toggle for agent mode (full autonomy vs approval)

**File**: `SettingsActivity.java`

**Add Method**:
```java
public static boolean isAgentModeEnabled(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getBoolean("agent_mode_enabled", false); // Default: OFF for safety
}
```

**Add to Settings XML**:
```xml
<SwitchCompat
    android:id="@+id/agent_mode_switch"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Agent Mode (Automatic File Operations)"
    android:checked="false" />
```

**Wire Up Switch**:
```java
SwitchCompat agentModeSwitch = findViewById(R.id.agent_mode_switch);
agentModeSwitch.setChecked(isAgentModeEnabled(this));
agentModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.edit().putBoolean("agent_mode_enabled", isChecked).apply();
});
```

✅ **Verification**: Toggle should appear in settings and persist state

---

## What You Have Now

After these 5 steps:

✅ Stormy system prompt is active
✅ Stormy tools are enabled
✅ Stormy response parsing works
✅ Read-only tools execute automatically
✅ Agent mode toggle is available (optional)

---

## What's Missing (For Full Implementation)

To complete the full Stormy system, you still need:

1. **Approval Dialog**: For file modifications when agent mode is OFF
   - Use `StormyApprovalDialog.show()` (already created)
   - Call from `executeStormyTool()` when `requiresApproval()` is true

2. **Workflow Orchestrator**: For iterative execution
   - Use `StormyWorkflowOrchestrator` (already created)
   - Handles tool continuation and multi-step tasks

3. **Rich UI**: Diff views, tool displays
   - Use `view_diff_display.xml` (already created)
   - Update `ChatMessageAdapter` to show tool operations

4. **Tool Result Continuation**: Send results back to AI
   - After tool executes, send result to AI
   - AI decides next action (another tool, completion, etc.)

**See `STORMY_INTEGRATION_GUIDE.md` for complete implementation.**

---

## Quick Testing Checklist

Once you've completed the 5 steps, test these scenarios:

- [ ] **Read file**: "Read index.html" → File content displayed
- [ ] **List files**: "List all files" → File list displayed
- [ ] **Search files**: "Search for 'function'" → Results displayed
- [ ] **Ask question**: AI says "Which theme do you prefer?" → Question displayed
- [ ] **Simple message**: AI sends text response → Message displayed
- [ ] **Tool error**: "Read nonexistent.html" → Error message displayed

---

## Troubleshooting

### Issue: AI not using new tool format

**Check**: System prompt includes Stormy prompt
**Fix**: Verify `PromptManager.getFileOpsSystemPrompt()` returns Stormy prompt

### Issue: Tools not executing

**Check**: Tools are being sent to AI
**Fix**: Log the tools JSON being sent in API request

### Issue: Parse errors

**Check**: Response format matches expected
**Fix**: Log the raw AI response and check against examples

### Issue: No response from AI

**Check**: API connection and streaming
**Fix**: Check Qwen API client logs

---

## Next Steps

1. ✅ **Complete These 5 Steps** (30 minutes)
2. 📖 **Read Integration Guide** (`STORMY_INTEGRATION_GUIDE.md`)
3. 🚀 **Implement Full System** (Follow Phases 2-4 in guide)
4. 🧪 **Test Thoroughly** (Use testing checklist in guide)
5. 🎉 **Launch Stormy!**

---

## Need Help?

- **Implementation details**: See `STORMY_INTEGRATION_GUIDE.md`
- **Architecture overview**: See `IMPLEMENTATION_SUMMARY.md`
- **Code examples**: Check inline comments in each new class
- **Tool definitions**: See `StormyToolSpec.java`
- **Tool implementations**: See `StormyToolExecutor.java`

---

**You're ready to get started! Follow these 5 steps and you'll have Stormy running in 30 minutes or less.** 🚀
