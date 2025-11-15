package com.codex.apk.ai;

import android.content.Context;
import com.codex.apk.util.FileOps;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;

public class ToolExecutor {

    private final Context context;
    private final String projectPath;

    public ToolExecutor(Context context, String projectPath) {
        this.context = context;
        this.projectPath = projectPath;
    }

    public String execute(String toolName, Map<String, Object> args) {
        switch (toolName) {
            case "write_to_file":
                return writeToFile((String) args.get("path"), (String) args.get("content"));
            case "replace_in_file":
                return replaceInFile((String) args.get("path"), (String) args.get("diff"));
            case "read_file":
                return readFile((String) args.get("path"));
            case "list_files":
                return listFiles((String) args.get("path"), (Boolean) args.get("recursive"));
            case "rename_file":
                return renameFile((String) args.get("old_path"), (String) args.get("new_path"));
            case "delete_file":
                return deleteFile((String) args.get("path"));
            case "copy_file":
                return copyFile((String) args.get("source_path"), (String) args.get("destination_path"));
            case "move_file":
                return moveFile((String) args.get("source_path"), (String) args.get("destination_path"));
            case "search_files":
                return searchFiles((String) args.get("directory"), (String) args.get("regex_pattern"));
            case "list_code_definition_names":
                return listCodeDefinitionNames((String) args.get("directory"));
            case "ask_followup_question":
                return askFollowupQuestion((String) args.get("question"));
            case "attempt_completion":
                return attemptCompletion((String) args.get("summary"));
            default:
                return "Unknown tool: " + toolName;
        }
    }

    private String writeToFile(String path, String content) {
        File file = new File(projectPath, path);
        try {
            FileOps.createFile(new File(projectPath), path, content);
            return "File written successfully.";
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    private String replaceInFile(String path, String diff) {
        File file = new File(projectPath, path);
        try {
            String content = FileOps.readFile(new File(projectPath), path);
            String[] parts = diff.split("\n=======\n");
            String search = parts[0].substring("------- SEARCH\n".length());
            String replace = parts[1].substring("+++++++ REPLACE\n".length());
            String newContent = content.replaceFirst(Pattern.quote(search), replace);
            FileOps.updateFile(new File(projectPath), path, newContent);
            return "File updated successfully.";
        } catch (Exception e) {
            return "Error replacing in file: " + e.getMessage();
        }
    }

    private String readFile(String path) {
        try {
            return FileOps.readFile(new File(projectPath), path);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String listFiles(String path, boolean recursive) {
        File file = new File(projectPath, path);
        StringBuilder sb = new StringBuilder();
        listFiles(file, "", sb, recursive);
        return sb.toString();
    }

    private void listFiles(File file, String indent, StringBuilder sb, boolean recursive) {
        sb.append(indent).append(file.getName()).append("\n");
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                if (recursive || child.isDirectory()) {
                    listFiles(child, indent + "  ", sb, recursive);
                } else {
                    sb.append(indent).append("  ").append(child.getName()).append("\n");
                }
            }
        }
    }

    private String renameFile(String oldPath, String newPath) {
        if (FileOps.renameFile(new File(projectPath), oldPath, newPath)) {
            return "File renamed successfully.";
        } else {
            return "Error renaming file.";
        }
    }

    private String deleteFile(String path) {
        File file = new File(projectPath, path);
        if (FileOps.deleteRecursively(file)) {
            return "File deleted successfully.";
        } else {
            return "Error deleting file.";
        }
    }

    private String copyFile(String sourcePath, String destinationPath) {
        File source = new File(projectPath, sourcePath);
        File dest = new File(projectPath, destinationPath);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return "File copied successfully.";
        } catch (IOException e) {
            return "Error copying file: " + e.getMessage();
        }
    }

    private String moveFile(String sourcePath, String destinationPath) {
        File source = new File(projectPath, sourcePath);
        File dest = new File(projectPath, destinationPath);
        if (source.renameTo(dest)) {
            return "File moved successfully.";
        } else {
            return "Error moving file.";
        }
    }

    private String searchFiles(String directory, String regexPattern) {
        File dir = new File(projectPath, directory);
        List<FileOps.LineSearchResult> results = FileOps.searchInFiles(dir, regexPattern, false, true, null, 100);
        StringBuilder sb = new StringBuilder();
        for (FileOps.LineSearchResult result : results) {
            sb.append(result.getFileName()).append(":").append(result.getLineNumber()).append(": ").append(result.getLineContent()).append("\n");
        }
        return sb.toString();
    }

    private String listCodeDefinitionNames(String directory) {
        File dir = new File(projectPath, directory);
        StringBuilder sb = new StringBuilder();
        listCodeDefinitionNames(dir, sb);
        return sb.toString();
    }

    private void listCodeDefinitionNames(File dir, StringBuilder sb) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                listCodeDefinitionNames(file, sb);
            } else {
                sb.append(file.getName()).append(":\n");
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    if (file.getName().endsWith(".java")) {
                        // Java class and method names
                        Pattern p = Pattern.compile("(class|interface|enum)\\s+(\\w+)");
                        Matcher m = p.matcher(content);
                        while (m.find()) {
                            sb.append("  - ").append(m.group(2)).append("\n");
                        }
                        p = Pattern.compile("(public|protected|private|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *\\([^\\)]*\\) *(\\{?|[^;])");
                        m = p.matcher(content);
                        while (m.find()) {
                            sb.append("    - ").append(m.group(2)).append("\n");
                        }
                    } else if (file.getName().endsWith(".js")) {
                        // JavaScript function and class names
                        Pattern p = Pattern.compile("function\\s+(\\w+)");
                        Matcher m = p.matcher(content);
                        while (m.find()) {
                            sb.append("  - ").append(m.group(1)).append("\n");
                        }
                        p = Pattern.compile("class\\s+(\\w+)");
                        m = p.matcher(content);
                        while (m.find()) {
                            sb.append("  - ").append(m.group(1)).append("\n");
                        }
                    }
                } catch (IOException e) {
                    sb.append("  - Error reading file: ").append(e.getMessage()).append("\n");
                }
            }
        }
    }

    private String askFollowupQuestion(String question) {
        // This tool will be handled by the AIAssistant, which will pause the workflow and ask the user the question.
        return "Asking user: " + question;
    }

    private String attemptCompletion(String summary) {
        // This tool will be handled by the AIAssistant, which will signal that the task is complete.
        return "Task complete: " + summary;
    }
}
