package com.codex.apk;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialog for approving file modification operations when Agent Mode is disabled.
 *
 * Shows:
 * - Tool description (what will happen)
 * - Reasoning (why Stormy wants to do this)
 * - Preview (diff view, file content, etc.)
 * - Warning about file modifications
 */
public class StormyApprovalDialog extends DialogFragment {
    private static final String ARG_RESPONSE = "response";
    private static final String ARG_TOOL = "tool";
    private static final String ARG_ARGS = "args";
    private static final String ARG_REASONING = "reasoning";

    private StormyResponseParser.StormyParsedResponse response;
    private ApprovalCallback callback;

    /**
     * Callback interface for approval decisions
     */
    public interface ApprovalCallback {
        void onApproved();
        void onRejected(String reason);
    }

    /**
     * Create and show the approval dialog
     */
    public static void show(FragmentManager fragmentManager,
                           StormyResponseParser.StormyParsedResponse response,
                           ApprovalCallback callback) {
        StormyApprovalDialog dialog = new StormyApprovalDialog();

        // Store response data in arguments
        Bundle args = new Bundle();
        args.putString(ARG_RESPONSE, response.rawResponse);
        args.putString(ARG_TOOL, response.tool);
        args.putString(ARG_ARGS, response.toolArgs.toString());
        if (response.reasoning != null) {
            args.putString(ARG_REASONING, response.reasoning);
        }
        dialog.setArguments(args);

        dialog.callback = callback;
        dialog.show(fragmentManager, "stormy_approval");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        // Inflate custom layout
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_stormy_approval, null);

        // Reconstruct response from arguments
        Bundle args = getArguments();
        if (args != null) {
            String tool = args.getString(ARG_TOOL);
            String argsJson = args.getString(ARG_ARGS);
            String reasoning = args.getString(ARG_REASONING);

            response = new StormyResponseParser.StormyParsedResponse();
            response.action = "tool_use";
            response.tool = tool;
            response.toolArgs = com.google.gson.JsonParser.parseString(argsJson).getAsJsonObject();
            response.reasoning = reasoning;
            response.isValid = true;
        }

        // Setup UI
        setupUI(view);

        builder.setView(view);

        // Handle buttons
        view.findViewById(R.id.btn_approve).setOnClickListener(v -> {
            if (callback != null) {
                callback.onApproved();
            }
            dismiss();
        });

        view.findViewById(R.id.btn_reject).setOnClickListener(v -> {
            if (callback != null) {
                callback.onRejected("User rejected the operation");
            }
            dismiss();
        });

