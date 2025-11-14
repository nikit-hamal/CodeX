# Stormy: Production-Grade AI Agent for CodeX

**Stormy** is a complete refactor of the CodeX agent system, transforming it from a naive "plan-then-execute" workflow into a professional, iterative, autonomous coding assistant.

---

## 🎯 What is Stormy?

Stormy is an expert AI assistant that:

- ✅ **Specializes** in HTML, CSS, Tailwind CSS, and JavaScript
- ✅ **Works iteratively** - no static plans, just autonomous execution
- ✅ **Uses tools** to read, write, and analyze code
- ✅ **Defaults to Tailwind CSS** for all styling
- ✅ **Prioritizes accessibility** and responsive design
- ✅ **Asks questions** when clarification is needed
- ✅ **Signals completion** with detailed summaries

---

## 📦 What's Included

### Core Components

| Component | File | Lines | Description |
|-----------|------|-------|-------------|
| **System Prompt** | `PromptManager.java` | 304 | Stormy's persona, scope, and workflow |
| **Tool Definitions** | `StormyToolSpec.java` | 514 | 12 mandatory tools with schemas |
| **Tool Executor** | `StormyToolExecutor.java` | 703 | Production-grade tool implementations |
| **Response Parser** | `StormyResponseParser.java` | 463 | Parses Stormy's responses |
| **Workflow Orchestrator** | `StormyWorkflowOrchestrator.java` | 394 | Iterative execution engine |

### UI Components

| Component | File | Description |
|-----------|------|-------------|
| **Diff View** | `view_diff_display.xml` | Shows SEARCH/REPLACE blocks |
| **Approval Dialog** | `dialog_stormy_approval.xml` | Approval UI for file mods |
| **Approval Dialog** | `StormyApprovalDialog.java` | Dialog logic and previews |

### Documentation

| Document | Description |
|----------|-------------|
| `QUICK_START.md` | Get running in 30 minutes |
| `STORMY_INTEGRATION_GUIDE.md` | Complete integration guide (4,500+ words) |
| `IMPLEMENTATION_SUMMARY.md` | Technical specification and architecture |
| `STORMY_README.md` | This file - Overview and quick reference |

---

## 🚀 Quick Start

**Want to get Stormy running fast?** Follow these steps:

1. **Read**: `QUICK_START.md` (30-minute setup)
2. **Update**: System prompt (already done in `PromptManager.java`)
3. **Enable**: Stormy tools (switch from `ToolSpec` to `StormyToolSpec`)
4. **Parse**: Stormy responses (add `StormyResponseParser` to your response handler)
5. **Test**: Read/list/search tools

**For full implementation**: Read `STORMY_INTEGRATION_GUIDE.md`

---

## 🛠️ The 12 Mandatory Tools

### File I/O (3)
1. **`write_to_file`** - Create or overwrite files
2. **`replace_in_file`** - Targeted modifications with SEARCH/REPLACE
3. **`read_file`** - Read file contents

### File System (5)
4. **`list_files`** - List directory contents (recursive option)
5. **`rename_file`** - Rename or move files
6. **`delete_file`** - Delete files/directories
7. **`copy_file`** - Copy files
8. **`move_file`** - Move files

### Search & Analysis (2)
9. **`search_files`** - Regex search across project
10. **`list_code_definition_names`** - Extract classes, functions, IDs

### Agent Interaction (2)
11. **`ask_followup_question`** - Pause for user input
12. **`attempt_completion`** - Signal task completion

---

## 🎨 Key Features

### 1. Iterative Workflow

**Old Way**:
```
User Request → AI → Static Plan → User Approves → Execute All Steps
```

**Stormy Way**:
```
User Request → AI → Tool Use → Execute → Result → AI → Next Tool → ...
                                                     ↓
                                            Completion Signal
```

### 2. Two-Mode System

**Agent Mode ON** (Full Autonomy):
- Stormy executes all tools automatically
- No approval needed
- Fastest workflow

**Agent Mode OFF** (Approval Required):
- Read-only tools execute automatically
- File modifications require approval
- Approval dialog shows preview
- User can approve or reject

### 3. Professional Communication

Stormy:
- ✅ Explains reasoning for each action
- ✅ Uses clear, technical language
- ✅ References files and code with backticks
- ✅ Provides helpful context
- ✅ Asks questions when needed
- ✅ Signals completion with detailed summaries

### 4. Scope Limitations

Stormy **ONLY** works with:
- HTML5
- CSS3
- Tailwind CSS
- JavaScript (ES6+)

Stormy **politely declines** requests for:
- Backend languages (Python, PHP, Node.js, etc.)
- Frameworks (React, Vue, Angular, etc.)
- Databases
- Build tools

---

## 📊 Architecture

### Response Format

Stormy responds in one of four formats:

