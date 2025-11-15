# Codex Android App – Developer Handbook

## Quick Facts
- **Target SDK**: 34 (`compileSdk` 34, `targetSdk` 34).
- **Minimum SDK**: 21.
- **Language**: Java 17 across the codebase.
- **Entry Points**: `MainActivity` (`app/src/main/java/com/codex/apk/MainActivity.java`) launches the workspace UI; `EditorActivity` (`app/src/main/java/com/codex/apk/EditorActivity.java`) provides the core editor + chat experience.
- **Application Class**: `CodeXApplication` (`app/src/main/java/com/codex/apk/CodeXApplication.java`) sets global theme and crash handling.

## Repository Layout (app/)
```
app/
├── build.gradle
├── codex.keystore
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/
    ├── java/com/codex/apk/
    │   ├── ai/
    │   ├── editor/
    │   │   └── adapters/
    │   ├── lint/
    │   ├── util/
    │   └── *.java (activities, fragments, managers, adapters)
    └── res/
        ├── drawable/
        ├── layout/
        ├── menu/
        ├── mipmap/
        ├── values/
        └── xml/
```

> Only files under `app/` are documented here, per project guidance.

## Build & Run Checklist
- **Tooling**: Android Studio Flamingo+ (Gradle 8.x), Android SDK 34, JDK 17.
- **Clone**: `git clone` then open the root in Android Studio. Import as Gradle project.
- **API Credentials**: The app uses a single, built-in API provider (Qwen) that does not require user-configured API keys.
- **Signing**: A debug keystore is bundled as `app/codex.keystore` (password `codex123`). If removing from VCS, update `build.gradle` to match your keystore.
- **Gradle Sync**: Ensure sync completes; dependencies are published to Maven Central or GitHub packages (no local AARs required).
- **Run**: Choose an emulator/device on API 21+ and launch.

## Application Architecture Overview

- **Presentation Layer**: Activities and fragments coordinate UI. `MainActivity` handles project list navigation; `EditorActivity` hosts `CodeEditorFragment` (code view) and `AIChatFragment` (assistant view).
- **Manager Layer**: High-level orchestrators encapsulate domain flows.
  - `ProjectManager` (`ProjectManager.java`) and `ProjectImportExportManager` manage workspace persistence.
  - `ToolExecutor` (`ToolExecutor.java`) drives AI-generated file operations.
- **Domain Layer**: Models in `ai/` describe providers, models, capabilities, and prompt composition.
- **Infrastructure Layer**: The `QwenApiClient` (`QwenApiClient.java`) performs network I/O with OkHttp, with streaming handled via `SseClient`.
- **Utilities**: Helpers in `util/` (`FileOps`, `JsonUtils`, `ResponseUtils`) consolidate cross-cutting concerns.

The architecture does not enforce MVVM; responsibilities bleed between UI and managers. Future work should introduce ViewModels and dependency injection to tighten contracts.

## Startup Sequence
1. `CodeXApplication.onCreate()` applies theming via `ThemeManager.setupTheme()` and installs a crash handler launching `DebugActivity` (`DebugActivity.java`).
2. `MainActivity` loads recent projects using `ProjectManager` and `ProjectsAdapter`.
3. Selecting a project starts `EditorActivity`, which wires `FileTreeManager`, `TabManager`, and `CodeEditorFragment`.
4. `EditorActivity` attaches `AIChatFragment`; `AIChatUIManager` binds RecyclerViews and view bindings.
5. `AIAssistant` is initialized, which in turn sets up the `QwenApiClient`.

## Feature Walkthroughs

### AI Chat + Tooling Flow
- **Entry**: User submits prompt through `AIChatFragment` (`AIChatFragment.java`).
- **Network**: The `AIAssistant` routes requests to the `QwenApiClient`, which streams responses via `SseClient` (see `QwenStreamProcessor.java`).
- **Parsing**: `QwenResponseParser` extracts actions, plans, and tool suggestions from the incoming stream.
- **UI Update**: `AIChatUIManager` posts updates to `ChatMessageAdapter`, toggling plan cards, file diffs, and tool usage.
- **Tool Execution**: `ToolExecutor` orchestrates file modifications, calling helpers in `FileOps` and diff utilities (`DiffGenerator`, `DiffUtils`).

Key data class: `ChatMessage` stores raw responses, plan steps, thinking content, web sources, and tool usage for rendering.

### Project Lifecycle
- `ProjectManager` supports creating, opening, renaming, and deleting projects under the workspace root.
- `GitManager` wraps JGit commands for cloning remote repositories.
- `ProjectImportExportManager` zips/unzips workspaces, coordinating with `AdvancedFileManager` for storage access and SAF prompts.
- Recycler adapters (`ProjectsAdapter`, `RecentProjectsAdapter`) render project items.

