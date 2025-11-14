# Stormy Integration Guide

This document provides a comprehensive guide for integrating the new Stormy agent system into CodeX.

## Overview of Changes

The refactor replaces the naive "plan-then-execute" workflow with a production-grade, iterative, tool-based agent system. Here's what's been implemented:

### 1. **New System Prompt** (`PromptManager.java`)
- ✅ Comprehensive "Stormy" persona with clear expertise scope (HTML, CSS, Tailwind, JS only)
- ✅ Tool-based workflow examples
- ✅ Strict scope limitations with polite decline templates
- ✅ Iterative, autonomous working style
- ✅ Professional communication guidelines

### 2. **Production-Grade Toolset** (`StormyToolSpec.java`)
- ✅ 12 mandatory tools organized into categories:
  - **File I/O**: `write_to_file`, `replace_in_file`, `read_file`
  - **File System**: `list_files`, `rename_file`, `delete_file`, `copy_file`, `move_file`
  - **Search & Analysis**: `search_files`, `list_code_definition_names`
  - **Agent Interaction**: `ask_followup_question`, `attempt_completion`

- ✅ Each tool includes:
  - Detailed parameter schemas with descriptions
  - Category classification
  - Approval requirement flag (for agent mode disabled)
  - OpenAI/Qwen compatible JSON format

### 3. **Tool Executor** (`StormyToolExecutor.java`)
- ✅ Complete implementation of all 12 tools
- ✅ Comprehensive error handling
- ✅ Detailed result reporting with metadata
- ✅ SEARCH/REPLACE diff block parser for `replace_in_file`
- ✅ Recursive file operations with safety limits
- ✅ Code definition extraction for HTML, CSS, and JS

### 4. **Response Parser** (`StormyResponseParser.java`)
- ✅ Parses new tool_use format: `{"action": "tool_use", "tool": "...", "path": "...", ...}`
- ✅ Handles agent interaction tools: `ask_followup_question`, `attempt_completion`
- ✅ Backwards compatibility layer: converts to legacy `FileOperation` format
- ✅ Approval detection logic for non-agent mode
- ✅ Tool description generator for UI display

---

## Integration Steps

### Phase 1: Enable Stormy Tools (Minimal Integration)

**Goal**: Get Stormy tools working alongside the existing plan-based workflow.

#### Step 1.1: Update Tool Provider

**File**: `AIAssistant.java` (or wherever tools are provided to the API)

```java
// Add import
import com.codex.apk.StormyToolSpec;

// In the method that provides tools to the API:
public JsonArray getToolsForApi() {
    // Option A: Use Stormy tools exclusively
    List<StormyToolSpec> stormyTools = StormyToolSpec.getStormyTools();
    return StormyToolSpec.toJsonArray(stormyTools);

    // Option B: Keep legacy tools (for gradual migration)
    // return ToolSpec.toJsonArray(ToolSpec.defaultFileTools());
}
```

#### Step 1.2: Update Response Processing

**File**: `AiAssistantManager.java` (or similar coordinator)

```java
// Add imports
import com.codex.apk.StormyResponseParser;
import com.codex.apk.StormyResponseParser.StormyParsedResponse;

// In the method that handles AI responses:
private void processAiResponse(String responseText) {
    // Try parsing as Stormy response
    StormyParsedResponse stormyResponse = StormyResponseParser.parseResponse(responseText);

    if (stormyResponse != null && stormyResponse.isValid) {
        handleStormyResponse(stormyResponse);
        return;
    }

    // Fallback to legacy parsing
    QwenResponseParser.ParsedResponse legacyResponse = QwenResponseParser.parseResponse(responseText);
    if (legacyResponse != null) {
        handleLegacyResponse(legacyResponse);
    }
}
```

#### Step 1.3: Implement Stormy Response Handler

```java
private void handleStormyResponse(StormyParsedResponse response) {
    switch (response.action) {
        case "tool_use":
            executeStormyTool(response);
            break;

        case "message":
            displayMessageToUser(response.message);
            break;

        case "ask_followup_question":
            pauseAndAskUser(response.question);
            break;

        case "attempt_completion":
            showCompletionSummary(response.summary);
            break;
    }
}

private void executeStormyTool(StormyParsedResponse response) {
    // Check if approval is needed
    boolean needsApproval = StormyResponseParser.requiresApproval(response, isAgentModeEnabled());

    if (needsApproval) {
        showApprovalDialog(response);
    } else {
        executeToolImmediately(response);
    }
}

private void executeToolImmediately(StormyParsedResponse response) {
    // Execute the tool
    File projectDir = getCurrentProjectDir();
    JsonObject result = StormyToolExecutor.execute(projectDir, response.tool, response.toolArgs);

    // Handle the result
    if (result.get("ok").getAsBoolean()) {
        handleToolSuccess(result, response);
    } else {
        handleToolError(result, response);
    }
}
```