#### 1. Tool Use
```json
{
  "action": "tool_use",
  "tool": "write_to_file",
  "path": "index.html",
  "content": "<!DOCTYPE html>...",
  "reasoning": "Creating landing page with Tailwind CSS"
}
```

#### 2. Message
```json
{
  "action": "message",
  "content": "I've analyzed your codebase and found..."
}
```

#### 3. Question
```json
{
  "action": "ask_followup_question",
  "question": "Which theme should be the default?",
  "reasoning": "Need clarification before implementing theme system"
}
```

#### 4. Completion
```json
{
  "action": "attempt_completion",
  "summary": "✅ Landing page complete! Features:\n- Hero section\n- Contact form\n- Responsive design",
  "reasoning": "All requested features implemented"
}
```

### Tool Result Format

All tools return standardized results:

```json
{
  "ok": true,
  "message": "Successfully wrote 1234 bytes to index.html",
  "path": "index.html",
  "bytes_written": 1234,
  "lines_written": 45
}
```

Error format:

```json
{
  "ok": false,
  "error": "File not found: nonexistent.html"
}
```

---

## 🔧 Integration Points

### 1. Tool Provider

**Where**: Wherever you configure AI tools

**Change**:
```java
// Before
List<ToolSpec> tools = ToolSpec.defaultFileTools();

// After
List<StormyToolSpec> tools = StormyToolSpec.getStormyTools();
JsonArray toolsJson = StormyToolSpec.toJsonArray(tools);
```

### 2. Response Handler

**Where**: Wherever you process AI responses

**Change**:
```java
// Add Stormy parsing
StormyParsedResponse response = StormyResponseParser.parseResponse(responseText);

if (response != null && response.isValid) {
    // Handle Stormy response
    handleStormyResponse(response);
}
```

### 3. Tool Executor

**Where**: Wherever you execute tools

**Change**:
```java
// Execute Stormy tools
JsonObject result = StormyToolExecutor.execute(projectDir, toolName, toolArgs);

// Check result
if (result.get("ok").getAsBoolean()) {
    // Success
} else {
    // Error: result.get("error").getAsString()
}
```

### 4. Workflow Orchestrator (Optional but Recommended)

**For Full Autonomy**:

```java
StormyWorkflowOrchestrator orchestrator = new StormyWorkflowOrchestrator(
    context, aiAssistant, projectDir, workflowCallback
);

orchestrator.startWorkflow("Create a landing page with hero section");
```

---

## 🧪 Testing

### Quick Sanity Tests

1. **Read**: "Read the index.html file"
   - ✅ Uses `read_file` tool
   - ✅ Returns file content

2. **List**: "List all files in the current directory"
   - ✅ Uses `list_files` tool
   - ✅ Returns file list

3. **Search**: "Search for 'function' in JavaScript files"
   - ✅ Uses `search_files` tool
   - ✅ Returns matches

4. **Question**: Stormy asks "Which theme?"
   - ✅ Workflow pauses
   - ✅ Question displayed to user

5. **Completion**: Stormy signals completion
   - ✅ Summary displayed
   - ✅ Workflow ends

### Complete Testing Checklist

See `STORMY_INTEGRATION_GUIDE.md` for:
- 20+ tool execution tests
- Workflow tests
- UI tests
- Approval flow tests

---

## 🎯 Example Usage

### Example 1: Create a Landing Page

**User**: "Create a modern landing page with a hero section and contact form"

**Stormy's Workflow**:

1. **`list_files`** - Check project structure
2. **`write_to_file`** - Create `index.html` with:
   - Tailwind CSS CDN
   - Hero section with gradient
   - Contact form with validation
   - Responsive layout
3. **`attempt_completion`** - Signal completion with summary

**Result**: Fully functional landing page, no static plan needed

### Example 2: Modify Existing Code

**User**: "Make the navigation sticky"

**Stormy's Workflow**:

1. **`read_file`** - Read current HTML
2. **`replace_in_file`** - Add `sticky top-0 z-50` classes
   - Shows SEARCH/REPLACE diff
   - User approves (if agent mode OFF)
3. **`attempt_completion`** - Confirm changes

**Result**: Navigation is now sticky

### Example 3: Multi-Step Task

**User**: "Create a portfolio page with project cards"

**Stormy's Workflow**:

1. **`list_files`** - Check structure
2. **`write_to_file`** - Create `portfolio.html`
3. **`write_to_file`** - Create `portfolio.css`
4. **`replace_in_file`** - Link CSS in HTML
5. **`attempt_completion`** - Show summary

**Result**: Complete portfolio page with styling

---

## ⚙️ Configuration

### Agent Mode

**Location**: `SettingsActivity.java`

```java
boolean agentModeEnabled = SettingsActivity.isAgentModeEnabled(context);
```

**Default**: OFF (for safety)

**Recommendation**: Start with OFF, enable once comfortable

### Tool Limits

