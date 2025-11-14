package com.codex.apk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade iterative workflow orchestrator for Stormy.
 *
 * Manages the autonomous, iterative execution of tasks:
 * 1. Receives user request
 * 2. Sends to AI (Stormy)
 * 3. Parses response (tool_use, message, question, completion)
 * 4. Executes tools with approval flow (if needed)
 * 5. Sends results back to AI
 * 6. Repeats until task completion
 *
 * Supports two modes:
 * - Agent Mode ON: Full autonomy, no approval required
 * - Agent Mode OFF: Approval required for file modifications
 */
public class StormyWorkflowOrchestrator {
    private static final String TAG = "StormyWorkflow";
    private static final int MAX_ITERATIONS = 50; // Prevent infinite loops

    private final Context context;
    private final AIAssistant aiAssistant;
    private final File projectDir;
    private final WorkflowCallback callback;
    private final Handler mainHandler;

    private boolean isRunning = false;
    private int iterationCount = 0;
    private List<ExecutionStep> executionHistory = new ArrayList<>();
    private List<ChatMessage> conversationHistory = new ArrayList<>();
    private QwenConversationState conversationState = null;

    /**
     * Represents a single step in the workflow execution
     */
    public static class ExecutionStep {
        public final StormyResponseParser.StormyParsedResponse response;
        public JsonObject toolResult; // null if not a tool_use (non-final to allow update after execution)
        public final long timestamp;

        public ExecutionStep(StormyResponseParser.StormyParsedResponse response, JsonObject toolResult) {
            this.response = response;
            this.toolResult = toolResult;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Callback interface for workflow events
     */
    public interface WorkflowCallback {
        /**
         * Called when a tool is about to be executed
         */
        void onToolStarted(StormyResponseParser.StormyParsedResponse response);

        /**
         * Called when a tool has been executed successfully
         */
        void onToolCompleted(StormyResponseParser.StormyParsedResponse response, JsonObject result);

        /**
         * Called when a tool execution fails
         */
        void onToolFailed(StormyResponseParser.StormyParsedResponse response, JsonObject result);

        /**
         * Called when Stormy asks a question (workflow pauses)
         */
        void onQuestionAsked(String question, String reasoning);

        /**
         * Called when Stormy signals task completion
         */
        void onTaskCompleted(String summary);

        /**
         * Called when Stormy sends a text message (no tool use)
         */
        void onMessage(String message);

        /**
         * Called when approval is needed (Agent Mode OFF only)
         */
        void onApprovalNeeded(StormyResponseParser.StormyParsedResponse response, ApprovalCallback approvalCallback);

        /**
         * Called when an error occurs
         */
        void onError(String error);

        /**
         * Called when workflow starts
         */
        void onWorkflowStarted();

        /**
         * Called when workflow ends (completion or error)
         */
        void onWorkflowEnded();
    }

    /**
     * Callback for approval decisions
     */
    public interface ApprovalCallback {
        void onApproved();
        void onRejected(String reason);
    }

    /**
     * Constructor
     */
    public StormyWorkflowOrchestrator(Context context, AIAssistant aiAssistant,
                                     File projectDir, WorkflowCallback callback) {
        this.context = context;
        this.aiAssistant = aiAssistant;
        this.projectDir = projectDir;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Start the iterative workflow with a user request
     */
    public void startWorkflow(String userRequest) {
        if (isRunning) {
            callback.onError("Workflow already running");
            return;
        }

        Log.d(TAG, "Starting workflow with request: " + userRequest);

        isRunning = true;
        iterationCount = 0;
        executionHistory.clear();
        conversationHistory.clear();
        conversationState = new QwenConversationState();

        callback.onWorkflowStarted();

        // Add user message to history
        ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, userRequest, null);
        conversationHistory.add(userMessage);

        // Send initial request to AI
        sendToAI(userRequest, new AIResponseHandler() {
            @Override
            public void onResponse(String responseText) {
                // Add AI response to history
                ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, responseText, null);
                conversationHistory.add(aiMessage);

                processAIResponse(responseText);
            }

            @Override
            public void onError(String error) {
                handleWorkflowError("AI request failed: " + error);
            }
        });
    }

