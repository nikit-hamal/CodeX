package com.codex.apk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.codex.apk.util.FileOps;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ToolExecutor {

    public static JsonObject execute(File projectDir, String name, JsonObject args) {
        JsonObject result = new JsonObject();
        try {
            switch (name) {
                // --- File I/O ---
                case "write_to_file": {
                    String path = args.get("path").getAsString();
                    String content = args.get("content").getAsString();
                    FileOps.createFile(projectDir, path, content);
                    result.addProperty("status", "success");
                    result.addProperty("message", "File written successfully: " + path);
                    break;
                }
                case "replace_in_file": {
                    String path = args.get("path").getAsString();
                    String diff = args.get("diff").getAsString();
                    String originalContent = FileOps.readFile(projectDir, path);
                    if (originalContent == null) {
                        result.addProperty("status", "error");
                        result.addProperty("message", "File not found: " + path);
                        break;
                    }
                    String newContent = FileOps.applyDiffPatch(originalContent, diff);
                    if (newContent == null) {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Failed to apply diff. Ensure the SEARCH block matches the file content exactly.");
                    } else {
                        FileOps.updateFile(projectDir, path, newContent);
                        result.addProperty("status", "success");
                        result.addProperty("message", "File updated successfully: " + path);
                    }
                    break;
                }
                case "read_file": {
                    String path = args.get("path").getAsString();
                    String content = FileOps.readFile(projectDir, path);
                    if (content == null) {
                        result.addProperty("status", "error");
                        result.addProperty("message", "File not found: " + path);
                    } else {
                        result.addProperty("status", "success");
                        result.addProperty("content", content);
                    }
                    break;
                }

                // --- File System ---
                case "list_files": {
                    String path = args.has("path") ? args.get("path").getAsString() : ".";
                    boolean recursive = args.has("recursive") && args.get("recursive").getAsBoolean();
                    // Use a simplified listing for now, or the existing tree builder
                    String tree = FileOps.buildFileTree(new File(projectDir, path), recursive ? 5 : 1, 1000);
                    result.addProperty("status", "success");
                    result.addProperty("files", tree);
                    break;
                }
                case "rename_file": {
                    String oldPath = args.get("old_path").getAsString();
                    String newPath = args.get("new_path").getAsString();
                    boolean ok = FileOps.renameFile(projectDir, oldPath, newPath);
                    if (ok) {
                        result.addProperty("status", "success");
                        result.addProperty("message", "Renamed " + oldPath + " to " + newPath);
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Failed to rename file.");
                    }
                    break;
                }
                case "delete_file": {
                    String path = args.get("path").getAsString();
                    boolean ok = FileOps.deleteRecursively(new File(projectDir, path));
                    if (ok) {
                        result.addProperty("status", "success");
                        result.addProperty("message", "Deleted " + path);
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Failed to delete file.");
                    }
                    break;
                }
                case "copy_file": {
                    String src = args.get("source_path").getAsString();
                    String dst = args.get("destination_path").getAsString();
                    boolean ok = FileOps.copyFile(projectDir, src, dst);
                    if (ok) {
                        result.addProperty("status", "success");
                        result.addProperty("message", "Copied " + src + " to " + dst);
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Failed to copy file.");
                    }
                    break;
                }
                case "move_file": {
                    String src = args.get("source_path").getAsString();
                    String dst = args.get("destination_path").getAsString();
                    boolean ok = FileOps.renameFile(projectDir, src, dst);
                     if (ok) {
                        result.addProperty("status", "success");
                        result.addProperty("message", "Moved " + src + " to " + dst);
                    } else {
                        result.addProperty("status", "error");
                        result.addProperty("message", "Failed to move file.");
                    }
                    break;
                }

                // --- Search ---
                case "search_files": {
                    String dir = args.get("directory").getAsString();
                    String regex = args.get("regex_pattern").getAsString();
                    JsonArray matches = FileOps.searchInProject(new File(projectDir, dir), regex, 50, true);
                    result.addProperty("status", "success");
                    result.add("matches", matches);
                    break;
                }
                case "list_code_definition_names": {
                    // Placeholder for now, just list files
                    String dir = args.get("directory").getAsString();
                    String tree = FileOps.buildFileTree(new File(projectDir, dir), 2, 500);
                    result.addProperty("status", "success");
                    result.addProperty("definitions", "Definition listing not fully implemented yet. Files:\n" + tree);
                    break;
                }

                // --- Agent Interaction ---
                case "ask_followup_question": {
                    String question = args.get("question").getAsString();
                    result.addProperty("status", "success");
                    result.addProperty("action", "ask_user");
                    result.addProperty("question", question);
                    break;
                }
                case "attempt_completion": {
                    String summary = args.get("summary").getAsString();
                    result.addProperty("status", "success");
                    result.addProperty("action", "complete_task");
                    result.addProperty("summary", summary);
                    break;
                }

                default: {
                    result.addProperty("status", "error");
                    result.addProperty("message", "Unknown tool: " + name);
                }
            }
        } catch (Exception e) {
            result.addProperty("status", "error");
            result.addProperty("message", "Exception: " + e.getMessage());
        }
        return result;
    }

    public static String buildToolResultContinuation(JsonArray results) {
        JsonObject payload = new JsonObject();
        payload.addProperty("role", "tool");
        payload.add("content", results.toString()); // Qwen expects string content for tool results usually, but let's check format
        // Actually, for Qwen/OpenAI, it's usually a list of messages.
        // But here we are returning a string to be appended to the conversation or sent as a new message.
        // Let's stick to the previous pattern but update the content.
        return payload.toString();
    }
}
