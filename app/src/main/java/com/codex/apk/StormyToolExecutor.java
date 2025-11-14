package com.codex.apk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.codex.apk.util.FileOps;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade tool executor for Stormy's new toolset.
 *
 * Executes file I/O, file system, search/analysis, and interaction tools
 * with comprehensive error handling and detailed result reporting.
 *
 * Each tool returns a standardized JsonObject with:
 * - ok: boolean (success/failure)
 * - message: string (human-readable result)
 * - Additional fields specific to the tool
 * - error: string (only present on failure)
 */
public class StormyToolExecutor {

    // Maximum file size to read/display (20MB)
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024;

    // Maximum content length for search results
    private static final int MAX_SEARCH_RESULTS = 500;

    /**
     * Execute a tool with the given name and arguments
     *
     * @param projectDir The project's root directory
     * @param toolName   The name of the tool to execute
     * @param args       JSON object containing tool arguments
     * @return JsonObject with execution results
     */
    public static JsonObject execute(File projectDir, String toolName, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            switch (toolName) {
                // ========================================================================
                // FILE I/O TOOLS
                // ========================================================================

                case "write_to_file":
                    return executeWriteToFile(projectDir, args);

                case "replace_in_file":
                    return executeReplaceInFile(projectDir, args);

                case "read_file":
                    return executeReadFile(projectDir, args);

                // ========================================================================
                // FILE SYSTEM TOOLS
                // ========================================================================

                case "list_files":
                    return executeListFiles(projectDir, args);

                case "rename_file":
                    return executeRenameFile(projectDir, args);

                case "delete_file":
                    return executeDeleteFile(projectDir, args);

                case "copy_file":
                    return executeCopyFile(projectDir, args);

                case "move_file":
                    return executeMoveFile(projectDir, args);

                // ========================================================================
                // SEARCH & ANALYSIS TOOLS
                // ========================================================================

                case "search_files":
                    return executeSearchFiles(projectDir, args);

                case "list_code_definition_names":
                    return executeListCodeDefinitionNames(projectDir, args);

                // ========================================================================
                // AGENT INTERACTION TOOLS
                // ========================================================================

                case "ask_followup_question":
                    return executeAskFollowupQuestion(args);

                case "attempt_completion":
                    return executeAttemptCompletion(args);

                // ========================================================================
                // UNKNOWN TOOL
                // ========================================================================

                default:
                    result.addProperty("ok", false);
                    result.addProperty("error", "Unknown tool: " + toolName);
                    return result;
            }
        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", "Tool execution failed: " + e.getMessage());
            return result;
        }
    }

    // ============================================================================
    // FILE I/O TOOL IMPLEMENTATIONS
    // ============================================================================

    private static JsonObject executeWriteToFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String path = getRequiredString(args, "path");
            String content = getRequiredString(args, "content");

            File targetFile = new File(projectDir, path);

            // Create parent directories if needed
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Write the file
            Files.write(targetFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

            result.addProperty("ok", true);
            result.addProperty("message", "Successfully wrote " + content.length() + " characters to " + path);
            result.addProperty("path", path);
            result.addProperty("bytes_written", content.getBytes("UTF-8").length);
            result.addProperty("lines_written", content.split("\n").length);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeReplaceInFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String path = getRequiredString(args, "path");
            String diff = getRequiredString(args, "diff");

            File targetFile = new File(projectDir, path);

            if (!targetFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "File not found: " + path);
                return result;
            }

            // Read current content
            String currentContent = FileOps.readFile(projectDir, path);
            if (currentContent == null) {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to read file: " + path);
                return result;
            }

            // Parse the SEARCH/REPLACE block
            SearchReplaceBlock block = parseSearchReplaceBlock(diff);
            if (block == null) {
                result.addProperty("ok", false);
                result.addProperty("error", "Invalid diff format. Expected:\n<<<<<<< SEARCH\n...\n=======\n...\n>>>>>>> REPLACE");
                return result;
            }

            // Find the search text
            int index = currentContent.indexOf(block.search);
            if (index == -1) {
                result.addProperty("ok", false);
                result.addProperty("error", "SEARCH block not found in file. Ensure the search text is exact and unique.");
                result.addProperty("search_text", block.search);
                return result;
            }

            // Check if search text appears multiple times
            int secondIndex = currentContent.indexOf(block.search, index + 1);
            if (secondIndex != -1) {
                result.addProperty("ok", false);
                result.addProperty("error", "SEARCH block appears multiple times in file. Make the search text more specific.");
                result.addProperty("search_text", block.search);
                return result;
            }

            // Perform the replacement
            String newContent = currentContent.substring(0, index) +
                               block.replace +
                               currentContent.substring(index + block.search.length());

            // Write the modified content
            Files.write(targetFile.toPath(), newContent.getBytes(StandardCharsets.UTF_8));

            // Calculate diff statistics
            int oldLines = currentContent.split("\n").length;
            int newLines = newContent.split("\n").length;
            int linesAdded = Math.max(0, newLines - oldLines);
            int linesRemoved = Math.max(0, oldLines - newLines);

            result.addProperty("ok", true);
            result.addProperty("message", "Successfully modified " + path);
            result.addProperty("path", path);
            result.addProperty("lines_added", linesAdded);
            result.addProperty("lines_removed", linesRemoved);
            result.addProperty("diff", diff); // Include original diff for UI display

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeReadFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String path = getRequiredString(args, "path");
            File targetFile = new File(projectDir, path);

            if (!targetFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "File not found: " + path);
                return result;
            }

            if (targetFile.isDirectory()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Cannot read a directory. Use list_files instead: " + path);
                return result;
            }

            // Check file size
            long fileSize = targetFile.length();
            if (fileSize > MAX_FILE_SIZE) {
                result.addProperty("ok", false);
                result.addProperty("error", "File too large to read: " + formatFileSize(fileSize) +
                                           " (max: " + formatFileSize(MAX_FILE_SIZE) + ")");
                return result;
            }

            // Read the file
            String content = FileOps.readFile(projectDir, path);
            if (content == null) {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to read file: " + path);
                return result;
            }

            result.addProperty("ok", true);
            result.addProperty("message", "Successfully read " + path);
            result.addProperty("content", content);
            result.addProperty("path", path);
            result.addProperty("size_bytes", fileSize);
            result.addProperty("lines", content.split("\n").length);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    // ============================================================================
    // FILE SYSTEM TOOL IMPLEMENTATIONS
    // ============================================================================

    private static JsonObject executeListFiles(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String path = getRequiredString(args, "path");
            boolean recursive = args.has("recursive") && args.get("recursive").getAsBoolean();

            File targetDir = new File(projectDir, path);

            if (!targetDir.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Directory not found: " + path);
                return result;
            }

            if (!targetDir.isDirectory()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Not a directory: " + path);
                return result;
            }

            JsonArray files = new JsonArray();

            if (recursive) {
                // Recursive listing
                listFilesRecursive(targetDir, "", files, 0, 5, 1000);
            } else {
                // Non-recursive listing
                File[] fileList = targetDir.listFiles();
                if (fileList != null) {
                    for (File file : fileList) {
                        files.add(createFileEntry(file, file.getName()));
                    }
                }
            }

            result.addProperty("ok", true);
            result.addProperty("message", "Listed " + files.size() + " items in " + path);
            result.add("files", files);
            result.addProperty("path", path);
            result.addProperty("recursive", recursive);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeRenameFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String oldPath = getRequiredString(args, "old_path");
            String newPath = getRequiredString(args, "new_path");

            File oldFile = new File(projectDir, oldPath);
            File newFile = new File(projectDir, newPath);

            if (!oldFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Source file not found: " + oldPath);
                return result;
            }

            if (newFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Destination already exists: " + newPath);
                return result;
            }

            // Create parent directories for new path if needed
            File newParent = newFile.getParentFile();
            if (newParent != null && !newParent.exists()) {
                newParent.mkdirs();
            }

            // Perform the rename/move
            boolean success = oldFile.renameTo(newFile);

            if (success) {
                result.addProperty("ok", true);
                result.addProperty("message", "Successfully renamed " + oldPath + " to " + newPath);
                result.addProperty("old_path", oldPath);
                result.addProperty("new_path", newPath);
            } else {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to rename file. Check permissions.");
            }

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeDeleteFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String path = getRequiredString(args, "path");
            File targetFile = new File(projectDir, path);

            if (!targetFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "File not found: " + path);
                return result;
            }

            // Delete recursively if directory
            boolean success = FileOps.deleteRecursively(targetFile);

            if (success) {
                result.addProperty("ok", true);
                result.addProperty("message", "Successfully deleted " + path);
                result.addProperty("path", path);
            } else {
                result.addProperty("ok", false);
                result.addProperty("error", "Failed to delete file. Check permissions.");
            }

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeCopyFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String sourcePath = getRequiredString(args, "source_path");
            String destPath = getRequiredString(args, "destination_path");

            File sourceFile = new File(projectDir, sourcePath);
            File destFile = new File(projectDir, destPath);

            if (!sourceFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Source file not found: " + sourcePath);
                return result;
            }

            if (sourceFile.isDirectory()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Cannot copy directories (only files): " + sourcePath);
                return result;
            }

            if (destFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Destination already exists: " + destPath);
                return result;
            }

            // Create parent directories for destination if needed
            File destParent = destFile.getParentFile();
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs();
            }

            // Perform the copy
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

            result.addProperty("ok", true);
            result.addProperty("message", "Successfully copied " + sourcePath + " to " + destPath);
            result.addProperty("source_path", sourcePath);
            result.addProperty("destination_path", destPath);
            result.addProperty("bytes_copied", sourceFile.length());

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeMoveFile(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String sourcePath = getRequiredString(args, "source_path");
            String destPath = getRequiredString(args, "destination_path");

            File sourceFile = new File(projectDir, sourcePath);
            File destFile = new File(projectDir, destPath);

            if (!sourceFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Source file not found: " + sourcePath);
                return result;
            }

            if (destFile.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Destination already exists: " + destPath);
                return result;
            }

            // Create parent directories for destination if needed
            File destParent = destFile.getParentFile();
            if (destParent != null && !destParent.exists()) {
                destParent.mkdirs();
            }

            // Perform the move
            Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE);

            result.addProperty("ok", true);
            result.addProperty("message", "Successfully moved " + sourcePath + " to " + destPath);
            result.addProperty("source_path", sourcePath);
            result.addProperty("destination_path", destPath);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    // ============================================================================
    // SEARCH & ANALYSIS TOOL IMPLEMENTATIONS
    // ============================================================================

    private static JsonObject executeSearchFiles(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String directory = getRequiredString(args, "directory");
            String regexPattern = getRequiredString(args, "regex_pattern");

            File searchDir = new File(projectDir, directory);

            if (!searchDir.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Directory not found: " + directory);
                return result;
            }

            // Compile the regex pattern
            Pattern pattern;
            try {
                pattern = Pattern.compile(regexPattern);
            } catch (Exception e) {
                result.addProperty("ok", false);
                result.addProperty("error", "Invalid regex pattern: " + e.getMessage());
                return result;
            }

            // Search files
            JsonArray matches = new JsonArray();
            searchFilesWithPattern(searchDir, "", pattern, matches, MAX_SEARCH_RESULTS);

            result.addProperty("ok", true);
            result.addProperty("message", "Found " + matches.size() + " matches for pattern: " + regexPattern);
            result.add("matches", matches);
            result.addProperty("directory", directory);
            result.addProperty("pattern", regexPattern);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeListCodeDefinitionNames(File projectDir, JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String directory = getRequiredString(args, "directory");
            File searchDir = new File(projectDir, directory);

            if (!searchDir.exists()) {
                result.addProperty("ok", false);
                result.addProperty("error", "Directory not found: " + directory);
                return result;
            }

            JsonObject definitions = new JsonObject();
            JsonArray htmlIds = new JsonArray();
            JsonArray cssClasses = new JsonArray();
            JsonArray jsFunctionNames = new JsonArray();

            extractCodeDefinitions(searchDir, htmlIds, cssClasses, jsFunctionNames);

            definitions.add("html_ids", htmlIds);
            definitions.add("css_classes", cssClasses);
            definitions.add("js_functions", jsFunctionNames);

            result.addProperty("ok", true);
            result.addProperty("message", "Extracted definitions from " + directory);
            result.add("definitions", definitions);
            result.addProperty("directory", directory);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    // ============================================================================
    // AGENT INTERACTION TOOL IMPLEMENTATIONS
    // ============================================================================

    private static JsonObject executeAskFollowupQuestion(JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String question = getRequiredString(args, "question");

            result.addProperty("ok", true);
            result.addProperty("message", "Asking user for clarification");
            result.addProperty("question", question);
            result.addProperty("awaiting_user_response", true);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    private static JsonObject executeAttemptCompletion(JsonObject args) {
        JsonObject result = new JsonObject();

        try {
            String summary = getRequiredString(args, "summary");

            result.addProperty("ok", true);
            result.addProperty("message", "Task completion proposed");
            result.addProperty("summary", summary);
            result.addProperty("completion_attempted", true);

        } catch (Exception e) {
            result.addProperty("ok", false);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Search/Replace block parser
     */
    private static class SearchReplaceBlock {
        String search;
        String replace;

        SearchReplaceBlock(String search, String replace) {
            this.search = search;
            this.replace = replace;
        }
    }

    private static SearchReplaceBlock parseSearchReplaceBlock(String diff) {
        // Expected format:
        // <<<<<<< SEARCH
        // [search text]
        // =======
        // [replace text]
        // >>>>>>> REPLACE

        Pattern pattern = Pattern.compile(
            "<<<<<<< SEARCH\\s*\\n(.*?)\\n=======\\s*\\n(.*?)\\n>>>>>>> REPLACE",
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(diff);
        if (matcher.find()) {
            String search = matcher.group(1);
            String replace = matcher.group(2);
            return new SearchReplaceBlock(search, replace);
        }

        return null;
    }

    /**
     * Get a required string parameter from args
     */
    private static String getRequiredString(JsonObject args, String key) {
        if (!args.has(key)) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return args.get(key).getAsString();
    }

    /**
     * Format file size for human reading
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    /**
     * Recursively list files with depth limit
     */
    private static void listFilesRecursive(File dir, String relativePath, JsonArray result,
                                          int currentDepth, int maxDepth, int maxEntries) {
        if (currentDepth >= maxDepth || result.size() >= maxEntries) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (result.size() >= maxEntries) break;

            String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
            result.add(createFileEntry(file, path));

            if (file.isDirectory()) {
                listFilesRecursive(file, path, result, currentDepth + 1, maxDepth, maxEntries);
            }
        }
    }

    /**
     * Create a JSON entry for a file
     */
    private static JsonObject createFileEntry(File file, String path) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", file.getName());
        entry.addProperty("path", path);
        entry.addProperty("type", file.isDirectory() ? "directory" : "file");
        entry.addProperty("size", file.length());
        entry.addProperty("modified", file.lastModified());
        return entry;
    }

    /**
     * Search files with a regex pattern
     */
    private static void searchFilesWithPattern(File dir, String relativePath, Pattern pattern,
                                               JsonArray matches, int maxResults) {
        if (matches.size() >= maxResults) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (matches.size() >= maxResults) break;

            String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();

            if (file.isDirectory()) {
                searchFilesWithPattern(file, path, pattern, matches, maxResults);
            } else if (isTextFile(file.getName())) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
                    String[] lines = content.split("\n");

                    for (int i = 0; i < lines.length; i++) {
                        if (matches.size() >= maxResults) break;

                        Matcher matcher = pattern.matcher(lines[i]);
                        if (matcher.find()) {
                            JsonObject match = new JsonObject();
                            match.addProperty("file", path);
                            match.addProperty("line", i + 1);
                            match.addProperty("content", lines[i].trim());
                            matches.add(match);
                        }
                    }
                } catch (IOException ignored) {
                    // Skip files that can't be read
                }
            }
        }
    }

    /**
     * Extract code definitions from HTML, CSS, and JS files
     */
    private static void extractCodeDefinitions(File dir, JsonArray htmlIds,
                                              JsonArray cssClasses, JsonArray jsFunctions) {
        Set<String> seenIds = new HashSet<>();
        Set<String> seenClasses = new HashSet<>();
        Set<String> seenFunctions = new HashSet<>();

        extractDefinitionsRecursive(dir, seenIds, seenClasses, seenFunctions);

        // Convert sets to arrays
        for (String id : seenIds) htmlIds.add(id);
        for (String cls : seenClasses) cssClasses.add(cls);
        for (String fn : seenFunctions) jsFunctions.add(fn);
    }

    private static void extractDefinitionsRecursive(File dir, Set<String> ids,
                                                   Set<String> classes, Set<String> functions) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                extractDefinitionsRecursive(file, ids, classes, functions);
            } else {
                String name = file.getName().toLowerCase();
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");

                    if (name.endsWith(".html")) {
                        // Extract IDs: id="..."
                        Pattern idPattern = Pattern.compile("id=[\"']([^\"']+)[\"']");
                        Matcher matcher = idPattern.matcher(content);
                        while (matcher.find()) {
                            ids.add(matcher.group(1));
                        }
                    } else if (name.endsWith(".css")) {
                        // Extract class selectors: .classname
                        Pattern classPattern = Pattern.compile("\\.([a-zA-Z_][a-zA-Z0-9_-]*)");
                        Matcher matcher = classPattern.matcher(content);
                        while (matcher.find()) {
                            classes.add(matcher.group(1));
                        }
                    } else if (name.endsWith(".js")) {
                        // Extract function declarations: function name(
                        Pattern funcPattern = Pattern.compile("function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
                        Matcher matcher = funcPattern.matcher(content);
                        while (matcher.find()) {
                            functions.add(matcher.group(1));
                        }

                        // Extract arrow functions: const name = (
                        Pattern arrowPattern = Pattern.compile("const\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*\\(");
                        matcher = arrowPattern.matcher(content);
                        while (matcher.find()) {
                            functions.add(matcher.group(1));
                        }
                    }
                } catch (IOException ignored) {
                    // Skip files that can't be read
                }
            }
        }
    }

    /**
     * Check if a file is likely a text file based on extension
     */
    private static boolean isTextFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".css") ||
               lower.endsWith(".js") || lower.endsWith(".txt") ||
               lower.endsWith(".json") || lower.endsWith(".md");
    }
}