    /**
     * Resume workflow after user answers a question
     */
    public void resumeWithAnswer(String answer) {
        if (isRunning) {
            Log.w(TAG, "Workflow already running, ignoring resume request");
            return;
        }

        Log.d(TAG, "Resuming workflow with answer: " + answer);

        isRunning = true;

        // Add user answer to history
        ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, answer, null);
        conversationHistory.add(userMessage);

        // Send user answer to AI
        sendToAI(answer, new AIResponseHandler() {
            @Override
            public void onResponse(String responseText) {
                // Add AI response to history
                ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, responseText, null);
                conversationHistory.add(aiMessage);

                processAIResponse(responseText);
            }

            @Override
            public void onError(String error) {
                handleWorkflowError("AI request failed: " + error);
            }
        });
    }

    /**
     * Cancel the running workflow
     */
    public void cancelWorkflow() {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "Cancelling workflow");
        isRunning = false;
        callback.onWorkflowEnded();
    }

    /**
     * Process AI response and execute tools iteratively
     */
    private void processAIResponse(String responseText) {
        if (!isRunning) {
            Log.w(TAG, "Workflow stopped, ignoring response");
            return;
        }

        // Check iteration limit
        iterationCount++;
        if (iterationCount > MAX_ITERATIONS) {
            handleWorkflowError("Maximum iterations reached (" + MAX_ITERATIONS + "). Possible infinite loop.");
            return;
        }

        Log.d(TAG, "Processing AI response (iteration " + iterationCount + ")");

        // Parse response
        StormyResponseParser.StormyParsedResponse response = StormyResponseParser.parseResponse(responseText);

        if (response == null || !response.isValid) {
            handleWorkflowError("Invalid response from AI: " + responseText.substring(0, Math.min(200, responseText.length())));
            return;
        }

        // Record in history
        executionHistory.add(new ExecutionStep(response, null));

        // Handle based on action type
        switch (response.action) {
            case "tool_use":
                handleToolUse(response);
                break;

            case "message":
                callback.onMessage(response.message);
                // Continue workflow after a short delay to allow UI update
                mainHandler.postDelayed(() -> continueWorkflow(), 500);
                break;

            case "ask_followup_question":
                // Pause workflow and wait for user input
                isRunning = false;
                callback.onQuestionAsked(response.question, response.reasoning);
                callback.onWorkflowEnded();
                break;

            case "attempt_completion":
                // Task complete
                isRunning = false;
                callback.onTaskCompleted(response.summary);
                callback.onWorkflowEnded();
                break;

            default:
                handleWorkflowError("Unknown action type: " + response.action);
                break;
        }
    }

    /**
     * Handle tool execution with approval flow
     */
    private void handleToolUse(StormyResponseParser.StormyParsedResponse response) {
        // Check if approval needed
        boolean agentModeEnabled = SettingsActivity.isAgentModeEnabled(context);
        boolean needsApproval = StormyResponseParser.requiresApproval(response, agentModeEnabled);

        Log.d(TAG, "Tool use: " + response.tool + ", needs approval: " + needsApproval);

        if (needsApproval) {
            // Request approval from user
            callback.onApprovalNeeded(response, new ApprovalCallback() {
                @Override
                public void onApproved() {
                    Log.d(TAG, "Tool approved by user");
                    executeTool(response);
                }

                @Override
                public void onRejected(String reason) {
                    Log.d(TAG, "Tool rejected by user: " + reason);
                    handleToolRejection(response, reason);
                }
            });
        } else {
            // Execute immediately (Agent Mode ON or read-only tool)
            executeTool(response);
        }
    }

    /**
     * Execute a tool and handle the result
     */
    private void executeTool(StormyResponseParser.StormyParsedResponse response) {
        if (!isRunning) {
            Log.w(TAG, "Workflow stopped, skipping tool execution");
            return;
        }

        Log.d(TAG, "Executing tool: " + response.tool);

        callback.onToolStarted(response);

        // Execute the tool
        JsonObject result;
        try {
            result = StormyToolExecutor.execute(projectDir, response.tool, response.toolArgs);
        } catch (Exception e) {
            Log.e(TAG, "Tool execution exception", e);
            result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", "Exception: " + e.getMessage());
        }

        // Record in history
        executionHistory.get(executionHistory.size() - 1).toolResult = result;

        // Check result
        boolean success = result.has("ok") && result.get("ok").getAsBoolean();

        if (success) {
            Log.d(TAG, "Tool succeeded: " + response.tool);
            callback.onToolCompleted(response, result);

            // Check if this is an interaction tool (doesn't need continuation)
            if (isInteractionTool(response.tool)) {
                // Interaction tools (ask_followup_question, attempt_completion) handle workflow control themselves
                // Already handled in processAIResponse
                return;
            }

            // Send result back to AI and continue
            sendToolResultAndContinue(response, result);
        } else {
            Log.w(TAG, "Tool failed: " + response.tool + ", error: " + result.get("error").getAsString());
            callback.onToolFailed(response, result);

            // Send error to AI for handling
            sendToolErrorAndContinue(response, result);
        }
    }

    /**
     * Handle tool rejection by user
     */
    private void handleToolRejection(StormyResponseParser.StormyParsedResponse response, String reason) {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "Sending tool rejection to AI");

        // Build rejection message
        String rejectionMessage = "I've rejected the proposed action: " + StormyResponseParser.getToolDescription(response) + "\n" +
            "Reason: " + reason + "\n\n" +
            "Please propose an alternative approach or ask for clarification.";

        // Add rejection to conversation history
        ChatMessage rejectionChatMessage = new ChatMessage(ChatMessage.ROLE_USER, rejectionMessage, null);
        conversationHistory.add(rejectionChatMessage);

        // Send to AI
        sendToAI(rejectionMessage, new AIResponseHandler() {
            @Override
            public void onResponse(String responseText) {
                // Add AI response to history
                ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, responseText, null);
                conversationHistory.add(aiMessage);

                processAIResponse(responseText);
            }

            @Override
            public void onError(String error) {
                handleWorkflowError("AI request failed after rejection: " + error);
            }
        });
    }

    /**
     * Send tool result back to AI and continue iteration
     */
    private void sendToolResultAndContinue(StormyResponseParser.StormyParsedResponse response, JsonObject result) {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "Sending tool result to AI and continuing");

        // Build tool result message in a format the AI can understand
        StringBuilder toolResultText = new StringBuilder();
        toolResultText.append("Tool execution result:\n");
        toolResultText.append("Tool: ").append(response.tool).append("\n");
        toolResultText.append("Status: ").append(result.has("ok") && result.get("ok").getAsBoolean() ? "SUCCESS" : "FAILED").append("\n");

        if (result.has("message")) {
            toolResultText.append("Message: ").append(result.get("message").getAsString()).append("\n");
        }

        // Include relevant result data based on tool type
        if (response.tool.equals("list_files") && result.has("files")) {
            toolResultText.append("\nFiles:\n");
            JsonArray files = result.getAsJsonArray("files");
            int count = Math.min(files.size(), 50); // Limit output
            for (int i = 0; i < count; i++) {
                JsonObject file = files.get(i).getAsJsonObject();
                String type = file.get("type").getAsString();
                String path = file.get("path").getAsString();
                toolResultText.append("  ").append(type.equals("directory") ? "[DIR]  " : "[FILE] ").append(path).append("\n");
            }
            if (files.size() > count) {
                toolResultText.append("  ... and ").append(files.size() - count).append(" more\n");
            }
        } else if (response.tool.equals("read_file") && result.has("content")) {
            toolResultText.append("\nContent:\n");
            toolResultText.append(result.get("content").getAsString());
        } else if (response.tool.equals("search_files") && result.has("matches")) {
            toolResultText.append("\nMatches:\n");
            JsonArray matches = result.getAsJsonArray("matches");
            for (int i = 0; i < Math.min(matches.size(), 20); i++) {
                JsonObject match = matches.get(i).getAsJsonObject();
                toolResultText.append("  ").append(match.get("file").getAsString())
                    .append(":").append(match.get("line").getAsInt())
                    .append(" - ").append(match.get("content").getAsString()).append("\n");
            }
        } else if (result.has("error")) {
            toolResultText.append("\nError: ").append(result.get("error").getAsString()).append("\n");
        }

        toolResultText.append("\nPlease continue with the next step to complete the task.");

        String toolResultMessage = toolResultText.toString();

        // Add tool result to conversation history as a user message
        // (since the AI needs to see it as input for the next response)
        ChatMessage toolResultChatMessage = new ChatMessage(ChatMessage.ROLE_USER, toolResultMessage, null);
        conversationHistory.add(toolResultChatMessage);

        // Send to AI
        sendToAI(toolResultMessage, new AIResponseHandler() {
            @Override
            public void onResponse(String responseText) {
                // Add AI response to history
                ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, responseText, null);
                conversationHistory.add(aiMessage);

                processAIResponse(responseText);
            }

            @Override
            public void onError(String error) {
                handleWorkflowError("AI request failed after tool result: " + error);
            }
        });
    }

    /**
     * Send tool error to AI for handling
     */
    private void sendToolErrorAndContinue(StormyResponseParser.StormyParsedResponse response, JsonObject result) {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "Sending tool error to AI");

        // Build error message in human-readable format
        String errorMessage = "Tool execution failed:\n" +
            "Tool: " + response.tool + "\n" +
            "Error: " + result.get("error").getAsString() + "\n\n" +
            "Please analyze the error and try a different approach or ask for clarification.";

        // Add error to conversation history
        ChatMessage errorChatMessage = new ChatMessage(ChatMessage.ROLE_USER, errorMessage, null);
        conversationHistory.add(errorChatMessage);

        // Send to AI
        sendToAI(errorMessage, new AIResponseHandler() {
            @Override
            public void onResponse(String responseText) {
                // Add AI response to history
                ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, responseText, null);
                conversationHistory.add(aiMessage);

                processAIResponse(responseText);
            }

            @Override
            public void onError(String error) {
                handleWorkflowError("AI request failed after tool error: " + error);
            }
        });
    }

    /**
     * Continue workflow without sending anything to AI (for message actions)
     */
    private void continueWorkflow() {
        if (!isRunning) {
            return;
        }

        // For now, we don't automatically continue after messages
        // The user needs to send a new message to continue
        Log.d(TAG, "Message action - waiting for user input");
        isRunning = false;
        callback.onWorkflowEnded();
    }

    /**
     * Send a message to the AI with full conversation context
     */
    private void sendToAI(String message, AIResponseHandler handler) {
        try {
            // Use the new method that accepts conversation history and state
            aiAssistant.sendMessage(message, conversationHistory, conversationState, new AIAssistant.ResponseCallback() {
                @Override
                public void onSuccess(String response) {
                    mainHandler.post(() -> handler.onResponse(response));
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> handler.onError(error));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending to AI", e);
            handler.onError("Exception: " + e.getMessage());
        }
    }

    /**
     * Handle a workflow error
     */
    private void handleWorkflowError(String error) {
        Log.e(TAG, "Workflow error: " + error);
        isRunning = false;
        callback.onError(error);
        callback.onWorkflowEnded();
    }

    /**
     * Check if a tool is an interaction tool (doesn't need result continuation)
     */
    private boolean isInteractionTool(String toolName) {
        return toolName.equals("ask_followup_question") || toolName.equals("attempt_completion");
    }

    /**
     * Get workflow status
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get execution history
     */
    public List<ExecutionStep> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }

    /**
     * Get iteration count
     */
    public int getIterationCount() {
        return iterationCount;
    }

    /**
     * Internal interface for AI response handling
     */
    private interface AIResponseHandler {
        void onResponse(String responseText);
        void onError(String error);
    }
}
