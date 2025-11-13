package com.codex.apk;

import android.util.Log;
import com.codex.apk.ai.ParsedResponse;
import com.codex.apk.ai.ResponseParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;

public class QwenResponseParser implements ResponseParser {
    private static final String TAG = "QwenResponseParser";

    @Override
    public com.codex.apk.ai.ParsedResponse parse(String json) {
        try {
            Log.d(TAG, "Parsing response: " + json.substring(0, Math.min(200, json.length())) + "...");
            JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
            com.codex.apk.ai.ParsedResponse parsed = new com.codex.apk.ai.ParsedResponse();
            parsed.rawResponse = json;

            // Single file operation
            if (jsonObj.has("action") && jsonObj.get("action").isJsonPrimitive()) {
                JsonObject singleOpWrapper = new JsonObject();
                JsonArray opsArray = new JsonArray();
                opsArray.add(jsonObj);
                singleOpWrapper.add("operations", opsArray);

                parsed.fileChanges = toFileActionDetails(parseFileOperationResponse(singleOpWrapper));
                parsed.action = "file_operation";
                parsed.isValid = true;
                return parsed;
            }

            // Plan response
            if (jsonObj.has("steps") && jsonObj.get("steps").isJsonArray()) {
                parsed.planSteps = new ArrayList<>();
                JsonArray arr = jsonObj.getAsJsonArray("steps");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject s = arr.get(i).getAsJsonObject();
                    String id = s.has("id") ? s.get("id").getAsString() : ("s" + (i + 1));
                    String title = s.has("title") ? s.get("title").getAsString() : ("Step " + (i + 1));
                    String kind = s.has("kind") ? s.get("kind").getAsString() : "file";
                    parsed.planSteps.add(new ChatMessage.PlanStep(id, title, kind));
                }
                parsed.explanation = jsonObj.has("goal") ? ("Plan for: " + jsonObj.get("goal").getAsString()) : "Plan";
                parsed.action = "plan";
                parsed.isValid = true;
                return parsed;
            }

            // File operations
            if (jsonObj.has("operations") && jsonObj.get("operations").isJsonArray()) {
                parsed.fileChanges = toFileActionDetails(parseFileOperationResponse(jsonObj));
                parsed.action = "file_operation";
                parsed.isValid = true;
                return parsed;
            }

            // Fallback for other JSON
            parsed.explanation = json;
            parsed.action = "message";
            parsed.isValid = true;
            return parsed;

        } catch (JsonParseException e) {
            Log.w(TAG, "Failed to parse JSON response: " + json, e);
            return fallback(json);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
            return fallback(json);
        }
    }

    private com.codex.apk.ai.ParsedResponse fallback(String json) {
        com.codex.apk.ai.ParsedResponse parsed = new com.codex.apk.ai.ParsedResponse();
        parsed.rawResponse = json;
        parsed.isValid = false;
        parsed.action = "message";
        parsed.explanation = json;
        return parsed;
    }

    private static ParsedFileOperationResponse parseFileOperationResponse(JsonObject jsonObj) {
        List<FileOperation> operations = new ArrayList<>();
        
        if (jsonObj.has("operations")) {
            JsonArray operationsArray = jsonObj.getAsJsonArray("operations");
            for (int i = 0; i < operationsArray.size(); i++) {
                JsonObject operation = operationsArray.get(i).getAsJsonObject();

                String type = operation.get("type").getAsString();
                String path = operation.has("path") ? operation.get("path").getAsString() : "";

                if (operation.has("modifyLines") && operation.get("modifyLines").isJsonArray()) {
                    JsonArray hunks = operation.getAsJsonArray("modifyLines");
                    for (int j = 0; j < hunks.size(); j++) {
                        try {
                            JsonObject h = hunks.get(j).getAsJsonObject();
                            String s = h.has("search") ? h.get("search").getAsString() : null;
                            String r = h.has("replace") ? h.get("replace").getAsString() : null;
                            if (s == null || r == null) continue;
                            operations.add(new FileOperation("searchAndReplace", path, "", "", "", s, r, null, null, null, null, null, null, null, null, null, null, null, null, null));
                        } catch (Exception ignored) {}
                    }
                    continue;
                }

                String content = operation.has("content") ? operation.get("content").getAsString() : (operation.has("newContent") ? operation.get("newContent").getAsString() : "");
                String oldPath = operation.has("oldPath") ? operation.get("oldPath").getAsString() : "";
                String newPath = operation.has("newPath") ? operation.get("newPath").getAsString() : "";
                String search = operation.has("search") ? operation.get("search").getAsString() : "";
                String replace = operation.has("replace") ? operation.get("replace").getAsString() : "";
                String updateType = operation.has("updateType") ? operation.get("updateType").getAsString() : null;
                String searchPattern = operation.has("searchPattern") ? operation.get("searchPattern").getAsString() : null;
                String replaceWith = operation.has("replaceWith") ? operation.get("replaceWith").getAsString() : null;
                String diffPatch = operation.has("diffPatch") ? operation.get("diffPatch").getAsString() : null;
                Boolean createBackup = operation.has("createBackup") ? operation.get("createBackup").getAsBoolean() : null;
                Boolean validateContent = operation.has("validateContent") ? operation.get("validateContent").getAsBoolean() : null;
                String contentType = operation.has("contentType") ? operation.get("contentType").getAsString() : null;
                String errorHandling = operation.has("errorHandling") ? operation.get("errorHandling").getAsString() : null;
                Boolean generateDiff = operation.has("generateDiff") ? operation.get("generateDiff").getAsBoolean() : null;
                String diffFormat = operation.has("diffFormat") ? operation.get("diffFormat").getAsString() : null;
                Integer startLine = operation.has("startLine") ? operation.get("startLine").getAsInt() : null;
                Integer deleteCount = operation.has("deleteCount") ? operation.get("deleteCount").getAsInt() : null;
                List<String> insertLines = null;
                if (operation.has("insertLines") && operation.get("insertLines").isJsonArray()) {
                    insertLines = new ArrayList<>();
                    JsonArray arr = operation.getAsJsonArray("insertLines");
                    for (int j = 0; j < arr.size(); j++) insertLines.add(arr.get(j).getAsString());
                }

                operations.add(new FileOperation(type, path, content, oldPath, newPath, search, replace, startLine, deleteCount, insertLines, updateType, searchPattern, replaceWith, diffPatch, createBackup, validateContent, contentType, errorHandling, generateDiff, diffFormat));
            }
        }
        
        String explanation = jsonObj.has("explanation") ? jsonObj.get("explanation").getAsString() : "";
        
        return new ParsedFileOperationResponse("file_operation", operations, new ArrayList<>(), explanation, true);
    }

    private static List<ChatMessage.FileActionDetail> toFileActionDetails(ParsedFileOperationResponse response) {
        List<ChatMessage.FileActionDetail> details = new ArrayList<>();
        
        for (FileOperation op : response.operations) {
            ChatMessage.FileActionDetail detail = new ChatMessage.FileActionDetail(op.type, op.path, op.oldPath, op.newPath, "", op.content, op.startLine != null ? op.startLine : 0, op.deleteCount != null ? op.deleteCount : 0, op.insertLines, op.search, op.replace);
            if (op.updateType != null) detail.updateType = op.updateType;
            if (op.searchPattern != null) detail.searchPattern = op.searchPattern;
            if (op.replaceWith != null) detail.replaceWith = op.replaceWith;
            if (op.diffPatch != null) detail.diffPatch = op.diffPatch;
            if (op.createBackup != null) detail.createBackup = op.createBackup;
            if (op.validateContent != null) detail.validateContent = op.validateContent;
            if (op.contentType != null) detail.contentType = op.contentType;
            if (op.errorHandling != null) detail.errorHandling = op.errorHandling;
            if (op.generateDiff != null) detail.generateDiff = op.generateDiff;
            if (op.diffFormat != null) detail.diffFormat = op.diffFormat;
            details.add(detail);
        }
        
        return details;
    }

    private static class FileOperation {
        public final String type, path, content, oldPath, newPath, search, replace;
        public final Integer startLine, deleteCount;
        public final List<String> insertLines;
        public final String updateType, searchPattern, replaceWith, diffPatch;
        public final Boolean createBackup, validateContent;
        public final String contentType, errorHandling;
        public final Boolean generateDiff;
        public final String diffFormat;

        public FileOperation(String type, String path, String content, String oldPath, String newPath, String search, String replace, Integer startLine, Integer deleteCount, List<String> insertLines, String updateType, String searchPattern, String replaceWith, String diffPatch, Boolean createBackup, Boolean validateContent, String contentType, String errorHandling, Boolean generateDiff, String diffFormat) {
            this.type = type; this.path = path; this.content = content; this.oldPath = oldPath; this.newPath = newPath; this.search = search; this.replace = replace;
            this.startLine = startLine; this.deleteCount = deleteCount; this.insertLines = insertLines;
            this.updateType = updateType; this.searchPattern = searchPattern; this.replaceWith = replaceWith; this.diffPatch = diffPatch;
            this.createBackup = createBackup; this.validateContent = validateContent; this.contentType = contentType; this.errorHandling = errorHandling;
            this.generateDiff = generateDiff; this.diffFormat = diffFormat;
        }
    }

    private static class ParsedFileOperationResponse {
        public String action;
        public List<FileOperation> operations;
        public List<PlanStep> planSteps;
        public String explanation;
        public boolean isValid;
        public String rawResponse;

        public ParsedFileOperationResponse(String action, List<FileOperation> operations, List<PlanStep> planSteps, String explanation, boolean isValid) {
            this.action = action; this.operations = operations; this.planSteps = planSteps; this.explanation = explanation; this.isValid = isValid;
        }

        public ParsedFileOperationResponse() {
            this.operations = new ArrayList<>(); this.planSteps = new ArrayList<>();
        }
    }

    private static class PlanStep {
        public final String id, title, kind;
        public PlanStep(String id, String title, String kind) {
            this.id = id; this.title = title; this.kind = kind;
        }
    }
}