package com.codex.apk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade tool specifications for Stormy, the CodeX AI agent.
 *
 * This class defines the complete toolset that Stormy uses to interact with
 * the file system and complete web development tasks iteratively and autonomously.
 *
 * Tool categories:
 * - File I/O: write_to_file, replace_in_file, read_file
 * - File System: list_files, rename_file, delete_file, copy_file, move_file
 * - Search & Analysis: search_files, list_code_definition_names
 * - Agent Interaction: ask_followup_question, attempt_completion
 */
public class StormyToolSpec {

    private final String name;
    private final String description;
    private final JsonObject parametersSchema;
    private final ToolCategory category;
    private final boolean requiresApproval; // For agent mode disabled

    public enum ToolCategory {
        FILE_IO,
        FILE_SYSTEM,
        SEARCH_ANALYSIS,
        AGENT_INTERACTION
    }

    public StormyToolSpec(String name, String description, JsonObject parametersSchema,
                          ToolCategory category, boolean requiresApproval) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.category = category;
        this.requiresApproval = requiresApproval;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ToolCategory getCategory() {
        return category;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    /**
     * Serializes this tool into OpenAI/Qwen compatible function format
     */
    public JsonObject toJson() {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        fn.add("parameters", parametersSchema);

        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "function");
        wrapper.add("function", fn);
        return wrapper;
    }

    /**
     * Converts a list of tools to a JsonArray for the API request
     */
    public static JsonArray toJsonArray(List<StormyToolSpec> specs) {
        JsonArray arr = new JsonArray();
        for (StormyToolSpec spec : specs) {
            arr.add(spec.toJson());
        }
        return arr;
    }

    /**
     * Returns the complete Stormy toolset with all mandatory tools
     */
    public static List<StormyToolSpec> getStormyTools() {
        List<StormyToolSpec> tools = new ArrayList<>();

        // File I/O Tools
        tools.add(writeToFile());
        tools.add(replaceInFile());
        tools.add(readFile());

        // File System Tools
        tools.add(listFiles());
        tools.add(renameFile());
        tools.add(deleteFile());
        tools.add(copyFile());
        tools.add(moveFile());

        // Search & Analysis Tools
        tools.add(searchFiles());
        tools.add(listCodeDefinitionNames());

        // Agent Interaction Tools
        tools.add(askFollowupQuestion());
        tools.add(attemptCompletion());

        return tools;
    }

    // ========================================================================
    // FILE I/O TOOLS
    // ========================================================================

    /**
     * write_to_file: Create or completely overwrite a file
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec writeToFile() {
        JsonObject schema = buildSchema(
            new Param("path", "string", "Relative path to the file (e.g., 'index.html', 'css/style.css')", true),
            new Param("content", "string", "Complete content to write to the file (supports line breaks with \\n)", true),
            new Param("reasoning", "string", "Brief explanation of why you're creating/overwriting this file", false)
        );

        return new StormyToolSpec(
            "write_to_file",
            "Create a new file or completely overwrite an existing file with the provided content. " +
            "Use this for creating new HTML/CSS/JS files or when you need to completely rewrite a file. " +
            "For targeted modifications to existing files, use replace_in_file instead.",
            schema,
            ToolCategory.FILE_IO,
            true // Requires approval in non-agent mode
        );
    }

    /**
     * replace_in_file: Make targeted modifications using SEARCH/REPLACE format
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec replaceInFile() {
        JsonObject schema = buildSchema(
            new Param("path", "string", "Relative path to the file to modify", true),
            new Param("diff", "string",
                "SEARCH/REPLACE block in this exact format:\n" +
                "<<<<<<< SEARCH\n" +
                "[exact text to find - must be unique in file]\n" +
                "=======\n" +
                "[exact replacement text]\n" +
                ">>>>>>> REPLACE\n\n" +
                "CRITICAL: The SEARCH block must be unique in the file. " +
                "The REPLACE block is the complete new text (not a diff). " +
                "Use \\n for line breaks.",
                true),
            new Param("reasoning", "string", "Brief explanation of what you're changing and why", false)
        );

        return new StormyToolSpec(
            "replace_in_file",
            "Make a targeted modification to an existing file using the SEARCH/REPLACE format. " +
            "The SEARCH block must match exactly and be unique in the file. " +
            "The REPLACE block contains the complete new text to use. " +
            "This is safer than rewriting entire files and preserves unchanged code.",
            schema,
            ToolCategory.FILE_IO,
            true // Requires approval in non-agent mode
        );
    }

    /**
     * read_file: Read the complete contents of a file
     * Does NOT require approval (read-only operation)
     */
    public static StormyToolSpec readFile() {
        JsonObject schema = buildSchema(
            new Param("path", "string", "Relative path to the file to read", true),
            new Param("reasoning", "string", "Brief explanation of why you need to read this file", false)
        );

        return new StormyToolSpec(
            "read_file",
            "Read and return the complete contents of a file. " +
            "Always read a file before modifying it with replace_in_file to ensure accurate SEARCH blocks.",
            schema,
            ToolCategory.FILE_IO,
            false // Does NOT require approval (read-only)
        );
    }

