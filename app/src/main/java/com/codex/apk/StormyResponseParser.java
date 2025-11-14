package com.codex.apk;

import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade response parser for Stormy's new tool-based workflow.
 *
 * Parses responses in the format:
 * {
 *   "action": "tool_use",
 *   "tool": "write_to_file" | "replace_in_file" | etc.,
 *   "path": "...",
 *   "content" or other params...
 *   "reasoning": "..."
 * }
 *
 * Also handles:
 * - message: Simple text response from Stormy
 * - ask_followup_question: Pause for user input
 * - attempt_completion: Task completion signal
 */
public class StormyResponseParser {
    private static final String TAG = "StormyResponseParser";

    /**
     * Represents a parsed Stormy response
     */
    public static class StormyParsedResponse {
        public String action; // tool_use, message, ask_followup_question, attempt_completion
        public String tool; // For tool_use actions
        public JsonObject toolArgs; // All tool arguments
        public String reasoning; // Optional reasoning
        public String message; // For message action
        public String question; // For ask_followup_question
        public String summary; // For attempt_completion
        public boolean isValid;
        public String rawResponse;

        public StormyParsedResponse() {
            this.isValid = false;
        }
    }

    /**
     * Parse a Stormy response from JSON string
     *
     * @param responseText JSON string to parse
     * @return StormyParsedResponse or null if invalid
     */
    public static StormyParsedResponse parseResponse(String responseText) {
        try {
            Log.d(TAG, "Parsing Stormy response: " + responseText.substring(0, Math.min(200, responseText.length())) + "...");

            JsonObject jsonObj = JsonParser.parseString(responseText).getAsJsonObject();
            StormyParsedResponse response = new StormyParsedResponse();
            response.rawResponse = responseText;

            // Check for action field
            if (!jsonObj.has("action")) {
                Log.w(TAG, "Response missing 'action' field");
                return null;
            }

            String action = jsonObj.get("action").getAsString();
            response.action = action;

            switch (action) {
                case "tool_use":
                    return parseToolUse(jsonObj, response);

                case "message":
                    return parseMessage(jsonObj, response);

                case "ask_followup_question":
                    return parseFollowupQuestion(jsonObj, response);

                case "attempt_completion":
                    return parseAttemptCompletion(jsonObj, response);

                default:
                    Log.w(TAG, "Unknown action type: " + action);
                    return null;
            }

        } catch (JsonParseException e) {
            Log.w(TAG, "Failed to parse JSON response: " + responseText, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
            return null;
        }
    }

    /**
     * Parse a tool_use action
     */
    private static StormyParsedResponse parseToolUse(JsonObject jsonObj, StormyParsedResponse response) {
        // Extract tool name
        if (!jsonObj.has("tool")) {
            Log.w(TAG, "tool_use action missing 'tool' field");
            return null;
        }

        response.tool = jsonObj.get("tool").getAsString();

        // Extract reasoning if present
        if (jsonObj.has("reasoning")) {
            response.reasoning = jsonObj.get("reasoning").getAsString();
        }

        // Collect all tool arguments
        response.toolArgs = new JsonObject();
        for (String key : jsonObj.keySet()) {
            // Skip metadata fields
            if (key.equals("action") || key.equals("tool") || key.equals("reasoning")) {
                continue;
            }
            response.toolArgs.add(key, jsonObj.get(key));
        }

        response.isValid = true;
        Log.d(TAG, "Parsed tool_use: tool=" + response.tool + ", args=" + response.toolArgs.size());
        return response;
    }

    /**
     * Parse a message action
     */
    private static StormyParsedResponse parseMessage(JsonObject jsonObj, StormyParsedResponse response) {
        if (!jsonObj.has("content")) {
            Log.w(TAG, "message action missing 'content' field");
            return null;
        }

        response.message = jsonObj.get("content").getAsString();
        response.isValid = true;
        Log.d(TAG, "Parsed message: " + response.message.substring(0, Math.min(100, response.message.length())));
        return response;
    }

    /**
     * Parse an ask_followup_question action
     *
     * This is actually a tool_use of the ask_followup_question tool,
     * but we treat it specially since it pauses the workflow
     */
    private static StormyParsedResponse parseFollowupQuestion(JsonObject jsonObj, StormyParsedResponse response) {
        // Check if it's wrapped as a tool_use
        if (jsonObj.has("tool") && jsonObj.get("tool").getAsString().equals("ask_followup_question")) {
            if (!jsonObj.has("question")) {
                Log.w(TAG, "ask_followup_question missing 'question' field");
                return null;
            }
            response.question = jsonObj.get("question").getAsString();
        } else {
            // Direct format (backwards compatibility)
            if (!jsonObj.has("question")) {
                Log.w(TAG, "ask_followup_question action missing 'question' field");
                return null;
            }
            response.question = jsonObj.get("question").getAsString();
        }

        if (jsonObj.has("reasoning")) {
            response.reasoning = jsonObj.get("reasoning").getAsString();
        }

        response.isValid = true;
        Log.d(TAG, "Parsed followup question: " + response.question);
        return response;
    }

    /**
     * Parse an attempt_completion action
     *
     * This signals that Stormy believes the task is complete
     */
    private static StormyParsedResponse parseAttemptCompletion(JsonObject jsonObj, StormyParsedResponse response) {
        // Check if it's wrapped as a tool_use
        if (jsonObj.has("tool") && jsonObj.get("tool").getAsString().equals("attempt_completion")) {
            if (!jsonObj.has("summary")) {
                Log.w(TAG, "attempt_completion missing 'summary' field");
                return null;
            }
            response.summary = jsonObj.get("summary").getAsString();
        } else {
            // Direct format (backwards compatibility)
            if (!jsonObj.has("summary")) {
                Log.w(TAG, "attempt_completion action missing 'summary' field");
                return null;
            }
            response.summary = jsonObj.get("summary").getAsString();
        }

        if (jsonObj.has("reasoning")) {
            response.reasoning = jsonObj.get("reasoning").getAsString();
        }

        response.isValid = true;
        Log.d(TAG, "Parsed attempt_completion");
        return response;
    }

    /**
     * Check if a string looks like JSON
     */
    public static boolean looksLikeJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            Log.d(TAG, "looksLikeJson: response is null or empty");
            return false;
        }