### Code Editing & Diffing
- `CodeEditorFragment` hosts a Rosemoe editor widget (syntax highlighting via BOM `io.github.rosemoe:editor-bom`).
- `TabManager` maintains multiple open files (`TabItem` models) with state persistence.
- Diff visualizations use `PreviewActivity`, `InlineDiffAdapter`, and `SplitDiffAdapter` with support from `DiffGenerator`.
- Markdown previews rely on `MarkdownFormatter` leveraging Markwon modules.

### Settings & Model Management
- `SettingsActivity` exposes preferences for theme, editor settings (font size, word wrap), and other UI toggles.
- `ModelsActivity` displays available AI models using `ModelAdapter` and allows setting a default model.
- Provider metadata defined by `AIModel`, `AIProvider`, and `ModelCapabilities` enumerations.

## AI Provider Integration

The application has been streamlined to use a single AI provider, **Alibaba Qwen**, via the `QwenApiClient`. This client handles stateful conversations (`QwenConversationManager`, `QwenMidTokenManager`), SSE streaming, and parsing tool-use actions.

The previous multi-provider abstraction (`ApiClient.java`) has been removed, simplifying the architecture significantly. All AI-related network traffic is now managed through the `QwenApiClient`.

## Core Data & State Objects
- `ChatMessage`: Chat payload with message types, plan steps (`ChatMessage.PlanStep`), tool usage, and file change proposals.
- `QwenConversationState`: Tracks conversation IDs and thread tokens for Qwen.
- `ToolSpec`: Declares tool metadata (name, description, parameters) consumed by AI planning.
- `AIModel`/`ModelCapabilities`: Define provider-specific features (streaming, tool support, file context).

## Managers, Utilities, and Helpers
- `FileOps`: Read/write/copy, BOM handling, safe file rename operations.
- `AdvancedFileManager` and `FileManager`: Bridge between Android storage APIs and internal file operations.
- `PromptManager`: Supplies system prompts for general vs. agent mode usage.
- `TemplateManager`: Stores canned templates/snippets.
- `ToolExecutor`: Executes plan steps, applying diffs with `DiffUtils` and `DiffGenerator`.
- `LocalServerManager`: Spins up local endpoints for preview or callbacks.

## UI Layer Breakdown
- **Activities**: `MainActivity`, `EditorActivity`, `SettingsActivity`, `ModelsActivity`, `AboutActivity`, `PreviewActivity`, `DebugActivity`.
- **Fragments**: `AIChatFragment`, `CodeEditorFragment`.
- **Adapters**: `ChatMessageAdapter`, `FileActionAdapter`, `ProjectsAdapter`, `RecentProjectsAdapter`, `ModelAdapter`, `SimpleSoraTabAdapter`, `SplitDiffAdapter`, `InlineDiffAdapter`, `WebSourcesAdapter`.
- **Layout Resources**: Found under `app/src/main/res/layout/`, each adapter references specific item layouts (e.g., `item_chat_message_ai`, `item_plan_step`).

Heavy adapters (notably `ChatMessageAdapter`) contain extensive binding logic (>600 LOC). Future work should divide responsibilities into dedicated view binders or Compose UI modules.

## Asynchronous Patterns
- Uses raw `new Thread` for network tasks (`QwenApiClient`, `AIAssistant`, etc.).
- Streaming handled via callbacks from `SseClient`.
- No centralized executor or coroutine usage; introducing `ExecutorService` or RxJava would reduce duplication and improve control.
- UI updates rely on `runOnUiThread` or `Handler` posted from managers.

## Error Handling & Logging
- Global crashes are caught by `CodeXApplication` and displayed in `DebugActivity` with stack traces passed via Intent extras.
- API errors call `AIAssistant.AIActionListener.onAiError()`; ensure listener is non-null before invocation.
- Logging mostly uses `Log.d`/`Log.e` inline; no structured logging.
- Toasts provide user feedback for common errors.

## Resources & Assets
- `assets/` contains prompt templates, stylesheets, and perhaps example projects (verify contents when extending).
- `res/drawable` includes icons for file types, actions, statuses.
- `res/values` hosts strings, themes (light/dark), and color schemes.
- `res/xml/file_paths.xml` defines `FileProvider` paths matching `AndroidManifest.xml` provider entry.

## Performance & Stability Notes
- Many classes exceed 500 lines (e.g., `ChatMessageAdapter`, `PreviewActivity`, `QwenApiClient`). Split into cohesive modules to honor the project guideline and improve readability.
- Markdown rendering and bitmap decoding occur inside `RecyclerView` binders; cache results or pre-process off the UI thread.
- Frequent `new Thread().start()` calls may cause thread exhaustion; migrate to a shared executor.
- SSE parsing in `QwenApiClient` builds strings aggressively; consider streaming JSON parsing.