---

### Phase 2: Implement Two-Mode Agent System

**Goal**: Support both "Agent Mode Enabled" (full autonomy) and "Agent Mode Disabled" (approval for file modifications).

#### Step 2.1: Add Agent Mode Setting

**File**: `SettingsActivity.java` or similar

```java
public class SettingsActivity extends AppCompatActivity {
    private static final String PREF_AGENT_MODE_ENABLED = "agent_mode_enabled";

    public static boolean isAgentModeEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_AGENT_MODE_ENABLED, false); // Default: OFF
    }

    public static void setAgentModeEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_AGENT_MODE_ENABLED, enabled).apply();
    }
}
```

Add UI toggle in settings:
```xml
<!-- settings_layout.xml -->
<SwitchCompat
    android:id="@+id/agent_mode_switch"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Agent Mode (Auto-Execute File Operations)"
    android:checked="false" />
```

#### Step 2.2: Implement Approval Dialog

**File**: Create `StormyApprovalDialog.java`

```java
public class StormyApprovalDialog extends DialogFragment {
    private StormyParsedResponse pendingResponse;
    private ApprovalCallback callback;

    public interface ApprovalCallback {
        void onApproved(StormyParsedResponse response);
        void onRejected(StormyParsedResponse response);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        // Inflate custom layout
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_stormy_approval, null);

        // Show tool description and reasoning
        TextView toolDescription = view.findViewById(R.id.tool_description);
        TextView reasoning = view.findViewById(R.id.reasoning);

        toolDescription.setText(StormyResponseParser.getToolDescription(pendingResponse));
        reasoning.setText(pendingResponse.reasoning != null ? pendingResponse.reasoning : "No reasoning provided");

        // Show affected files/paths
        displayAffectedFiles(view);

        builder.setView(view)
            .setTitle("Approve File Operation?")
            .setPositiveButton("Approve", (dialog, which) -> {
                if (callback != null) callback.onApproved(pendingResponse);
            })
            .setNegativeButton("Reject", (dialog, which) -> {
                if (callback != null) callback.onRejected(pendingResponse);
            });

        return builder.create();
    }

    private void displayAffectedFiles(View view) {
        // Show preview of what will be modified
        // For replace_in_file: show diff
        // For write_to_file: show file path and content preview
        // etc.
    }

    public static void show(FragmentManager manager, StormyParsedResponse response, ApprovalCallback callback) {
        StormyApprovalDialog dialog = new StormyApprovalDialog();
        dialog.pendingResponse = response;
        dialog.callback = callback;
        dialog.show(manager, "stormy_approval");
    }
}
```

**Layout**: Create `res/layout/dialog_stormy_approval.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tool_description"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold"
        android:paddingBottom="8dp" />

    <TextView
        android:id="@+id/reasoning"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:paddingBottom="16dp" />

    <TextView
        android:text="Affected Files:"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:paddingBottom="8dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/affected_files_list"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="200dp" />
</LinearLayout>
```

#### Step 2.3: Wire Up Approval Flow

```java
private void executeStormyTool(StormyParsedResponse response) {
    boolean agentModeEnabled = SettingsActivity.isAgentModeEnabled(context);
    boolean needsApproval = StormyResponseParser.requiresApproval(response, agentModeEnabled);

    if (needsApproval) {
        StormyApprovalDialog.show(getFragmentManager(), response, new StormyApprovalDialog.ApprovalCallback() {
            @Override
            public void onApproved(StormyParsedResponse response) {
                executeToolImmediately(response);
            }

            @Override
            public void onRejected(StormyParsedResponse response) {
                sendRejectionFeedbackToAI(response);
            }
        });
    } else {
        executeToolImmediately(response);
    }
}
```

---

### Phase 3: Implement Iterative Workflow

**Goal**: Remove static plans. Agent works iteratively until task completion.

#### Step 3.1: Create Iterative Workflow Orchestrator

**File**: Create `StormyWorkflowOrchestrator.java`

