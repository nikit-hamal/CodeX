package com.codex.apk.util;

public class JsonUtils {
    public static boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    public static String extractJsonFromCodeBlock(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String jsonPattern = "```json\\s*([\\s\\S]*?)```";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(jsonPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String genericPattern = "```\\s*([\\s\\S]*?)```";
        pattern = java.util.regex.Pattern.compile(genericPattern);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            if (looksLikeJson(extracted)) {
                return extracted;
            }
        }
        return null;
    }
}
