package com.codex.apk.editor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.codex.apk.AIChatFragment;
import com.codex.apk.ChatMessage;
import com.codex.apk.EditorActivity;
import com.codex.apk.TabItem;

import com.codex.apk.ai.PromptBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Manages the execution of an AI-generated plan.
 * This class encapsulates the state machine and logic for stepping through a plan,
 * sending prompts for each step, and handling the results.
 */
public class PlanExecutor {
    private static final String TAG = "PlanExecutor";

    private final EditorActivity activity;
    private final AiAssistantManager aiAssistantManager;

    private Integer lastPlanMessagePosition = null;
    private int planProgressIndex = 0;
    private int planStepRetryCount = 0;
    private boolean isExecutingPlan = false;
    private boolean waitingForUserApproval = false;
    private final Deque<String> executedStepSummaries = new ArrayDeque<>();

    public PlanExecutor(EditorActivity activity, AiAssistantManager aiAssistantManager) {
        this.activity = activity;
        this.aiAssistantManager = aiAssistantManager;
    }

    public boolean isExecutingPlan() {
        return isExecutingPlan;
    }
    public boolean isAwaitingUserApproval(int messagePosition) {
        return waitingForUserApproval && lastPlanMessagePosition != null && lastPlanMessagePosition == messagePosition;
    }
    public boolean isPlanMessage(int messagePosition) {
        return lastPlanMessagePosition != null && lastPlanMessagePosition == messagePosition;
    }

    public void cancelPlan(String message) {
        if (!isExecutingPlan) return;
        waitingForUserApproval = false;
        finalizePlanExecution(message != null ? message : "Plan cancelled", true);
    }

    public void startPlan(int messagePosition, ChatMessage message) {
        Log.d(TAG, "Starting plan auto-run for message at position: " + messagePosition);
        if (isExecutingPlan) {
            finalizePlanExecution("Superseding previous plan", true);
        }

        isExecutingPlan = true;
        waitingForUserApproval = false;
        planStepRetryCount = 0;
        message.setStatus(ChatMessage.STATUS_ACCEPTED);
        lastPlanMessagePosition = messagePosition;
        planProgressIndex = 0;
        executedStepSummaries.clear();

        AIChatFragment aiChatFragment = activity.getAiChatFragment();
        if (aiChatFragment != null) {
            aiChatFragment.updateMessage(messagePosition, message);
            aiChatFragment.hideThinkingMessage();
        }

        aiAssistantManager.setCurrentStreamingMessagePosition(null);
        sendNextPlanStepFollowUp();
    }

