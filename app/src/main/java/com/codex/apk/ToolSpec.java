package com.codex.apk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class ToolSpec {

    private final String name;
    private final String description;
    private final JsonObject parametersSchema;

    public ToolSpec(String name, String description, JsonObject parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getName() {
        return name;
    }

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

    public static JsonArray toJsonArray(List<ToolSpec> specs) {
        JsonArray arr = new JsonArray();
        for (ToolSpec spec : specs) {
            arr.add(spec.toJson());
        }
        return arr;
    }

    public static List<ToolSpec> getStormyTools() {
        List<ToolSpec> tools = new ArrayList<>();

        // --- File I/O ---
        tools.add(new ToolSpec(
            "write_to_file",
            "Writes content to a file. Overwrites existing files or creates new ones.",
            buildSchema(
                new String[]{"path", "content"},
                new String[]{"string", "string"},
                new String[]{"The relative path to the file.", "The full content to write."}
            )
        ));

        tools.add(new ToolSpec(
            "replace_in_file",
            "Replaces a specific section of a file using a SEARCH/REPLACE block.",
            buildSchema(
                new String[]{"path", "diff"},
                new String[]{"string", "string"},
                new String[]{"The relative path to the file.", "The diff string containing <<<<<<< SEARCH, =======, and >>>>>>> REPLACE blocks."}
            )
        ));

        tools.add(new ToolSpec(
            "read_file",
            "Reads the full content of a file.",
            buildSchema(
                new String[]{"path"},
                new String[]{"string"},
                new String[]{"The relative path to the file."}
            )
        ));

        // --- File System ---
        tools.add(new ToolSpec(
            "list_files",
            "Lists files and directories in a given path.",
            buildSchema(
                new String[]{"path", "recursive"},
                new String[]{"string", "boolean"},
                new String[]{"The relative path to the directory (use '.' for root).", "Whether to list recursively."}
            )
        ));

        tools.add(new ToolSpec(
            "rename_file",
            "Renames or moves a file or directory.",
            buildSchema(
                new String[]{"old_path", "new_path"},
                new String[]{"string", "string"},
                new String[]{"The current path.", "The new path."}
            )
        ));

        tools.add(new ToolSpec(
            "delete_file",
            "Deletes a file or directory.",
            buildSchema(
                new String[]{"path"},
                new String[]{"string"},
                new String[]{"The path to delete."}
            )
        ));

        tools.add(new ToolSpec(
            "copy_file",
            "Copies a file from source to destination.",
            buildSchema(
                new String[]{"source_path", "destination_path"},
                new String[]{"string", "string"},
                new String[]{"The source file path.", "The destination file path."}
            )
        ));
        
        tools.add(new ToolSpec(
            "move_file",
            "Moves a file from source to destination.",
            buildSchema(
                new String[]{"source_path", "destination_path"},
                new String[]{"string", "string"},
                new String[]{"The source file path.", "The destination file path."}
            )
        ));

        // --- Search ---
        tools.add(new ToolSpec(
            "search_files",
            "Searches for a regex pattern across files in a directory.",
            buildSchema(
                new String[]{"directory", "regex_pattern"},
                new String[]{"string", "string"},
                new String[]{"The directory to search in.", "The regex pattern to search for."}
            )
        ));

        tools.add(new ToolSpec(
            "list_code_definition_names",
            "Lists definition names (classes, functions) in source files.",
            buildSchema(
                new String[]{"directory"},
                new String[]{"string"},
                new String[]{"The directory to scan."}
            )
        ));

        // --- Agent Interaction ---
        tools.add(new ToolSpec(
            "ask_followup_question",
            "Asks the user a question to clarify requirements.",
            buildSchema(
                new String[]{"question"},
                new String[]{"string"},
                new String[]{"The question to ask the user."}
            )
        ));

        tools.add(new ToolSpec(
            "attempt_completion",
            "Signals that the task is complete.",
            buildSchema(
                new String[]{"summary"},
                new String[]{"string"},
                new String[]{"A summary of what was accomplished."}
            )
        ));

        return tools;
    }

    private static JsonObject buildSchema(String[] keys, String[] types, String[] descriptions) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        for (int i = 0; i < keys.length; i++) {
            JsonObject field = new JsonObject();
            field.addProperty("type", types[i]);
            if (descriptions != null && i < descriptions.length) {
                field.addProperty("description", descriptions[i]);
            }
            props.add(keys[i], field);
        }
        schema.add("properties", props);

        JsonArray req = new JsonArray();
        for (String k : keys) req.add(k);
        schema.add("required", req);
        return schema;
    }
}