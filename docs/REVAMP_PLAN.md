# Codex Agentic Workflow Revamp Plan

## 1. Introduction

This document outlines a plan to revamp the agentic workflow of the Codex Android application. The current implementation is naive and monolithic, making it difficult to extend and maintain. The proposed changes are inspired by the `qwen-code` CLI, which has a more robust and modular architecture.

## 2. Proposed Architecture

The new architecture will be based on a clear separation of concerns between the following components:

*   **Tool Definition**: A structured way to define tools.
*   **Tool Registry**: A central registry for managing tools.
*   **Agent Core**: The core logic of the agent, responsible for orchestrating the workflow.
*   **API Client**: A thin client for interacting with the Qwen API.
*   **UI**: The user interface for interacting with the agent.

## 3. Core Component Changes

### 3.1. Tool Definition

We will introduce a new `Tool` interface with the following methods:

```java
public interface Tool {
    String getName();
    String getDescription();
    JSONObject getParameterSchema();
    ToolResult execute(JSONObject parameters);
}
```

This interface will provide a structured way to define tools, including their name, description, and parameter schema. The `execute` method will contain the logic for executing the tool.

### 3.2. Tool Registry

We will create a new `ToolRegistry` class that will be responsible for managing the available tools. This class will have methods for registering and retrieving tools.

### 3.3. Agent Core

The `AIAssistant` class will be refactored to be the core of the agent. It will be responsible for the following:

*   Orchestrating the agentic workflow.
*   Retrieving tools from the `ToolRegistry`.
*   Sending prompts to the Qwen API.
*   Parsing the API response for tool calls.
*   Executing tool calls using the `Tool` interface.
*   Sending the results of tool calls back to the API.

### 3.4. API Client

The `QwenApiClient` will be refactored to be a thin client for interacting with the Qwen API. It will be responsible for the following:

*   Sending requests to the API.
*   Receiving and parsing the API response.

## 4. UI/UX Changes in `AIChatFragment`

The `AIChatFragment` will be updated to provide a richer user experience for interacting with the agent. The following changes will be made:

*   **Displaying the Agent's Plan**: The UI will be updated to display the agent's plan, which will be a list of steps that the agent intends to take.
*   **Displaying Tool Calls**: The UI will be updated to display the tool calls that the agent is making, along with the results of those calls.
*   **User Interaction**: The UI will be updated to allow the user to interact with the agent's plan. For example, the user will be able to approve or reject the plan.

## 5. Step-by-Step Implementation Plan

1.  **Implement the `Tool` interface and `ToolRegistry` class.**
2.  **Refactor the existing tools to implement the `Tool` interface.**
3.  **Refactor the `AIAssistant` to be the core of the agent.**
4.  **Refactor the `QwenApiClient` to be a thin client.**
5.  **Update the `AIChatFragment` to display the agent's plan and tool calls.**

## 6. Conclusion

The proposed changes will make the Codex agentic workflow more robust, modular, and extensible. This will make it easier to add new features and tools in the future.
