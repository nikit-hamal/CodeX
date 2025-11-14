package com.codex.apk;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.codex.apk.ai.ToolExecutor;
import java.util.Map;
import com.google.gson.Gson;

public class ParallelToolExecutor {

    private final File projectDir;
    private final ToolExecutor toolExecutor;

    public ParallelToolExecutor(File projectDir) {
        this.projectDir = projectDir;
        this.toolExecutor = new ToolExecutor(null, projectDir.getAbsolutePath());
    }

    public static class ToolResult {
        public final String toolName;
        public final JsonObject result;

        public ToolResult(String toolName, JsonObject result) {
            this.toolName = toolName;
            this.result = result;
        }
    }

    public CompletableFuture<List<ToolResult>> executeTools(List<ChatMessage.ToolUsage> tools) {
        List<CompletableFuture<ToolResult>> futures = tools.stream()
                .map(this::executeSingleTool)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<ToolResult> executeSingleTool(ChatMessage.ToolUsage tool) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> args = new Gson().fromJson(tool.argsJson, Map.class);
                String result = toolExecutor.execute(tool.toolName, args);
                JsonObject resultJson = new JsonObject();
                resultJson.addProperty("ok", true);
                resultJson.addProperty("result", result);
                return new ToolResult(tool.toolName, resultJson);
            } catch (Exception e) {
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("ok", false);
                errorResult.addProperty("error", e.getMessage());
                return new ToolResult(tool.toolName, errorResult);
            }
        });
    }

    // A simple dependency partitioner. In a real scenario, this would be more complex.
    private List<List<ChatMessage.ToolUsage>> partitionToolsByDependency(List<ChatMessage.ToolUsage> tools) {
        // For now, we assume no dependencies and run all in parallel.
        List<List<ChatMessage.ToolUsage>> partitioned = new ArrayList<>();
        partitioned.add(tools);
        return partitioned;
    }
}