        return builder.create();
    }

    /**
     * Setup the UI with response data
     */
    private void setupUI(View view) {
        if (response == null || !response.isValid) {
            return;
        }

        // Set tool description
        TextView toolDescription = view.findViewById(R.id.tool_description);
        toolDescription.setText(StormyResponseParser.getToolDescription(response));

        // Set reasoning (if available)
        LinearLayout reasoningContainer = view.findViewById(R.id.reasoning_container);
        TextView toolReasoning = view.findViewById(R.id.tool_reasoning);

        if (response.reasoning != null && !response.reasoning.isEmpty()) {
            reasoningContainer.setVisibility(View.VISIBLE);
            toolReasoning.setText(response.reasoning);
        } else {
            reasoningContainer.setVisibility(View.GONE);
        }

        // Setup preview based on tool type
        FrameLayout previewContent = view.findViewById(R.id.preview_content);
        previewContent.removeAllViews();

        switch (response.tool) {
            case "write_to_file":
                setupWriteToFilePreview(previewContent);
                break;

            case "replace_in_file":
                setupReplaceInFilePreview(previewContent);
                break;

            case "delete_file":
                setupDeleteFilePreview(previewContent);
                break;

            case "rename_file":
            case "move_file":
                setupRenameFilePreview(previewContent);
                break;

            case "copy_file":
                setupCopyFilePreview(previewContent);
                break;

            default:
                setupGenericPreview(previewContent);
                break;
        }
    }

    /**
     * Setup preview for write_to_file
     */
    private void setupWriteToFilePreview(FrameLayout container) {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 12);

        // File path
        TextView pathLabel = new TextView(context);
        pathLabel.setText("File: " + response.toolArgs.get("path").getAsString());
        pathLabel.setTextSize(13);
        pathLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        pathLabel.setPadding(0, 0, 0, 8);
        layout.addView(pathLabel);

        // Content preview (first 500 chars)
        String content = response.toolArgs.get("content").getAsString();
        String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content;

        TextView contentView = new TextView(context);
        contentView.setText(preview);
        contentView.setTextSize(11);
        contentView.setTypeface(android.graphics.Typeface.MONOSPACE);
        contentView.setBackgroundColor(0xFFF5F5F5);
        contentView.setPadding(8, 8, 8, 8);
        layout.addView(contentView);

        // Statistics
        int lines = content.split("\n").length;
        int chars = content.length();

        TextView stats = new TextView(context);
        stats.setText(lines + " lines, " + chars + " characters");
        stats.setTextSize(11);
        stats.setTextColor(0xFF666666);
        stats.setPadding(0, 8, 0, 0);
        layout.addView(stats);

        container.addView(layout);
    }

    /**
     * Setup preview for replace_in_file
     */
    private void setupReplaceInFilePreview(FrameLayout container) {
        // Inflate diff view
        View diffView = LayoutInflater.from(requireContext()).inflate(
            R.layout.view_diff_display, container, false);

        // Parse diff
        String diff = response.toolArgs.get("diff").getAsString();
        String[] parts = parseDiffBlock(diff);

        if (parts != null) {
            TextView filePath = diffView.findViewById(R.id.diff_file_path);
            TextView searchContent = diffView.findViewById(R.id.diff_search_content);
            TextView replaceContent = diffView.findViewById(R.id.diff_replace_content);
            TextView stats = diffView.findViewById(R.id.diff_stats);

            filePath.setText("Modified: " + response.toolArgs.get("path").getAsString());
            searchContent.setText(parts[0]);
            replaceContent.setText(parts[1]);

            // Calculate statistics
            int searchLines = parts[0].split("\n").length;
            int replaceLines = parts[1].split("\n").length;
            int added = Math.max(0, replaceLines - searchLines);
            int removed = Math.max(0, searchLines - replaceLines);

            stats.setText("Lines: +" + added + " -" + removed);

            // Hide "View Full" button in dialog
            diffView.findViewById(R.id.btn_view_full_diff).setVisibility(View.GONE);
        }

        container.addView(diffView);
    }

    /**
     * Setup preview for delete_file
     */
    private void setupDeleteFilePreview(FrameLayout container) {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 12);

        TextView warning = new TextView(context);
        warning.setText("⚠️ This file will be permanently deleted:\n\n" +
                       response.toolArgs.get("path").getAsString());
        warning.setTextSize(13);
        warning.setTextColor(0xFFD32F2F);
        layout.addView(warning);

        container.addView(layout);
    }

    /**
     * Setup preview for rename_file
     */
    private void setupRenameFilePreview(FrameLayout container) {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 12);

        String oldPath = response.toolArgs.get("old_path").getAsString();
        String newPath = response.toolArgs.get("new_path").getAsString();

        TextView fromLabel = new TextView(context);
        fromLabel.setText("From:");
        fromLabel.setTextSize(11);
        fromLabel.setTextColor(0xFF666666);
        layout.addView(fromLabel);

        TextView fromPath = new TextView(context);
        fromPath.setText(oldPath);
        fromPath.setTextSize(13);
        fromPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        fromPath.setPadding(0, 0, 0, 12);
        layout.addView(fromPath);

        TextView toLabel = new TextView(context);
        toLabel.setText("To:");
        toLabel.setTextSize(11);
        toLabel.setTextColor(0xFF666666);
        layout.addView(toLabel);

        TextView toPath = new TextView(context);
        toPath.setText(newPath);
        toPath.setTextSize(13);
        toPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        toPath.setTextColor(0xFF388E3C);
        layout.addView(toPath);

        container.addView(layout);
    }

    /**
     * Setup preview for copy_file
     */
    private void setupCopyFilePreview(FrameLayout container) {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 12);

        String source = response.toolArgs.get("source_path").getAsString();
        String dest = response.toolArgs.get("destination_path").getAsString();

        TextView sourceLabel = new TextView(context);
        sourceLabel.setText("Source:");
        sourceLabel.setTextSize(11);
        sourceLabel.setTextColor(0xFF666666);
        layout.addView(sourceLabel);

        TextView sourcePath = new TextView(context);
        sourcePath.setText(source);
        sourcePath.setTextSize(13);
        sourcePath.setTypeface(android.graphics.Typeface.MONOSPACE);
        sourcePath.setPadding(0, 0, 0, 12);
        layout.addView(sourcePath);

        TextView destLabel = new TextView(context);
        destLabel.setText("Will be copied to:");
        destLabel.setTextSize(11);
        destLabel.setTextColor(0xFF666666);
        layout.addView(destLabel);

        TextView destPath = new TextView(context);
        destPath.setText(dest);
        destPath.setTextSize(13);
        destPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        destPath.setTextColor(0xFF388E3C);
        layout.addView(destPath);

        container.addView(layout);
    }

    /**
     * Setup generic preview for other tools
     */
    private void setupGenericPreview(FrameLayout container) {
        Context context = requireContext();
        TextView textView = new TextView(context);
        textView.setText(response.toolArgs.toString());
        textView.setTextSize(11);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setPadding(12, 12, 12, 12);
        container.addView(textView);
    }

    /**
     * Parse SEARCH/REPLACE diff block
     */
    private String[] parseDiffBlock(String diff) {
        try {
            Pattern pattern = Pattern.compile(
                "<<<<<<< SEARCH\\s*\\n(.*?)\\n=======\\s*\\n(.*?)\\n>>>>>>> REPLACE",
                Pattern.DOTALL
            );

            Matcher matcher = pattern.matcher(diff);
            if (matcher.find()) {
                String search = matcher.group(1);
                String replace = matcher.group(2);
                return new String[]{search, replace};
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        if (callback != null) {
            callback.onRejected("User cancelled the dialog");
        }
    }
}
