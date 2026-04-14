# Codepocalypse Now — LangChain4j Side Demo Script

Speaker notes + live-demo runbook for the Java/LangChain4j half of
**Codepocalypse Now: LangChain4j vs Spring AI**.

> Paired with Baruch's Spring AI track. Both sides build the same 6
> features against Claude. This file only covers the LangChain4j side.
>
> **You are on branch `round-4` (Rounds 1–4).** This script covers
> Rounds 1 through 4. Check out `round-5`..`round-6` to see later rounds.

---

## Stack (Round 4)

| Layer       | Version                                                                                   | Notes                      |
|-------------|-------------------------------------------------------------------------------------------|----------------------------|
| JDK         | 21                                                                                        | `sdkman` default           |
| Build       | Gradle 8.10 (wrapper)                                                                     | `./gradlew`                |
| Runtime     | Quarkus 3.34.3                                                                            | `quarkus-bom`              |
| LangChain4j | quarkus-langchain4j BOM 1.8.4                                                             | pulls `langchain4j:1.12.2` |
| LLM         | Claude Sonnet 4                                                                           | `claude-sonnet-4-20250514` |
| MCP server  | [developer-events MCP](https://developer-events-mcp-54127830651.europe-west2.run.app/mcp) | Streamable HTTP at `/mcp`  |
| Agentic     | `langchain4j-agentic:1.12.2-beta22`                                                       | via BOM                    |
| TUI client  | TUI4J 0.3.3                                                                               | Bubble Tea port for Java   |

## Branch layout

```
  round-1 (main)    Basic agent + TUI
  round-2           Persistent memory
  round-3           Tools + MCP
* round-4           Agentic supervisor   (EventsSpecialist + Calendar + General)
  round-5           (next)
  round-6           (next)
```

## Before you go on stage

```bash
export ANTHROPIC_API_KEY=sk-ant-...
git checkout round-4
./gradlew quarkusDev                         # Terminal 1
./tui.sh http://localhost:8080 vik           # Terminal 2
```

`./tui.sh` execs `java` directly — never run the TUI through Gradle
JavaExec (stdin piping breaks raw mode → Enter dies + double echo).

---

## Round 1 — Basic agent + TUI

```java
@RegisterAiService
public interface JClawAgent {
    @SystemMessage("You are JClaw, the Java cousin of NanoClaw...")
    String chat(String message);
}
```

"I didn't write an implementation — the framework IS the implementation."
Contrast with Baruch's `ChatClient.builder()`.

---

## Round 2 — Persistent memory

- `JClawAgent.chat(@MemoryId String sessionId, @UserMessage String message)`
- `FileChatMemoryStore` (@ApplicationScoped) → `~/.jclaw/memory/<id>.json`
- `quarkus.langchain4j.chat-memory.type=message-window` + `max-messages=20`

**Talking point:** Memory is declared, not wrapped. Baruch's equivalent
is `MessageWindowChatMemoryAdvisor` (middleware chain).

---

## Round 3 — Tools + MCP

**⚠ Required Quarkus 3.17 → 3.34.3 + LC4J 0.25 → 1.8.4** to get
`StreamableHttpMcpTransport` for the hosted server (old version only
had HTTP+SSE).

- `tools/LocalTools.java`: `@Tool getCurrentDateTime`, `getCurrentTimezone`
- `JClawAgent`: `@RegisterAiService(tools = LocalTools.class)` +
  `@McpToolBox("events")`
- `application.properties`:
  ```properties
  quarkus.langchain4j.mcp.events.transport-type=streamable-http
  quarkus.langchain4j.mcp.events.url=https://developer-events-mcp-54127830651.europe-west2.run.app/mcp
  ```

**MCP tools exposed:** `list_open_cfps`, `find_closing_cfps`,
`search_cfps_by_keyword`, `search_cfps_by_location`.

**Talking point:** Two tool sources, one annotation each. Spring AI has
a Boot starter for MCP; LangChain4j has `@McpToolBox`. Convergent
abstraction, different plumbing.

---

## Round 4 — Agentic supervisor

**Branch:** `round-4`

**⚠ Intentional architectural split**: `/chat` stays as the monolithic
Quarkus `@RegisterAiService` (memory + MCP + local tools, Rounds 1–3
intact). A **NEW** endpoint `/agent/chat` delegates to a supervisor
built programmatically from three specialist sub-agents.

This is the **key abstraction-war moment**: Spring AI says "agents are
just well-composed services"; LC4J says "agents are first-class and
deserve their own module."

**New files:**
- `agentic/EventsSpecialist.java` — LLM-only, conferences/CFPs
- `agentic/CalendarSpecialist.java` — uses `LocalTools` for time/tz
- `agentic/GeneralAssistant.java` — fallback
- `agentic/SupervisorProducer.java` — `@Produces @ApplicationScoped SupervisorAgent`
- `agentic/AgenticResource.java` — `POST /agent/chat`

**Supervisor wiring:**

```java
@Produces
@ApplicationScoped
public SupervisorAgent supervisor() {
    EventsSpecialist events = AgenticServices.agentBuilder(EventsSpecialist.class)
            .chatModel(chatModel).build();
    CalendarSpecialist calendar = AgenticServices.agentBuilder(CalendarSpecialist.class)
            .chatModel(chatModel).tools(localTools).build();
    GeneralAssistant general = AgenticServices.agentBuilder(GeneralAssistant.class)
            .chatModel(chatModel).build();

    return AgenticServices.supervisorBuilder()
            .chatModel(chatModel)
            .subAgents(events, calendar, general)
            .responseStrategy(SupervisorResponseStrategy.LAST)
            .maxAgentsInvocations(3)
            .build();
}
```

**Talking points:**
- "Spring AI doesn't have a dedicated agentic module — Baruch composes
  advisors. LangChain4j DOES — `langchain4j-agentic`, 5 workflow patterns,
  typed agents, a supervisor with an LLM-based planner."
- "My agents are declarative interfaces. The framework wires them into
  a graph. I don't manage state — the `AgenticScope` does."

**⚠ Known limitation I didn't fix:** the sub-agents **don't** call MCP
tools. `@McpToolBox` is a Quarkus-side annotation that works on
`@RegisterAiService` interfaces, not on plain LC4J `AgenticServices`
agents. If someone asks: *"For the demo we kept MCP on the monolithic
`/chat`. Wiring MCP into sub-agents is a few lines — hosting it as a
talk punchline is not."*

**Demo:**
1. `curl -X POST -H 'Content-Type: text/plain' \
     -d 'what time is it in Berlin?' http://localhost:8080/agent/chat`
   → supervisor routes to `calendarSpecialist` → `getCurrentDateTime` fires
2. `curl -X POST -H 'Content-Type: text/plain' \
     -d 'tell me about devoxx cfp' http://localhost:8080/agent/chat`
   → supervisor routes to `eventsSpecialist`
3. **Watch the Quarkus logs** — you'll see the supervisor planning
   ("I'll ask the calendarSpecialist...") then the sub-agent responding.

**Optional**: switch the TUI's default URL to `/agent/chat` for a real
side-by-side with `/chat`.

---

## Runtime risks (Round 4)

**`@Produces SupervisorAgent` injecting `ChatModel`**: the producer
needs Quarkus to expose a `ChatModel` CDI bean. Quarkus LC4J *does*
create one from the Anthropic config, but scope/qualifiers vary by
version. If Quarkus throws "Unsatisfied dependency for ChatModel" on
startup, try:

```java
@Inject
@io.quarkiverse.langchain4j.ModelName("default")
ChatModel chatModel;
```

…or build a new `AnthropicChatModel` directly from
`quarkus.langchain4j.anthropic.*` values via `@ConfigProperty`.

---

## Demo failure playbook

**TUI renders garbage / Enter dies** → `./tui.sh`, not `./gradlew runTui`.

**Memory not persisting** → `ls -la ~/.jclaw/memory/`; confirm `sessionId`.

**MCP server unreachable** → Agent falls back to LLM knowledge. Still on-topic.

**Supervisor picks the wrong sub-agent** → That's the demo. *"LLM routing
is probabilistic. This is why you test."*

---

## Quick command cheat sheet (Rounds 1–4)

```bash
# Run the agent
./gradlew quarkusDev

# Run the TUI
./tui.sh                                        # default session
./tui.sh http://localhost:8080 vik              # named session

# Hit the monolithic agent
curl -X POST -H 'Content-Type: text/plain' \
     -d 'Hello!' 'http://localhost:8080/chat?sessionId=vik'

# Hit the agentic supervisor
curl -X POST -H 'Content-Type: text/plain' \
     -d 'What time is it in Berlin?' 'http://localhost:8080/agent/chat'

# Clear a session's memory
rm ~/.jclaw/memory/vik.json

# Probe the hosted MCP server
curl -sS -X POST https://developer-events-mcp-54127830651.europe-west2.run.app/mcp \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"0.0.1"}}}'
```

---

## Talking points cheat sheet (so far)

| Round | LC4J one-liner | Spring AI contrast |
|---|---|---|
| 1 | "Interface is the implementation" | `ChatClient.builder()` fluent |
| 2 | Declarative memory injection | `ChatMemoryAdvisor` middleware |
| 3 | `@Tool` + `@McpToolBox` | `@Tool` + MCP Boot Starter |
| 4 | Dedicated `langchain4j-agentic` module | Composed advisor chain |
