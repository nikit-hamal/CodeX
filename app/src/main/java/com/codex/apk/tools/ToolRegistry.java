package com.codex.apk.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private static final Map<String, Tool> toolMap = new HashMap<>();

    static {
        addTool(new WriteToFileTool());
        addTool(new ReplaceInFileTool());
        addTool(new ReadFileTool());
        addTool(new ListFilesTool());
        addTool(new RenameFileTool());
        addTool(new DeleteFileTool());
        addTool(new CopyFileTool());
        addTool(new MoveFileTool());
        addTool(new SearchFilesTool());
        addTool(new ListCodeDefinitionNamesTool());
        addTool(new AskFollowupQuestionTool());
        addTool(new AttemptCompletionTool());
    }

    private static void addTool(Tool tool) {
        toolMap.put(tool.getName(), tool);
    }

    public static Tool getTool(String name) {
        return toolMap.get(name);
    }

    public static List<Tool> getAllTools() {
        return new ArrayList<>(toolMap.values());
    }
}
