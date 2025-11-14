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
import java.util.Map;

public class AIChatFragment extends Fragment implements ChatMessageAdapter.OnAiActionInteractionListener, AIAssistant.AIActionListener {

    private List<ChatMessage> chatHistory;
    private QwenConversationState qwenConversationState;
    private ChatMessageAdapter chatMessageAdapter;
    private AIChatUIManager uiManager;
    private AIChatHistoryManager historyManager;
    private AIChatFragmentListener listener;
    private AIAssistant aiAssistant;
    private boolean isAiProcessing = false;
    private String projectPath;

    public interface AIChatFragmentListener {
        AIAssistant getAIAssistant();
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
        aiAssistant = listener.getAIAssistant();
        chatMessageAdapter = new ChatMessageAdapter(requireContext(), chatHistory, aiAssistant.isAgentModeEnabled());
        chatMessageAdapter.setOnAiActionInteractionListener(this);
        uiManager.setupRecyclerView(chatMessageAdapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        historyManager.loadChatState(chatHistory, qwenConversationState);
        if (aiAssistant != null) {
            aiAssistant.setActionListener(this);
        }
        uiManager.updateUiVisibility(chatHistory.isEmpty());
        uiManager.setListeners();
        uiManager.scrollToBottom();
    }

    public void sendPrompt() {
        if (aiAssistant == null || aiAssistant.getCurrentModel() == null) {
            Toast.makeText(requireContext(), "Please select an AI model first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String prompt = uiManager.getText().trim();
        if (prompt.isEmpty() || isAiProcessing) {
            return;
        }

        uiManager.setSendButtonEnabled(false);
        isAiProcessing = true;
        ChatMessage userMsg = new ChatMessage("user", prompt);
        addMessage(userMsg);
        uiManager.setText("");
        aiAssistant.sendMessageStreaming(prompt, new ArrayList<>(chatHistory), qwenConversationState, null, null, null);
    }

    public void addMessage(ChatMessage message) {
        chatHistory.add(message);
        chatMessageAdapter.notifyItemInserted(chatHistory.size() - 1);
        uiManager.scrollToBottom();
        historyManager.saveChatState(chatHistory, qwenConversationState);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onApprovalResponse(boolean approved, String toolName, Map<String, Object> args) {
        if (approved) {
            String result = aiAssistant.getToolExecutor().execute(toolName, args);
            ChatMessage toolResultMessage = new ChatMessage("tool", result);
            addMessage(toolResultMessage);
            aiAssistant.sendMessageStreaming("", new ArrayList<>(chatHistory), qwenConversationState, null, null, null);
        } else {
            ChatMessage approvalMessage = new ChatMessage("assistant", "User denied tool execution.");
            addMessage(approvalMessage);
        }
    }

    @Override
    public void onAiError(String errorMessage) {
        isAiProcessing = false;
        uiManager.setSendButtonEnabled(true);
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAiRequestStarted() {
    }

    @Override
    public void onAiStreamUpdate(String partialResponse, boolean isThinking) {
        // In the new workflow, we'll just display the final response.
    }

    @Override
    public void onAiRequestCompleted() {
        isAiProcessing = false;
        uiManager.setSendButtonEnabled(true);
    }

    @Override
    public void onQwenConversationStateUpdated(QwenConversationState state) {
        if (state != null) {
            this.qwenConversationState = state;
            historyManager.saveChatState(chatHistory, qwenConversationState);
        }
    }

    @Override
    public void onToolCall(String toolName, Map<String, Object> args) {
        // This is now handled by the adapter.
    }

    @Override
    public void onApprovalRequired(String toolName, Map<String, Object> args) {
        // This is now handled by the adapter.
    }

    @Override
    public void onFollowupQuestion(String question) {
        ChatMessage followupMessage = new ChatMessage("assistant", question);
        addMessage(followupMessage);
    }

    @Override
    public void onCompletion(String summary) {
        ChatMessage completionMessage = new ChatMessage("assistant", summary);
        addMessage(completionMessage);
    }
}
