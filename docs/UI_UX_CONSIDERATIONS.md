# UI/UX Considerations for the New Agentic Workflow

## 1. Introduction

This document outlines the user interface (UI) and user experience (UX) considerations for integrating the new, modular agentic architecture into the `AIChatFragment` of the Codex Android application. The primary challenge is to translate the powerful, text-based workflow of a CLI tool like `qwen-code` into an intuitive and visually clear experience on a mobile platform.

## 2. Core Principles

*   **Clarity:** The user must always understand what the AI is doing, why it's doing it, and what the results are.
*   **Control:** The user should feel in control of the agent, with the ability to approve or deny potentially destructive actions.
*   **Responsiveness:** The UI must remain responsive, even when the agent is performing long-running tasks.

## 3. Key UI/UX Enhancements

### 3.1. Visualizing Tool Calls and Plans

The current system of displaying AI plans and actions should be enhanced to provide more detail and clarity.

*   **Distinct Message Types:** Create new view types in the `ChatMessageAdapter` for:
    *   **AI Plan:** A checklist-style view showing the steps the AI intends to take. Each step should have a status indicator (e.g., Pending, In Progress, Success, Failure).
    *   **Tool Call:** A message indicating which tool is being executed, along with its parameters. This could be a card with a specific icon for each tool (e.g., a terminal icon for `shell`, a file icon for `readFile`).
    *   **Tool Output:** A message displaying the results of a tool's execution.

*   **Collapsible Content:** For tool calls with large parameters or tool outputs with long results, the content should be displayed in a collapsed state by default, with a "Show More" button to expand it.

### 3.2. User Confirmation for Sensitive Tools

To ensure user control and prevent accidental damage, a confirmation mechanism must be implemented for potentially sensitive tools (`shell`, `deleteFile`, `writeFile`, etc.).

*   **Confirmation Prompt:** When the AI attempts to use a sensitive tool for the first time, the UI should present a non-blocking confirmation prompt within the chat flow.
*   **Confirmation Options:** The prompt should offer clear choices:
    *   **Allow Once:** Execute this specific command just this one time.
    *   **Always Allow:** Add this specific command to an allowlist for the current session, so the user is not prompted again.
    *   **Deny:** Do not execute the command.
*   **Visual Cue:** The tool call message should have a "Pending Confirmation" state while waiting for user input.

### 3.3. Streaming and Real-Time Feedback

The UI needs to effectively communicate that the agent is working, especially for streaming output and long-running tasks.

*   **Live Updates:** The content of a tool output message should update in real-time as new data is streamed from the tool (e.g., the output of a `shell` command).
*   **Simulated Terminal:** For the `shell` tool, the output view could be styled to resemble a mini-terminal, providing a familiar experience for developers.
*   **Progress Indicators:** Use subtle loading indicators or spinners within the plan steps and tool call messages to show that the agent is actively processing.

### 3.4. Managing Background Processes

The `shell` tool can start background processes. The UI must provide a way for the user to manage these.

*   **Status Indicator:** A persistent, non-intrusive UI element (e.g., a small icon or bar at the top of the chat view) should appear when background tasks are running.
*   **Task Manager:** Tapping the status indicator could open a simple dialog listing the active background processes, their command, and an option to terminate them.

### 3.5. Example Workflow Visualization

Here is a descriptive mock-up of how a typical interaction could look in the chat view:

1.  **User:**
    > "Add a new file called `README.md` with the content 'Hello, World!'."

2.  **AI (Plan):**
    > **Plan:**
    > *   [ PENDING ] Create a new file named `README.md`.

3.  **AI (Tool Call):**
    > **Executing Tool:** `createFile`
    > *   **path:** `README.md`
    > *   **content:** "Hello, World!"
    >
    > *Waiting for user confirmation...*
    >
    > `[Allow Once]` `[Always Allow]` `[Deny]`

4.  *(User taps "Allow Once")*

5.  **AI (Tool Call updating to In Progress):**
    > **Executing Tool:** `createFile` (In Progress...)

6.  **AI (Tool Result):**
    > **Tool Result:** `createFile`
    > *   **status:** Success
    > *   **message:** "File created: README.md"

7.  **AI (Final Response):**
    > I have successfully created the `README.md` file with the content you requested.