    public void onStepExecutionResult(List<ChatMessage.FileActionDetail> fileActions, String rawResponse, String explanation) {
        waitingForUserApproval = false;
        if (fileActions != null && !fileActions.isEmpty()) {
            planStepRetryCount = 0;
            AIChatFragment frag = activity.getAiChatFragment();
            ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
            if (planMsg != null) {
                try {
                    List<ChatMessage.PlanStep> steps = planMsg.getPlanSteps();
                    if (steps != null && planProgressIndex < steps.size()) {
                        ChatMessage.PlanStep running = steps.get(planProgressIndex);
                        if (running != null) {
                            running.rawResponse = (rawResponse != null && !rawResponse.isEmpty()) ? rawResponse : (explanation != null ? explanation : "");
                        }
                    }
                } catch (Exception ignored) {}

                boolean agentMode = aiAssistantManager.getAIAssistant() != null
                        && aiAssistantManager.getAIAssistant().isAgentModeEnabled();

                List<ChatMessage.FileActionDetail> merged = planMsg.getProposedFileChanges() != null ? planMsg.getProposedFileChanges() : new ArrayList<>();
                merged.addAll(fileActions);
                planMsg.setProposedFileChanges(merged);
                if (!agentMode) {
                    markCurrentPlanStepStatus(planMsg, "awaiting_approval");
                }
                frag.updateMessage(lastPlanMessagePosition, planMsg);

                if (agentMode) {
                    waitingForUserApproval = false;
                    aiAssistantManager.onAiAcceptActions(lastPlanMessagePosition, planMsg);
                } else {
                    waitingForUserApproval = true;
                }
                return;
            }
            finalizePlanExecution("Plan context unavailable", true);
            return;
        } else {
            try {
                AIChatFragment frag = activity.getAiChatFragment();
                if (frag != null) {
                    ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
                    if (planMsg != null) {
                        List<ChatMessage.PlanStep> steps = planMsg.getPlanSteps();
                        if (steps != null && planProgressIndex < steps.size()) {
                            ChatMessage.PlanStep running = steps.get(planProgressIndex);
                            if (running != null && (running.rawResponse == null || running.rawResponse.isEmpty())) {
                                running.rawResponse = (rawResponse != null && !rawResponse.isEmpty()) ? rawResponse : (explanation != null ? explanation : "");
                                frag.updateMessage(lastPlanMessagePosition, planMsg);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            planStepRetryCount++;
            if (planStepRetryCount > 4) {
                Log.e(TAG, "AI did not produce file ops after 5 prompts; marking step failed and continuing.");
                setCurrentRunningPlanStepStatus("failed");
                planStepRetryCount = 0;
                // We must update the UI to show the failed state
                AIChatFragment frag = activity.getAiChatFragment();
                if (lastPlanMessagePosition != null && frag != null) {
                    ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
                    if (planMsg != null) frag.updateMessage(lastPlanMessagePosition, planMsg);
                }
                // Don't halt, just move to the next step
                sendNextPlanStepFollowUp();
            } else {
                Log.w(TAG, "AI did not return file operations. Retrying step (attempt " + planStepRetryCount + ")");
                sendNextPlanStepFollowUp();
            }
        }
    }

    public void onStepActionsApplied() {
        waitingForUserApproval = false;
        setCurrentRunningPlanStepStatus("completed");
        AIChatFragment frag = activity.getAiChatFragment();
        if (lastPlanMessagePosition != null && frag != null) {
            ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
            if (planMsg != null) frag.updateMessage(lastPlanMessagePosition, planMsg);
        }
        sendNextPlanStepFollowUp();
    }

    private void setNextPlanStepStatus(String status) {
        if (lastPlanMessagePosition == null) return;
        AIChatFragment frag = activity.getAiChatFragment();
        if (frag == null) return;
        ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
        if (planMsg == null || planMsg.getPlanSteps() == null || planMsg.getPlanSteps().isEmpty()) return;

        List<ChatMessage.PlanStep> steps = planMsg.getPlanSteps();
        int tempIndex = planProgressIndex;
        while (tempIndex < steps.size()) {
            ChatMessage.PlanStep ps = steps.get(tempIndex);
            if (ps != null && (isActionableStepKind(ps.kind)) &&
                    !"completed".equals(ps.status) && !"failed".equals(ps.status)) {
                ps.status = status;
                break;
            }
            tempIndex++;
        }
    }

    private void markCurrentPlanStepStatus(ChatMessage planMsg, String status) {
        if (planMsg == null || planMsg.getPlanSteps() == null || planMsg.getPlanSteps().isEmpty()) return;
        if (planProgressIndex < planMsg.getPlanSteps().size()) {
            ChatMessage.PlanStep ps = planMsg.getPlanSteps().get(planProgressIndex);
            if (ps != null) ps.status = status;
        }
    }

    public void addExecutedStepSummary(String summary) {
        executedStepSummaries.add(summary);
    }

    public void setCurrentRunningPlanStepStatus(String status) {
        if (lastPlanMessagePosition == null) return;
        AIChatFragment frag = activity.getAiChatFragment();
        if (frag == null) return;
        ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
        if (planMsg == null || planMsg.getPlanSteps() == null || planMsg.getPlanSteps().isEmpty()) return;
        List<ChatMessage.PlanStep> steps = planMsg.getPlanSteps();
        if (planProgressIndex < steps.size()) {
            ChatMessage.PlanStep ps = steps.get(planProgressIndex);
            if (ps != null) {
                ps.status = status;
                planProgressIndex++;
            }
        }
    }

    public void onStepActionsRejected(String reason) {
        waitingForUserApproval = false;
        setCurrentRunningPlanStepStatus("failed");
        finalizePlanExecution(reason != null ? reason : "Plan step discarded", true);
    }

    public void finalizePlanExecution(String toastMessage, boolean sanitizeDanglingRunning) {
        AIChatFragment frag = activity.getAiChatFragment();
        if (sanitizeDanglingRunning && frag != null && lastPlanMessagePosition != null) {
            ChatMessage pm = frag.getMessageAt(lastPlanMessagePosition);
            if (pm != null && pm.getPlanSteps() != null) {
                boolean changed = false;
                for (ChatMessage.PlanStep ps : pm.getPlanSteps()) {
                    if (ps != null && "running".equals(ps.status)) { ps.status = "completed"; changed = true; }
                }
                if (changed) { frag.updateMessage(lastPlanMessagePosition, pm); }
            }
        }
        isExecutingPlan = false;
        waitingForUserApproval = false;
        if (frag != null) { frag.hideThinkingMessage(); }
        aiAssistantManager.setCurrentStreamingMessagePosition(null);
        lastPlanMessagePosition = null;
        planProgressIndex = 0;
        planStepRetryCount = 0;
        executedStepSummaries.clear();
        if (toastMessage != null) activity.showToast(toastMessage);
        Log.i(TAG, "Plan execution finalized. sanitizeDanglingRunning=" + sanitizeDanglingRunning);
    }

    private void sendNextPlanStepFollowUp() {
        if (waitingForUserApproval) {
            Log.d(TAG, "Paused for user approval. Waiting before sending next step.");
            return;
        }
        AIChatFragment frag = activity.getAiChatFragment();
        if (frag == null || lastPlanMessagePosition == null) {
            finalizePlanExecution("Plan completed", true);
            return;
        }
        ChatMessage planMsg = frag.getMessageAt(lastPlanMessagePosition);
        if (planMsg == null || planMsg.getPlanSteps() == null || planMsg.getPlanSteps().isEmpty()) {
            finalizePlanExecution("Plan completed", true);
            return;
        }
        List<ChatMessage.PlanStep> steps = planMsg.getPlanSteps();

        int idx = planProgressIndex;
        while (idx < steps.size()) {
            ChatMessage.PlanStep s = steps.get(idx);
            if (s != null && (isActionableStepKind(s.kind))
                    && !"completed".equals(s.status) && !"failed".equals(s.status)) {
                break;
            }
            idx++;
        }

        if (idx >= steps.size()) {
            finalizePlanExecution("Plan completed", false);
            return;
        }

        ChatMessage.PlanStep target = steps.get(idx);
        String prompt = PromptBuilder.buildPromptForStep(target, planMsg, idx, executedStepSummaries, activity.getProjectDirectory(), activity.getActiveTab());

        isExecutingPlan = true;
        setNextPlanStepStatus("running");
        activity.runOnUiThread(() -> {
            if (lastPlanMessagePosition != null) {
                ChatMessage pm = activity.getAiChatFragment().getMessageAt(lastPlanMessagePosition);
                if (pm != null) activity.getAiChatFragment().updateMessage(lastPlanMessagePosition, pm);
            }
        });

        aiAssistantManager.sendAiPrompt(prompt.toString(), new ArrayList<>(), activity.getQwenState(), activity.getActiveTab());
    }

    private boolean isActionableStepKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) return true;
        String k = kind.trim().toLowerCase(java.util.Locale.ROOT);
        return k.equals("file") || k.equals("code") || k.equals("edit") || k.equals("modify") || k.equals("update") || k.equals("patch") || k.equals("change") || k.equals("smartupdate") || k.equals("refactor");
    }
}