```java
public class StormyWorkflowOrchestrator {
    private final AIAssistant aiAssistant;
    private final File projectDir;
    private final boolean agentModeEnabled;
    private final WorkflowCallback callback;

    private boolean isRunning = false;
    private List<StormyParsedResponse> executionHistory = new ArrayList<>();

    public interface WorkflowCallback {
        void onToolExecuted(StormyParsedResponse response, JsonObject result);
        void onQuestionAsked(String question);
        void onTaskCompleted(String summary);
        void onError(String error);
    }

    /**
     * Start the iterative workflow with a user request
     */
    public void startWorkflow(String userRequest) {
        if (isRunning) {
            callback.onError("Workflow already running");
            return;
        }

        isRunning = true;
        executionHistory.clear();

        // Send initial request to AI
        aiAssistant.sendMessage(userRequest, new AIResponseCallback() {
            @Override
            public void onResponse(String responseText) {
                processAiResponse(responseText);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
                isRunning = false;
            }
        });
    }

    /**
     * Process AI response and execute tools iteratively
     */
    private void processAiResponse(String responseText) {
        StormyParsedResponse response = StormyResponseParser.parseResponse(responseText);

        if (response == null || !response.isValid) {
            callback.onError("Invalid response from AI");
            isRunning = false;
            return;
        }

        executionHistory.add(response);

        switch (response.action) {
            case "tool_use":
                handleToolUse(response);
                break;

            case "message":
                // Simple message - continue workflow
                continueWorkflow();
                break;

            case "ask_followup_question":
                // Pause and wait for user input
                callback.onQuestionAsked(response.question);
                isRunning = false;
                break;

            case "attempt_completion":
                // Task complete
                callback.onTaskCompleted(response.summary);
                isRunning = false;
                break;
        }
    }

    /**
     * Handle tool execution and continue workflow
     */
    private void handleToolUse(StormyParsedResponse response) {
        // Check if approval needed
        boolean needsApproval = StormyResponseParser.requiresApproval(response, agentModeEnabled);

        if (needsApproval) {
            // Pause and show approval dialog
            showApprovalDialog(response, approved -> {
                if (approved) {
                    executeAndContinue(response);
                } else {
                    // Send rejection to AI and continue
                    sendRejectionAndContinue(response);
                }
            });
        } else {
            // Execute immediately
            executeAndContinue(response);
        }
    }

    /**
     * Execute tool and continue workflow
     */
    private void executeAndContinue(StormyParsedResponse response) {
        // Execute the tool
        JsonObject result = StormyToolExecutor.execute(projectDir, response.tool, response.toolArgs);

        // Notify callback
        callback.onToolExecuted(response, result);

        // Check if this is a read-only tool or if it succeeded
        if (result.get("ok").getAsBoolean()) {
            // Send result back to AI and continue
            sendToolResultAndContinue(response, result);
        } else {
            // Tool failed - report error
            callback.onError("Tool execution failed: " + result.get("error").getAsString());
            isRunning = false;
        }
    }

    /**
     * Send tool result back to AI and continue iteration
     */
    private void sendToolResultAndContinue(StormyParsedResponse response, JsonObject result) {
        // Build continuation message with tool result
        JsonObject continuationMsg = new JsonObject();
        continuationMsg.addProperty("role", "tool");
        continuationMsg.addProperty("tool", response.tool);
        continuationMsg.add("result", result);

        // Send to AI
        aiAssistant.continueWithToolResult(continuationMsg, new AIResponseCallback() {
            @Override
            public void onResponse(String responseText) {
                processAiResponse(responseText);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
                isRunning = false;
            }
        });
    }

    /**
     * Resume workflow after user answers a question
     */
    public void resumeWithAnswer(String answer) {
        if (isRunning) {
            return; // Already running
        }

        isRunning = true;

        // Send user answer to AI
        aiAssistant.sendMessage(answer, new AIResponseCallback() {
            @Override
            public void onResponse(String responseText) {
                processAiResponse(responseText);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
                isRunning = false;
            }
        });
    }
}
```

---

### Phase 4: Update Chat UI for Tool Display

**Goal**: Rich, dynamic UI for tool operations with diff views, file listings, etc.

#### Step 4.1: Create Diff View Component

**File**: Create `DiffView.java`

```java
public class DiffView extends LinearLayout {
    private TextView searchBlock;
    private TextView replaceBlock;
    private ImageView arrowIcon;

    public DiffView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_diff, this, true);
        searchBlock = findViewById(R.id.search_block);
        replaceBlock = findViewById(R.id.replace_block);
        arrowIcon = findViewById(R.id.arrow_icon);

        setOrientation(VERTICAL);
    }

    public void setDiff(String searchText, String replaceText) {
        searchBlock.setText(searchText);
        replaceBlock.setText(replaceText);

        // Highlight differences
        highlightDifferences(searchText, replaceText);
    }

    private void highlightDifferences(String search, String replace) {
        // Use DiffUtils to compute line-by-line differences
        // Apply colored backgrounds: red for removed, green for added
    }
}
```