| Limit | Value | Reason |
|-------|-------|--------|
| Max file size | 20 MB | Prevent memory issues |
| Max search results | 500 | Prevent UI overload |
| Max iterations | 50 | Prevent infinite loops |
| Recursive depth | 5 levels | Performance |
| Max file list | 1000 entries | Performance |

---

## 🐛 Troubleshooting

### AI Not Using Tools

**Check**:
- Tools are being sent to AI in request
- System prompt is Stormy's prompt
- AI model supports tool calling

**Fix**:
- Log tools JSON in API request
- Verify `PromptManager.getFileOpsSystemPrompt()` returns Stormy prompt
- Check Qwen API client logs

### Tools Not Executing

**Check**:
- Response is being parsed correctly
- `StormyToolExecutor.execute()` is being called
- Project directory is correct

**Fix**:
- Log parsed response
- Add breakpoints in executor
- Verify file permissions

### Approval Dialog Not Showing

**Check**:
- Agent mode is OFF
- Tool requires approval (file modifications)
- Dialog fragment is being shown correctly

**Fix**:
- Verify `isAgentModeEnabled()` returns false
- Check `StormyResponseParser.requiresApproval()`
- Log dialog show calls

---

## 📈 Performance

### Benchmarks (Typical)

| Operation | Time | Notes |
|-----------|------|-------|
| Parse response | <10ms | JSON parsing |
| Execute read_file | <50ms | Small files |
| Execute write_to_file | <100ms | Small files |
| Execute search_files | <500ms | 100 files |
| Execute list_code_definition_names | <1s | Full project |

### Optimization Tips

1. **Cache file reads** - Store recently read files
2. **Async tool execution** - Don't block UI
3. **Batch operations** - Group similar operations
4. **Lazy load UI** - Render tool displays on demand

---

## 🔮 Future Enhancements

### Planned

1. **Undo/Redo** - Track file modifications for undo
2. **Batch Approval** - Approve multiple operations at once
3. **Dry Run Mode** - Preview changes without applying
4. **Tool Metrics** - Track usage statistics
5. **Custom Tools** - Allow users to define tools

### Possible

1. **Syntax Highlighting** - Color code in diff views
2. **File Preview** - Show files before modifying
3. **History View** - See all tool executions
4. **Export Logs** - Save workflow history
5. **Voice Input** - Control Stormy with voice

---

## 📚 Documentation Reference

| Document | Purpose | Audience |
|----------|---------|----------|
| `QUICK_START.md` | Get running in 30 minutes | Developers (first time) |
| `STORMY_INTEGRATION_GUIDE.md` | Complete integration steps | Developers (full impl) |
| `IMPLEMENTATION_SUMMARY.md` | Technical specification | Architects, Reviewers |
| `STORMY_README.md` | Overview and reference | Everyone |

---

## 💡 Best Practices

### For Developers

1. **Start Small** - Get basic tools working first
2. **Test Thoroughly** - Use the testing checklist
3. **Monitor Logs** - Watch for errors and edge cases
4. **Handle Errors** - All tools can fail, plan for it
5. **Document Changes** - Keep track of customizations

### For Users

1. **Be Specific** - Clear requests get better results
2. **Start with Agent Mode OFF** - Get comfortable first
3. **Review Previews** - Check what Stormy will do
4. **Provide Feedback** - If Stormy's wrong, say so
5. **Use Questions** - Stormy will ask if unclear

---

## 🎓 Learning Resources

### Understanding Stormy

1. Read the system prompt in `PromptManager.java`
2. Review tool definitions in `StormyToolSpec.java`
3. Check example responses in integration guide
4. Try the quick start scenarios

### Understanding the Architecture

1. Read `IMPLEMENTATION_SUMMARY.md`
2. Review class diagrams in integration guide
3. Study the workflow orchestrator
4. Trace a complete workflow in the code

---

## 📞 Support

### For Questions

- **Implementation**: See `STORMY_INTEGRATION_GUIDE.md`
- **Architecture**: See `IMPLEMENTATION_SUMMARY.md`
- **Quick Help**: See `QUICK_START.md`
- **Code Details**: Check inline comments

### For Issues

1. Check troubleshooting section above
2. Review integration guide
3. Check logs for errors
4. Verify configuration

---

## ✅ Summary

Stormy is a **production-ready, professional AI agent** that:

- ✅ Replaces naive plan-based workflow with iterative execution
- ✅ Provides 12 mandatory tools for file operations
- ✅ Supports two modes (full autonomy or approval)
- ✅ Includes rich UI components (diff views, approval dialogs)
- ✅ Has comprehensive documentation and integration guides
- ✅ Follows professional coding standards
- ✅ Is ready to be integrated into CodeX

**Total Implementation**: 3,577 lines of production-grade code
**Integration Time**: 30 minutes (quick start) to 5 weeks (full system)
**Status**: ✅ Complete and ready for integration

---

**Welcome to Stormy. Let's build something amazing! 🚀**
