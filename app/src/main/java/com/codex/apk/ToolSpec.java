package com.codex.apk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

    public static JsonArray toJsonArray(java.util.List<ToolSpec> specs) {
        JsonArray arr = new JsonArray();
        for (ToolSpec spec : specs) {
            arr.add(spec.toJson());
        }
        return arr;
    }

    public static java.util.List<ToolSpec> defaultFileTools() {
        java.util.List<ToolSpec> tools = new java.util.ArrayList<>();

        tools.add(new ToolSpec(
                "write_to_file",
                "Create a new file or overwrite an existing file with the provided content.",
                buildSchema(
                        new String[]{"path", "content"},
                        new String[]{"string", "string"},
                        new String[]{"Relative path to the file", "Content to write to the file"}
                )));

        tools.add(new ToolSpec(
                "replace_in_file",
                "Perform a targeted search-and-replace in an existing file.",
                buildSchema(
                        new String[]{"path", "diff"},
                        new String[]{"string", "string"},
                        new String[]{"Relative path to the file", "The diff to apply to the file, in the specified format"}
                )));

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