    // ========================================================================
    // FILE SYSTEM TOOLS
    // ========================================================================

    /**
     * list_files: List contents of a directory
     * Does NOT require approval (read-only operation)
     */
    public static StormyToolSpec listFiles() {
        JsonObject schema = buildSchema(
            new Param("path", "string", "Relative path to the directory (use '.' for project root)", true),
            new Param("recursive", "boolean", "If true, list all files recursively; if false, list only immediate children", false),
            new Param("reasoning", "string", "Brief explanation of why you're listing this directory", false)
        );

        return new StormyToolSpec(
            "list_files",
            "List all files and directories within a specified path. " +
            "Use recursive mode to see the complete file tree. " +
            "Essential for understanding project structure before making changes.",
            schema,
            ToolCategory.FILE_SYSTEM,
            false // Does NOT require approval (read-only)
        );
    }

    /**
     * rename_file: Rename or move a file/directory
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec renameFile() {
        JsonObject schema = buildSchema(
            new Param("old_path", "string", "Current relative path of the file or directory", true),
            new Param("new_path", "string", "New relative path for the file or directory", true),
            new Param("reasoning", "string", "Brief explanation of why you're renaming/moving this file", false)
        );

        return new StormyToolSpec(
            "rename_file",
            "Rename a file/directory or move it to a different location. " +
            "Can be used to reorganize project structure (e.g., moving 'style.css' to 'css/style.css').",
            schema,
            ToolCategory.FILE_SYSTEM,
            true // Requires approval in non-agent mode
        );
    }

    /**
     * delete_file: Delete a file or directory
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec deleteFile() {
        JsonObject schema = buildSchema(
            new Param("path", "string", "Relative path to the file or directory to delete", true),
            new Param("reasoning", "string", "Brief explanation of why you're deleting this file", false)
        );

        return new StormyToolSpec(
            "delete_file",
            "Permanently delete a file or directory from the project. " +
            "Use with caution. Ensure the file is truly no longer needed before deleting.",
            schema,
            ToolCategory.FILE_SYSTEM,
            true // Requires approval in non-agent mode
        );
    }

    /**
     * copy_file: Copy a file to a new location
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec copyFile() {
        JsonObject schema = buildSchema(
            new Param("source_path", "string", "Relative path to the source file to copy", true),
            new Param("destination_path", "string", "Relative path where the copy should be created", true),
            new Param("reasoning", "string", "Brief explanation of why you're copying this file", false)
        );

        return new StormyToolSpec(
            "copy_file",
            "Create a copy of a file at a new location. " +
            "Useful for duplicating templates or creating similar files as starting points.",
            schema,
            ToolCategory.FILE_SYSTEM,
            true // Requires approval in non-agent mode
        );
    }

    /**
     * move_file: Move a file to a new location (similar to rename but more explicit)
     * Requires approval when agent mode is disabled
     */
    public static StormyToolSpec moveFile() {
        JsonObject schema = buildSchema(
            new Param("source_path", "string", "Current relative path of the file", true),
            new Param("destination_path", "string", "New relative path for the file", true),
            new Param("reasoning", "string", "Brief explanation of why you're moving this file", false)
        );

        return new StormyToolSpec(
            "move_file",
            "Move a file from one location to another. " +
            "The original file is removed after being copied to the new location.",
            schema,
            ToolCategory.FILE_SYSTEM,
            true // Requires approval in non-agent mode
        );
    }

    // ========================================================================
    // SEARCH & ANALYSIS TOOLS
    // ========================================================================

    /**
     * search_files: Search for patterns across multiple files
     * Does NOT require approval (read-only operation)
     */
    public static StormyToolSpec searchFiles() {
        JsonObject schema = buildSchema(
            new Param("directory", "string", "Relative path to search within (use '.' for entire project)", true),
            new Param("regex_pattern", "string", "Regular expression pattern to search for (e.g., 'class=\".*btn.*\"')", true),
            new Param("reasoning", "string", "Brief explanation of what you're searching for and why", false)
        );

        return new StormyToolSpec(
            "search_files",
            "Search for a regex pattern across all files in a directory. " +
            "Returns matching lines with context (file path, line number, surrounding lines). " +
            "Useful for finding all uses of a class, function, or pattern across the project.",
            schema,
            ToolCategory.SEARCH_ANALYSIS,
            false // Does NOT require approval (read-only)
        );
    }

