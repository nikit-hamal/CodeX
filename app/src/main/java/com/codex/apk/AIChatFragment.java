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
import java.util.ArrayList;
import java.util.List;

public class AIChatFragment extends Fragment {

    private List<ChatMessage> chatHistory;
    private QwenConversationState qwenConversationState;
    private ChatMessageAdapter chatMessageAdapter;

    private AIChatUIManager uiManager;
    private AIChatHistoryManager historyManager;

    private AIChatFragmentListener listener;
    private AIAssistant aiAssistant;
    private ChatMessage currentAiStatusMessage = null;
    public boolean isAiProcessing = false;
    private String projectPath;
    private final List<java.io.File> pendingAttachments = new java.util.ArrayList<>();
    private androidx.activity.result.ActivityResultLauncher<String[]> pickFilesLauncher;

    public interface AIChatFragmentListener {
        AIAssistant getAIAssistant();
        void sendAiPrompt(String userPrompt, List<ChatMessage> chatHistory, QwenConversationState qwenState);
        void onAiFileChangeClicked(ChatMessage.FileActionDetail fileActionDetail);
        void onQwenConversationStateUpdated(QwenConversationState state);
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
        uiManager.setupRecyclerView(chatMessageAdapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        historyManager.loadChatState(chatHistory, qwenConversationState);
        aiAssistant = listener.getAIAssistant();
        uiManager.updateUiVisibility(chatHistory.isEmpty());
        uiManager.setListeners();
        uiManager.scrollToBottom();
    }

    public void sendPrompt() {
        if (aiAssistant == null) {
            Toast.makeText(requireContext(), "AI assistant not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String prompt = uiManager.getText().trim();
        if (prompt.isEmpty() || isAiProcessing) {
            return;
        }

        uiManager.setSendButtonEnabled(false);
        ChatMessage userMsg = new ChatMessage(ChatMessage.SENDER_USER, prompt, System.currentTimeMillis());
        addMessage(userMsg);
        uiManager.setText("");

        if (listener != null) {
            listener.sendAiPrompt(prompt, new ArrayList<>(chatHistory), qwenConversationState);
        }
    }

    public int addMessage(ChatMessage message) {
        int indexChangedOrAdded = -1;

        if (message.getSender() == ChatMessage.SENDER_AI) {
            if (isAiProcessing && currentAiStatusMessage != null) {
                int index = chatHistory.indexOf(currentAiStatusMessage);
                if (index != -1) {
                    chatHistory.set(index, message);
                    currentAiStatusMessage = message;
                    chatMessageAdapter.notifyItemChanged(index);
                    indexChangedOrAdded = index;
                }
            } else {
                chatHistory.add(message);
                indexChangedOrAdded = chatHistory.size() - 1;
                chatMessageAdapter.notifyItemInserted(indexChangedOrAdded);
            }
            isAiProcessing = false;
            currentAiStatusMessage = null;
            uiManager.setSendButtonEnabled(true);
        } else {
            chatHistory.add(message);
            indexChangedOrAdded = chatHistory.size() - 1;
            chatMessageAdapter.notifyItemInserted(indexChangedOrAdded);
        }
        uiManager.scrollToBottom();
        historyManager.saveChatState(chatHistory, qwenConversationState);
        return indexChangedOrAdded;
    }

    public void showThinking() {
        if (isAiProcessing) return;
        currentAiStatusMessage = new ChatMessage(ChatMessage.SENDER_AI, "Thinking...", System.currentTimeMillis());
        isAiProcessing = true;
        chatHistory.add(currentAiStatusMessage);
        chatMessageAdapter.notifyItemInserted(chatHistory.size() - 1);
        uiManager.scrollToBottom();
    }
}