        String trimmed = response.trim();
        boolean isJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                        (trimmed.startsWith("[") && trimmed.endsWith("]"));

        Log.d(TAG, "looksLikeJson: checking '" + trimmed.substring(0, Math.min(50, trimmed.length())) + "...'");
        return isJson;
    }

    /**
     * Convert Stormy response to legacy FileOperation format for backwards compatibility
     *
     * This allows gradual migration from the old system to the new one
     */
    public static QwenResponseParser.FileOperation toFileOperation(StormyParsedResponse response) {
        if (response == null || !response.isValid || !response.action.equals("tool_use")) {
            return null;
        }

        String tool = response.tool;
        JsonObject args = response.toolArgs;

        // Map tool names to legacy operation types
        String type = mapToolToOperationType(tool);
        if (type == null) {
            return null; // Not a file operation tool
        }

        // Extract common fields
        String path = args.has("path") ? args.get("path").getAsString() : "";
        String content = args.has("content") ? args.get("content").getAsString() : "";
        String oldPath = args.has("old_path") ? args.get("old_path").getAsString() : "";
        String newPath = args.has("new_path") ? args.get("new_path").getAsString() : "";

        // Handle replace_in_file specially (extract search/replace from diff)
        String search = "";
        String replace = "";
        String diffPatch = null;

        if (tool.equals("replace_in_file") && args.has("diff")) {
            diffPatch = args.get("diff").getAsString();
            // Parse the diff to extract search and replace blocks
            String[] parts = parseDiffBlock(diffPatch);
            if (parts != null) {
                search = parts[0];
                replace = parts[1];
            }
        }

        return new QwenResponseParser.FileOperation(
            type, path, content, oldPath, newPath, search, replace,
            null, null, null, // line edit fields (not used)
            null, null, null, diffPatch, // advanced fields
            null, null, null, null, null, null
        );
    }

    /**
     * Map Stormy tool names to legacy operation types
     */
    private static String mapToolToOperationType(String tool) {
        switch (tool) {
            case "write_to_file":
                return "createFile"; // or updateFile, depending on context
            case "replace_in_file":
                return "searchAndReplace";
            case "read_file":
                return "readFile";
            case "list_files":
                return "listFiles";
            case "rename_file":
                return "renameFile";
            case "delete_file":
                return "deleteFile";
            case "copy_file":
                return "createFile"; // Copy is like creating with existing content
            case "move_file":
                return "renameFile"; // Move is like rename
            default:
                return null; // Not a file operation
        }
    }

    /**
     * Parse a SEARCH/REPLACE diff block
     *
     * Expected format:
     * <<<<<<< SEARCH
     * [search text]
     * =======
     * [replace text]
     * >>>>>>> REPLACE
     *
     * @return String array [search, replace] or null if invalid
     */
    private static String[] parseDiffBlock(String diff) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<<<<<<< SEARCH\\s*\\n(.*?)\\n=======\\s*\\n(.*?)\\n>>>>>>> REPLACE",
                java.util.regex.Pattern.DOTALL
            );

            java.util.regex.Matcher matcher = pattern.matcher(diff);
            if (matcher.find()) {
                String search = matcher.group(1);
                String replace = matcher.group(2);
                return new String[]{search, replace};
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse diff block", e);
        }

        return null;
    }

    /**
     * Create a file operation list from a Stormy response for backwards compatibility
     */
    public static List<QwenResponseParser.FileOperation> toFileOperationList(StormyParsedResponse response) {
        List<QwenResponseParser.FileOperation> operations = new ArrayList<>();

        QwenResponseParser.FileOperation op = toFileOperation(response);
        if (op != null) {
            operations.add(op);
        }

        return operations;
    }

    /**
     * Check if a response represents a file modification tool that requires approval
     */
    public static boolean requiresApproval(StormyParsedResponse response, boolean agentModeEnabled) {
        if (agentModeEnabled) {
            return false; // No approval needed in agent mode
        }

        if (response == null || !response.isValid || !response.action.equals("tool_use")) {
            return false;
        }

        // Check if this tool modifies the file system
        String tool = response.tool;
        return tool.equals("write_to_file") ||
               tool.equals("replace_in_file") ||
               tool.equals("delete_file") ||
               tool.equals("rename_file") ||
               tool.equals("copy_file") ||
               tool.equals("move_file");
    }

    /**
     * Get a human-readable description of what a tool does
     */
    public static String getToolDescription(StormyParsedResponse response) {
        if (response == null || !response.isValid) {
            return "Unknown action";
        }

        if (response.action.equals("tool_use")) {
            String tool = response.tool;
            JsonObject args = response.toolArgs;

            switch (tool) {
                case "write_to_file":
                    String path = args.has("path") ? args.get("path").getAsString() : "unknown";
                    return "Writing to " + path;

                case "replace_in_file":
                    path = args.has("path") ? args.get("path").getAsString() : "unknown";
                    return "Modifying " + path;

                case "read_file":
                    path = args.has("path") ? args.get("path").getAsString() : "unknown";
                    return "Reading " + path;

                case "list_files":
                    path = args.has("path") ? args.get("path").getAsString() : ".";
                    boolean recursive = args.has("recursive") && args.get("recursive").getAsBoolean();
                    return "Listing files in " + path + (recursive ? " (recursive)" : "");

                case "rename_file":
                    String oldPath = args.has("old_path") ? args.get("old_path").getAsString() : "unknown";
                    String newPath = args.has("new_path") ? args.get("new_path").getAsString() : "unknown";
                    return "Renaming " + oldPath + " to " + newPath;

                case "delete_file":
                    path = args.has("path") ? args.get("path").getAsString() : "unknown";
                    return "Deleting " + path;

                case "copy_file":
                    String source = args.has("source_path") ? args.get("source_path").getAsString() : "unknown";
                    String dest = args.has("destination_path") ? args.get("destination_path").getAsString() : "unknown";
                    return "Copying " + source + " to " + dest;

                case "move_file":
                    source = args.has("source_path") ? args.get("source_path").getAsString() : "unknown";
                    dest = args.has("destination_path") ? args.get("destination_path").getAsString() : "unknown";
                    return "Moving " + source + " to " + dest;

                case "search_files":
                    String dir = args.has("directory") ? args.get("directory").getAsString() : ".";
                    String pattern = args.has("regex_pattern") ? args.get("regex_pattern").getAsString() : "unknown";
                    return "Searching in " + dir + " for pattern: " + pattern;

                case "list_code_definition_names":
                    dir = args.has("directory") ? args.get("directory").getAsString() : ".";
                    return "Extracting code definitions from " + dir;

                case "ask_followup_question":
                    return "Asking a question";

                case "attempt_completion":
                    return "Task complete";

                default:
                    return "Using tool: " + tool;
            }
        } else if (response.action.equals("message")) {
            return "Responding";
        } else if (response.action.equals("ask_followup_question")) {
            return "Asking a question";
        } else if (response.action.equals("attempt_completion")) {
            return "Task complete";
        }

        return "Unknown action: " + response.action;
    }
}
