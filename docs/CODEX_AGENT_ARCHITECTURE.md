# Codex Agent Architecture

## 1. Overview

This document proposes a new agentic architecture for the Codex Android application, inspired by the modular and extensible design of the `qwen-code` CLI. The goal is to refactor the existing monolithic architecture into a more robust, scalable, and maintainable system that can support a growing number of tools and more complex agentic workflows.

The new architecture will be centered around a clear separation of concerns, with distinct layers for the UI, the core agent logic, services, and tools.

## 2. Proposed Architecture

The proposed architecture consists of the following layers:

*   **UI Layer:** This layer is responsible for rendering the chat interface and handling user input. It will be implemented in the `AIChatFragment` and will communicate with the Core Agent Layer through a well-defined interface.
*   **Core Agent Layer:** This layer is the heart of the agent. It will be responsible for orchestrating the agentic workflow, managing the conversation history, and dispatching tool calls.
*   **Services Layer:** This layer will provide low-level functionality to the tools. Each service will be responsible for a specific domain, such as file system operations, shell command execution, or network requests.
*   **Tools Layer:** This layer will consist of a collection of self-contained tool classes. Each tool will expose a specific functionality to the AI model and will interact with the Services Layer to perform its operations.

### 2.1. UI Layer

The UI Layer will be responsible for the following:

*   Displaying the chat history, including user messages, AI responses, tool calls, and tool outputs.
*   Handling user input and sending it to the Core Agent Layer.
*   Displaying streaming output from the AI and the tools.
*   Prompting the user for confirmation before executing potentially dangerous tool calls.

The `AIChatFragment` will be refactored to delegate all agentic logic to the Core Agent Layer. It will communicate with the Core Agent Layer through a listener interface.

### 2.2. Core Agent Layer

The Core Agent Layer will be responsible for the following:

*   Managing the main agentic loop: receiving user input, calling the AI model, parsing the response, and dispatching tool calls.
*   Maintaining the conversation history.
*   Managing the state of the agent, including the current context and the available tools.
*   Providing a clear interface for the UI Layer to interact with the agent.

This layer will be implemented in a new `CodexAgent` class, which will replace the `AIAssistant` class.

### 2.3. Services Layer

The Services Layer will consist of a set of service classes, each responsible for a specific low-level functionality. This will allow for a clean separation of concerns and will make it easier to add new functionality to the agent.

Examples of services include:

*   `FileSystemService`: Provides an API for reading, writing, and deleting files.
*   `ShellExecutionService`: Provides an API for executing shell commands.
*   `GitService`: Provides an API for interacting with Git repositories.
*   `NetworkService`: Provides an API for making HTTP requests.

### 2.4. Tools Layer

The Tools Layer will consist of a collection of tool classes, each representing a specific tool that the AI model can use. Each tool class will have a well-defined interface, including a name, a description, and a set of parameters.

The tools will be registered with a `ToolRegistry`, which will be used by the Core Agent Layer to look up and execute tools.

This modular design will make it easy to add new tools to the agent without modifying the core logic.

## 3. Data Flow

The data flow in the new architecture will be as follows:

1.  The user enters a prompt in the `AIChatFragment`.
2.  The `AIChatFragment` sends the prompt to the `CodexAgent`.
3.  The `CodexAgent` adds the prompt to the conversation history and calls the Qwen API.
4.  The `CodexAgent` receives a streaming response from the Qwen API.
5.  The `CodexAgent` parses the response and identifies any tool calls.
6.  For each tool call, the `CodexAgent` looks up the corresponding tool in the `ToolRegistry`.
7.  The `CodexAgent` executes the tool, passing in the required parameters.
8.  The tool interacts with the Services Layer to perform its operations.
9.  The tool returns a result to the `CodexAgent`.
10. The `CodexAgent` adds the tool result to the conversation history and sends it back to the Qwen API to continue the conversation.
11. The `CodexAgent` streams the final response from the AI to the `AIChatFragment`.
12. The `AIChatFragment` displays the response to the user.

## 4. Implementation Plan

The implementation of the new architecture will be carried out in the following phases:

1.  **Phase 1: Refactor the Core Agent Logic.**
    *   Create the `CodexAgent` class to replace the `AIAssistant` class.
    *   Implement the core agentic loop in the `CodexAgent`.
    *   Refactor the `AIChatFragment` to use the `CodexAgent`.
2.  **Phase 2: Implement the Services Layer.**
    *   Create the `FileSystemService`, `ShellExecutionService`, and other necessary services.
    *   Move the low-level logic from the `ToolExecutor` to the corresponding services.
3.  **Phase 3: Implement the Tools Layer.**
    *   Create individual tool classes for each tool, replacing the `ToolExecutor`.
    *   Create a `ToolRegistry` to manage the available tools.
    *   Integrate the new tools with the `CodexAgent`.
