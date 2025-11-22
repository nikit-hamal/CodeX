package com.codex.apk;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.codex.apk.tools.Tool;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIChatFragment extends Fragment implements ChatMessageAdapter.OnAiActionInteractionListener, AIAssistant.AIActionListener {

    private List<ChatMessage> chatHistory;
    private QwenConversationState qwenConversationState;
    private ChatMessageAdapter chatMessageAdapter;

    private AIChatUIManager uiManager;
    private AIChatHistoryManager historyManager;

    private AIChatFragmentListener listener;
    private AIAssistant aiAssistant;
    public boolean isAiProcessing = false;
    private String projectPath;
    private final List<java.io.File> pendingAttachments = new java.util.ArrayList<>();
    private androidx.activity.result.ActivityResultLauncher<String[]> pickFilesLauncher;

    public interface AIChatFragmentListener {
        AIAssistant getAIAssistant();
        void onAiFileChangeClicked(ChatMessage.FileActionDetail fileActionDetail);
    }

    // Hook used by UI manager to trigger attachment selection
    public void onAttachButtonClicked() {
        if (pickFilesLauncher != null) {
            pickFilesLauncher.launch(new String[]{"image/*", "application/pdf", "text/*", "application/octet-stream", "application/zip"});
        }
    }

    // Called by UI to remove an attachment from the pending list
    public void removePendingAttachmentAt(int index) {
        if (index >= 0 && index < pendingAttachments.size()) {
            pendingAttachments.remove(index);
            if (uiManager != null) uiManager.showAttachedFilesPreview(pendingAttachments);
        }
    }

    public static AIChatFragment newInstance(String projectPath) {
        AIChatFragment fragment = new AIChatFragment();
        Bundle args = new Bundle();
        args.putString("project_path", projectPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AIChatFragmentListener) {
            listener = (AIChatFragmentListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement AIChatFragmentListener");
        }
        // Prepare file picker
        pickFilesLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            android.content.ContentResolver cr = requireContext().getContentResolver();
            for (android.net.Uri uri : uris) {
                try (java.io.InputStream in = cr.openInputStream(uri)) {
                    if (in == null) continue;
                    String name = "attachment"; // Simplified for brevity
                    java.io.File out = new java.io.File(requireContext().getCacheDir(), System.currentTimeMillis() + "_" + name);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                    pendingAttachments.add(out);
                } catch (Exception ignore) {}
            }
            if (uiManager != null) uiManager.showAttachedFilesPreview(pendingAttachments);
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectPath = getArguments() != null ? getArguments().getString("project_path") : "default_project";
        chatHistory = new ArrayList<>();
        qwenConversationState = new QwenConversationState();
        historyManager = new AIChatHistoryManager(requireContext(), projectPath);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_ai_chat_tab, container, false);
        uiManager = new AIChatUIManager(this, view);

        chatMessageAdapter = new ChatMessageAdapter(requireContext(), chatHistory);
        chatMessageAdapter.setOnAiActionInteractionListener(this);
        uiManager.setupRecyclerView(chatMessageAdapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        historyManager.loadChatState(chatHistory, qwenConversationState);
        aiAssistant = listener.getAIAssistant();
        if(aiAssistant != null) {
            aiAssistant.setActionListener(this);
        }
        uiManager.updateUiVisibility(chatHistory.isEmpty());
        uiManager.setListeners();
        uiManager.scrollToBottom();
    }

    public void sendPrompt() {
        if (aiAssistant == null) {
            Toast.makeText(requireContext(), "AI Assistant not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String prompt = uiManager.getText().trim();
        if (prompt.isEmpty() || isAiProcessing) {
            return;
        }

        uiManager.setSendButtonEnabled(false);
        isAiProcessing = true;

        aiAssistant.startAgenticLoop(prompt, new ArrayList<>(chatHistory), qwenConversationState);
        uiManager.setText("");
    }

    @Override
    public void onHistoryUpdate(List<ChatMessage> updatedHistory) {
        requireActivity().runOnUiThread(() -> {
            chatHistory.clear();
            chatHistory.addAll(updatedHistory);
            chatMessageAdapter.notifyDataSetChanged();
            uiManager.scrollToBottom();
            historyManager.saveChatState(chatHistory, qwenConversationState);
        });
    }

    @Override
    public boolean requestUserApproval(String toolName, String args) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        requireActivity().runOnUiThread(() -> {
            DialogHelper.showConfirmationDialog(
                requireContext(),
                "Approve Action",
                "Do you approve the following action?\n\nTool: " + toolName + "\nArgs: " + args,
                "Approve",
                "Deny",
                (dialog, which) -> future.complete(true),
                (dialog, which) -> future.complete(false)
            );
        });
        try {
            return future.get();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onToolCall(String toolName, String args) {
        requireActivity().runOnUiThread(() -> {
            addMessage(new ChatMessage("Tool Call: " + toolName + "\nArgs: " + args, ChatMessage.SENDER_AI_TOOL_CALL));
        });
    }

    @Override
    public void onToolResult(String toolName, String result) {
        requireActivity().runOnUiThread(() -> {
            addMessage(new ChatMessage("Tool Result: " + toolName + "\nResult: " + result, ChatMessage.SENDER_AI_TOOL_RESULT));
        });
    }

    @Override
    public void onToolSkipped(String toolName) {
        requireActivity().runOnUiThread(() -> {
            addMessage(new ChatMessage("Tool Skipped: " + toolName, ChatMessage.SENDER_AI));
        });
    }


    @Override
    public void onAiError(String errorMessage) {
        requireActivity().runOnUiThread(() -> {
            addMessage(new ChatMessage("Error: " + errorMessage, ChatMessage.SENDER_AI));
            isAiProcessing = false;
            uiManager.setSendButtonEnabled(true);
        });
    }

    @Override
    public void onAiRequestStarted() {
        // Not used in the new agentic loop
    }

    @Override
    public void onAiRequestCompleted() {
        requireActivity().runOnUiThread(() -> {
            isAiProcessing = false;
            uiManager.setSendButtonEnabled(true);
        });
    }

    public int addMessage(ChatMessage message) {
        chatHistory.add(message);
        int position = chatHistory.size() - 1;
        chatMessageAdapter.notifyItemInserted(position);
        uiManager.scrollToBottom();
        historyManager.saveChatState(chatHistory, qwenConversationState);
        return position;
    }

    // Unused methods from the old implementation
    @Override public void onAcceptClicked(int pos, ChatMessage msg) {}
    @Override public void onDiscardClicked(int pos, ChatMessage msg) {}
    @Override public void onReapplyClicked(int pos, ChatMessage msg) {}
    @Override public void onFileChangeClicked(ChatMessage.FileActionDetail detail) {
        if (listener != null) {
            listener.onAiFileChangeClicked(detail);
        }
    }
    @Override public void onPlanAcceptClicked(int pos, ChatMessage msg) {}
    @Override public void onPlanDiscardClicked(int pos, ChatMessage msg) {}

    // Methods from StreamingApiClient.StreamListener
    @Override public void onStreamStarted(String requestId) {}
    @Override public void onStreamPartialUpdate(String requestId, String partialResponse, boolean isThinking) {}
    @Override public void onStreamCompleted(String requestId, QwenResponseParser.ParsedResponse response) {}
    @Override public void onStreamError(String requestId, String errorMessage, Throwable throwable) {}
}
