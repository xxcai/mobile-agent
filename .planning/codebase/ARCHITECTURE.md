# Architecture

**Analysis Date:** 2026-03-03

## Pattern Overview

**Overall:** MVP (Model-View-Presenter) Architecture

This codebase uses MVP pattern for Android:
- **Android (Java):** MVP (Model-View-Presenter) pattern

**Key Characteristics:**
- Clean separation between UI and business logic via MVP Contract interfaces
- API abstraction allowing pluggable implementations (HTTP/Mock)
- Native Android UI only (Vue frontend removed)
- Session-based chat model with in-memory message storage

## Layers

### Android Layer (app module)

**View Layer:**
- Location: `app/src/main/java/com/hh/agent/`
- Contains: `MainActivity.java`, `MessageAdapter.java`
- Responsibilities: UI rendering, user input handling, lifecycle management
- Depends on: Presenter, Models
- Used by: Android framework

**Presenter Layer:**
- Location: `app/src/main/java/com/hh/agent/presenter/`
- Contains: `MainPresenter.java`
- Responsibilities: Business logic, API coordination, thread management
- Depends on: Contract interfaces, NanobotApi
- Used by: View (MainActivity)

**Contract Layer:**
- Location: `app/src/main/java/com/hh/agent/contract/`
- Contains: `MainContract.java`
- Responsibilities: Define View and Presenter interfaces for MVP communication
- Depends on: Models (Message)
- Used by: View and Presenter

### Library Layer (lib module)

**API Layer:**
- Location: `lib/src/main/java/com/hh/agent/lib/api/`
- Contains: `NanobotApi.java` (interface)
- Responsibilities: Define contract for chat operations
- Depends on: Models
- Used by: Presenters, HTTP/Mock implementations

**HTTP Implementation:**
- Location: `lib/src/main/java/com/hh/agent/lib/http/`
- Contains: `HttpNanobotApi.java`
- Responsibilities: Execute HTTP calls to Nanobot service
- Depends on: NanobotApi, Config, DTOs, OkHttp
- Used by: Presenters

**Mock Implementation:**
- Location: `lib/src/main/java/com/hh/agent/lib/impl/`
- Contains: `MockNanobotApi.java`
- Responsibilities: Provide mock responses for testing
- Depends on: NanobotApi interface
- Used by: Presenters (switchable via ApiType)

**Model Layer:**
- Location: `lib/src/main/java/com/hh/agent/lib/model/`
- Contains: `Message.java`, `Session.java`
- Responsibilities: Data entities for messages and sessions
- Used by: All layers

**DTO Layer:**
- Location: `lib/src/main/java/com/hh/agent/lib/dto/`
- Contains: `ChatRequest.java`, `ChatResponse.java`
- Responsibilities: Request/response serialization structures
- Used by: HTTP implementation

**Config Layer:**
- Location: `lib/src/main/java/com/hh/agent/lib/config/`
- Contains: `NanobotConfig.java`
- Responsibilities: HTTP endpoint and timeout configuration
- Used by: HTTP implementation

## Data Flow

**Message Send Flow:**

1. User enters message in UI (MainActivity)
2. View calls Presenter.store method (sendMessage)
3. Presenter creates user message and notifies View
4. Presenter shows "thinking" indicator
5. Presenter executes API call in background thread
6. API implementation (HttpNanobotApi) sends HTTP POST to Nanobot
7. Response converted to Message model
8. Presenter updates View with assistant response
9. View renders message in RecyclerView

**State Management:**
- Android: Presenter holds reference to View, uses Handler for UI thread updates

## Key Abstractions

**NanobotApi Interface:**
- Purpose: Define chat operations contract
- Examples: `lib/src/main/java/com/hh/agent/lib/api/NanobotApi.java`
- Pattern: Strategy pattern - allows runtime selection of HTTP or Mock implementation

**MainContract Interface:**
- Purpose: Decouple View and Presenter
- Examples: `app/src/main/java/com/hh/agent/contract/MainContract.java`
- Pattern: Passive View - View interface only, no Presenter reference in View

**Session Management:**
- Purpose: Group messages per conversation
- Examples: `lib/src/main/java/com/hh/agent/lib/model/Session.java`
- Pattern: In-memory session store with key-based lookup

## Entry Points

**Android Main Entry:**
- Location: `app/src/main/java/com/hh/agent/MainActivity.java`
- Triggers: App launch from launcher
- Responsibilities: Initialize MVP, load messages, handle user input

## Error Handling

**Strategy:** Try-catch with fallback messaging

**Patterns:**
- Presenter catches exceptions and displays error via View.onError()
- HTTP implementation catches IOException and returns error Message
- Network errors show as user-friendly messages

## Cross-Cutting Concerns

**Logging:** Android Log.d/e for debugging HTTP operations

**Validation:** Input validation in Presenter (empty message check)

**Authentication:** Session-based via sessionKey parameter (no auth token)

---

*Architecture analysis: 2026-03-03*