**Layout**: Create `res/layout/view_diff.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/bg_code_block"
    android:padding="8dp">

    <TextView
        android:text="SEARCH:"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:textColor="@color/diff_search_label" />

    <TextView
        android:id="@+id/search_block"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="monospace"
        android:textSize="12sp"
        android:background="@color/diff_search_bg"
        android:padding="8dp" />

    <ImageView
        android:id="@+id/arrow_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_gravity="center"
        android:src="@drawable/ic_arrow_down"
        android:layout_marginVertical="4dp" />

    <TextView
        android:text="REPLACE:"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:textColor="@color/diff_replace_label" />

    <TextView
        android:id="@+id/replace_block"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="monospace"
        android:textSize="12sp"
        android:background="@color/diff_replace_bg"
        android:padding="8dp" />
</LinearLayout>
```

#### Step 4.2: Update Chat Message Item Layout

**File**: Update `res/layout/item_ai_message.xml`

Add container for tool displays:

```xml
<!-- After the main message content -->
<LinearLayout
    android:id="@+id/tool_operation_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:visibility="gone"
    android:layout_marginTop="8dp">

    <TextView
        android:id="@+id/tool_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:textSize="14sp"
        android:paddingBottom="4dp" />

    <TextView
        android:id="@+id/tool_reasoning"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="13sp"
        android:paddingBottom="8dp" />

    <!-- Dynamic content: DiffView, file list, code block, etc. -->
    <FrameLayout
        android:id="@+id/tool_content_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <TextView
        android:id="@+id/tool_result"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:layout_marginTop="8dp"
        android:visibility="gone" />
</LinearLayout>
```

#### Step 4.3: Update ChatMessageAdapter

```java
public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {

    // In bindAiMessage:
    private void bindToolOperation(ViewHolder holder, StormyParsedResponse response, JsonObject result) {
        holder.toolOperationContainer.setVisibility(View.VISIBLE);

        // Set tool name and reasoning
        holder.toolName.setText(StormyResponseParser.getToolDescription(response));
        if (response.reasoning != null) {
            holder.toolReasoning.setText(response.reasoning);
            holder.toolReasoning.setVisibility(View.VISIBLE);
        } else {
            holder.toolReasoning.setVisibility(View.GONE);
        }

        // Dynamic content based on tool type
        holder.toolContentContainer.removeAllViews();

        switch (response.tool) {
            case "replace_in_file":
                addDiffView(holder, response);
                break;

            case "write_to_file":
                addFileContentView(holder, response);
                break;

            case "list_files":
                addFileListView(holder, result);
                break;

            case "search_files":
                addSearchResultsView(holder, result);
                break;

            case "read_file":
                addFileContentView(holder, result);
                break;

            // etc.
        }

        // Show result if available
        if (result != null) {
            String message = result.get("message").getAsString();
            holder.toolResult.setText(message);
            holder.toolResult.setVisibility(View.VISIBLE);

            boolean ok = result.get("ok").getAsBoolean();
            holder.toolResult.setTextColor(ok ? Color.GREEN : Color.RED);
        }
    }

    private void addDiffView(ViewHolder holder, StormyParsedResponse response) {
        DiffView diffView = new DiffView(holder.itemView.getContext());

        // Parse diff from response
        String diff = response.toolArgs.get("diff").getAsString();
        String[] parts = parseDiffBlock(diff);
        if (parts != null) {
            diffView.setDiff(parts[0], parts[1]);
        }

        holder.toolContentContainer.addView(diffView);
    }

    private void addFileContentView(ViewHolder holder, StormyParsedResponse response) {
        // Show file content in a syntax-highlighted code block
        CodeView codeView = new CodeView(holder.itemView.getContext());
        String content = response.toolArgs.get("content").getAsString();
        String path = response.toolArgs.get("path").getAsString();

        codeView.setCode(content, getLanguageFromPath(path));
        holder.toolContentContainer.addView(codeView);
    }

    private void addFileListView(ViewHolder holder, JsonObject result) {
        // Show file list as a RecyclerView
        RecyclerView fileList = new RecyclerView(holder.itemView.getContext());
        fileList.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));

        JsonArray files = result.getAsJsonArray("files");
        FileListAdapter adapter = new FileListAdapter(files);
        fileList.setAdapter(adapter);

        holder.toolContentContainer.addView(fileList);
    }
}
```