    /**
     * list_code_definition_names: Extract classes, functions, and IDs from code
     * Does NOT require approval (read-only operation)
     */
    public static StormyToolSpec listCodeDefinitionNames() {
        JsonObject schema = buildSchema(
            new Param("directory", "string", "Relative path to analyze (use '.' for entire project)", true),
            new Param("reasoning", "string", "Brief explanation of why you need this overview", false)
        );

        return new StormyToolSpec(
            "list_code_definition_names",
            "Extract and list all code definitions from HTML, CSS, and JavaScript files: " +
            "- HTML: element IDs and significant classes\n" +
            "- CSS: class selectors and ID selectors\n" +
            "- JavaScript: function names and class names\n\n" +
            "Provides a high-level overview of the codebase structure.",
            schema,
            ToolCategory.SEARCH_ANALYSIS,
            false // Does NOT require approval (read-only)
        );
    }

    // ========================================================================
    // AGENT INTERACTION TOOLS
    // ========================================================================

    /**
     * ask_followup_question: Ask the user for clarification
     * Does NOT require approval (interaction tool)
     */
    public static StormyToolSpec askFollowupQuestion() {
        JsonObject schema = buildSchema(
            new Param("question", "string", "Clear, specific question to ask the user", true),
            new Param("reasoning", "string", "Brief explanation of why you need this information", false)
        );

        return new StormyToolSpec(
            "ask_followup_question",
            "Pause execution and ask the user a clarifying question. " +
            "Use this when you need critical information that isn't available in the codebase or context. " +
            "Be specific and actionable in your question.",
            schema,
            ToolCategory.AGENT_INTERACTION,
            false // Does NOT require approval (interaction tool)
        );
    }

    /**
     * attempt_completion: Signal that the task is complete
     * Does NOT require approval (interaction tool)
     */
    public static StormyToolSpec attemptCompletion() {
        JsonObject schema = buildSchema(
            new Param("summary", "string",
                "Comprehensive summary of what was accomplished. Include:\n" +
                "- What was created/modified\n" +
                "- Key features or changes\n" +
                "- Next steps or usage instructions\n" +
                "Use markdown formatting for clarity.",
                true),
            new Param("reasoning", "string", "Brief explanation of why the task is now complete", false)
        );

        return new StormyToolSpec(
            "attempt_completion",
            "Signal that you have successfully completed the user's request. " +
            "Provide a detailed summary of all work done. " +
            "The user will be presented with this summary and can either accept completion or provide feedback.",
            schema,
            ToolCategory.AGENT_INTERACTION,
            false // Does NOT require approval (interaction tool)
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Parameter class for building schemas
     */
    private static class Param {
        String name;
        String type;
        String description;
        boolean required;

        Param(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }
    }

    /**
     * Build a JSON schema object from parameters
     */
    private static JsonObject buildSchema(Param... params) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();

        for (Param param : params) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", param.type);
            prop.addProperty("description", param.description);
            properties.add(param.name, prop);

            if (param.required) {
                required.add(param.name);
            }
        }

        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    /**
     * Check if a tool requires user approval when agent mode is disabled
     */
    public static boolean toolRequiresApproval(String toolName, List<StormyToolSpec> tools) {
        for (StormyToolSpec tool : tools) {
            if (tool.getName().equals(toolName)) {
                return tool.requiresApproval();
            }
        }
        return false; // Unknown tools don't require approval by default
    }

    /**
     * Get all tools that modify the file system (for approval flow)
     */
    public static List<StormyToolSpec> getFileModificationTools() {
        List<StormyToolSpec> all = getStormyTools();
        List<StormyToolSpec> modifying = new ArrayList<>();

        for (StormyToolSpec tool : all) {
            if (tool.requiresApproval()) {
                modifying.add(tool);
            }
        }

        return modifying;
    }

    /**
     * Get all read-only tools (never require approval)
     */
    public static List<StormyToolSpec> getReadOnlyTools() {
        List<StormyToolSpec> all = getStormyTools();
        List<StormyToolSpec> readOnly = new ArrayList<>();

        for (StormyToolSpec tool : all) {
            if (!tool.requiresApproval()) {
                readOnly.add(tool);
            }
        }

        return readOnly;
    }
}