## Testing & Quality
- No instrumentation/unit tests currently in repo. Recommended additions:
  - Unit tests for `QwenResponseParser`, `ToolExecutor`, `DiffGenerator`.
  - Integration tests simulating AI chat flows with a mock API client.
  - UI tests for `EditorActivity` using Espresso.
- Enable Android Lint, PMD, or SpotBugs to detect unused code and complexity issues.
- Consider adding CI workflow (GitHub Actions) to run `./gradlew lint test` on PRs.

## Extending the Codebase

### Adding a New AI Provider
The current architecture is tightly coupled to the `QwenApiClient`. To add a new provider, you would need to re-introduce an abstraction layer:
1. Create an `ApiClient` interface that defines a common contract for sending messages.
2. Refactor `AIAssistant` to hold a map of `ApiClient` implementations.
3. Create a new class for your provider that implements the `ApiClient` interface.
4. Update `AIAssistant` to initialize and use the new client based on the selected `AIModel`.
5. Update UI (`ModelsActivity`, `SettingsActivity`) to expose any necessary configuration.

### Introducing Modular Architecture
- Add AndroidX Lifecycle dependencies and migrate `EditorActivity` state into a `ViewModel`.
- Extract service layers (API, storage, diffing) behind interfaces. Consider dependency injection (Hilt or manual factory) to simplify unit testing.
- For Compose adoption, port `ChatMessageAdapter` UI to composables for declarative updates.

### Tooling Enhancements
- Extend `ToolExecutor` to support additional operations (e.g., new file creation templates) by updating `ToolSpec` definitions and handler logic.

## Known Refactoring Targets
- `ChatMessageAdapter.java` (606 LOC) – split by view types.
- `QwenApiClient.java`, `PreviewActivity.java`, `TemplateManager.java`, `ProjectManager.java` – all near or above the 500 LOC threshold.
- Consolidate file operations between `FileManager`, `AdvancedFileManager`, and `FileOps`.
- Abstract duplicated OkHttp setup across API clients if more clients are added in the future.

## Troubleshooting Guide
- **Missing AI Responses**: For Qwen, ensure `QwenMidTokenManager.ensureMidToken()` succeeds (check logcat for `QwenMidTokenManager`). The app does not require external API keys.
- **File Actions Not Applying**: Confirm `ToolExecutor` has storage permissions. `PermissionManager` handles runtime prompts; watch for denial in logs.
- **Crash on Launch**: Inspect `DebugActivity` output. Common cause: missing theme resource referenced in `AndroidManifest.xml`.
- **Markdown Rendering Issues**: Check `MarkdownFormatter.getInstance()` initialization; ensure Markwon dependencies are synced.
- **Slow Scrolling in Chat**: Profile `ChatMessageAdapter.bind()`; consider precomputing Markdown and replacing heavy anonymous adapters for attachments.

## Contribution Guidelines
- Maintain files under 500 lines when feasible; split logic into focused classes.
- Follow existing Java code style (4-space indent, braces on new lines).
- Document public methods with concise comments; avoid removing existing documentation absent need.
- Run static analysis and format code before PRs.
- Coordinate large refactors to avoid disrupting concurrent contributors; consider feature branches per module.

## Glossary
- **Agent Mode**: When enabled, AI can produce action plans with tool invocations.
- **Thinking Mode**: Adds chain-of-thought style responses stored in `ChatMessage.getThinkingContent()`.
- **Plan Steps**: Structured actions produced by AI, rendered via plan cards in `ChatMessageAdapter`.
- **Tool Usage**: Metadata on executed tools displayed as chips in AI messages.

## Dependency Appendix
- `com.squareup.okhttp3:okhttp`: HTTP client for API integration; see `QwenApiClient` for usage patterns.
- `com.google.code.gson:gson`: JSON parsing building blocks (`JsonParser`, `JsonObject`) throughout the API client.
- `io.noties.markwon:*`: Markdown parsing and rendering; used in `MarkdownFormatter` and `ChatMessageAdapter`.
- `org.eclipse.jgit:org.eclipse.jgit`: Git commands in `GitManager` for cloning.
- `commons-io:commons-io`: Utility methods for file copying and stream handling in `FileOps`.
- `androidx.recyclerview:*`: Backbone for list presentations (chat, projects, tabs).
- `io.github.rosemoe:editor-*`: Advanced code editor component embedded in `CodeEditorFragment`.

---

This handbook provides future developers and AI agents with the essential context to navigate, extend, and maintain the Codex Android application. Pair this document with inline code comments for fine-grained implementation details.