---

## Migration Strategy

### Recommended Approach: Gradual Migration

1. **Week 1**: Implement Phase 1 (Minimal Integration)
   - Keep existing plan-based workflow
   - Add Stormy tools alongside legacy tools
   - Test with Stormy prompt in isolated environment

2. **Week 2**: Implement Phase 2 (Two-Mode System)
   - Add agent mode toggle
   - Implement approval dialog
   - Test approval flow thoroughly

3. **Week 3**: Implement Phase 3 (Iterative Workflow)
   - Create workflow orchestrator
   - Wire up iterative execution
   - Test with complex multi-step tasks

4. **Week 4**: Implement Phase 4 (Rich UI)
   - Create diff view component
   - Update chat message layouts
   - Polish UI/UX

5. **Week 5**: Testing & Refinement
   - End-to-end testing
   - Bug fixes
   - Performance optimization

---

## Testing Checklist

### Tool Execution Tests

- [ ] `write_to_file` creates new files correctly
- [ ] `write_to_file` overwrites existing files
- [ ] `replace_in_file` applies SEARCH/REPLACE correctly
- [ ] `replace_in_file` rejects ambiguous search patterns
- [ ] `read_file` returns file contents
- [ ] `list_files` works recursively and non-recursively
- [ ] `rename_file` renames and moves files
- [ ] `delete_file` deletes files and directories
- [ ] `copy_file` copies files correctly
- [ ] `move_file` moves files correctly
- [ ] `search_files` finds regex patterns
- [ ] `list_code_definition_names` extracts definitions

### Workflow Tests

- [ ] Agent mode ON: tools execute automatically
- [ ] Agent mode OFF: approval dialog shows for file modifications
- [ ] Agent mode OFF: read-only tools execute without approval
- [ ] Iterative workflow continues until completion
- [ ] `ask_followup_question` pauses workflow correctly
- [ ] `attempt_completion` ends workflow
- [ ] Tool failures are handled gracefully
- [ ] Approval rejection sends feedback to AI

### UI Tests

- [ ] Diff view displays SEARCH/REPLACE correctly
- [ ] Diff view highlights changes
- [ ] File list displays correctly
- [ ] Code blocks have syntax highlighting
- [ ] Tool reasoning displays correctly
- [ ] Tool results show success/failure

---

## Configuration

### Enable Stormy by Default

**File**: `SettingsActivity.java`

```java
// Change default tools
public static List<ToolSpec> getEnabledTools(Context context) {
    // Use Stormy tools
    return StormyToolSpec.getStormyTools();
}

// Update system prompt selection
public static String getSystemPrompt(Context context, boolean toolsEnabled) {
    if (toolsEnabled) {
        return PromptManager.getFileOpsSystemPrompt(); // This now returns Stormy prompt
    } else {
        return PromptManager.getGeneralSystemPrompt();
    }
}
```

---

## Troubleshooting

### Issue: AI not using new tool format

**Solution**: Ensure the system prompt includes examples of the new format. The Stormy prompt in `PromptManager.java` already includes this.

### Issue: SEARCH/REPLACE not working

**Solution**: Check that:
1. SEARCH block is unique in the file
2. SEARCH block matches exactly (including whitespace)
3. REPLACE block contains the complete replacement text

### Issue: Approval dialog not showing

**Solution**: Verify:
1. Agent mode is disabled (`isAgentModeEnabled() == false`)
2. The tool is in the "requires approval" list
3. `StormyResponseParser.requiresApproval()` is being called

---

## Performance Considerations

1. **File Size Limits**: StormyToolExecutor has a 20MB limit on file reads
2. **Search Results**: Limited to 500 matches to prevent memory issues
3. **Recursive Listings**: Limited to depth 5 and 1000 entries
4. **Tool Execution**: Currently synchronous - consider making async for large operations

---

## Future Enhancements

1. **Undo/Redo**: Track file modifications for undo functionality
2. **Batch Approval**: Allow approving multiple file operations at once
3. **Dry Run Mode**: Preview changes before applying
4. **Tool Metrics**: Track tool usage statistics
5. **Custom Tools**: Allow users to define custom tools
6. **Tool Chaining**: Optimize multiple tool calls in sequence

---

## Support & Questions

For questions about this integration, refer to:
- `StormyToolSpec.java` - Tool definitions
- `StormyToolExecutor.java` - Tool implementations
- `StormyResponseParser.java` - Response parsing
- `PromptManager.java` - System prompts

All classes are thoroughly documented with inline comments.
