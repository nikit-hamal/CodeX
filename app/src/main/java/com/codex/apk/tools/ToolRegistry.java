package com.codex.apk.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private static ToolRegistry instance;
    private final Map<String, Tool> tools = new HashMap<>();

    private ToolRegistry() {
    }

    public static synchronized ToolRegistry getInstance() {
        if (instance == null) {
            instance = new ToolRegistry();
        }
        return instance;
    }

    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }
}
