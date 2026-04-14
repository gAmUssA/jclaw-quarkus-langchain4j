# Codepocalypse Now — LangChain4j Side Demo Script

Speaker notes + live-demo runbook for the Java/LangChain4j half of
**Codepocalypse Now: LangChain4j vs Spring AI**.

> Paired with Baruch's Spring AI track. Both sides build the same 6
> features against Claude. This file only covers the LangChain4j side.
>
> **You are on branch `round-3` (Rounds 1–3).** This script covers
> Rounds 1 through 3. Check out `round-4`..`round-6` to see later rounds.

---

## Stack (Round 3)

| Layer       | Version                                                                                   | Notes                      |
|-------------|-------------------------------------------------------------------------------------------|----------------------------|
| JDK         | 21                                                                                        | `sdkman` default           |
| Build       | Gradle 8.10 (wrapper)                                                                     | `./gradlew`                |
| Runtime     | Quarkus 3.34.3                                                                            | `quarkus-bom`              |
| LangChain4j | quarkus-langchain4j BOM 1.8.4                                                             | pulls `langchain4j:1.12.2` |
| LLM         | Claude Sonnet 4                                                                           | `claude-sonnet-4-20250514` |
| MCP server  | [developer-events MCP](https://developer-events-mcp-54127830651.europe-west2.run.app/mcp) | Streamable HTTP at `/mcp`  |
| TUI client  | TUI4J 0.3.3                                                                               | Bubble Tea port for Java   |

## Branch layout

Stacked branches — each round builds on the previous. Checking out
`round-N` gives you everything through round N.

```
  round-1 (main)    Basic agent + TUI
  round-2           Persistent memory
* round-3           Tools + MCP          (hosted streamable-http at /mcp)
  round-4           (next)
  round-5           (next)
  round-6           (next)
```

Switch rounds live:

```bash
git checkout round-4   # next
./tui.sh
```

## Before you go on stage

```bash
# 1. API key
export ANTHROPIC_API_KEY=sk-ant-...

# 2. Sanity-check the hosted MCP server is alive (optional but smart)
curl -sS -X POST https://developer-events-mcp-54127830651.europe-west2.run.app/mcp \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"0.0.1"}}}'

# 3. Start Quarkus (Terminal 1)
git checkout round-3
./gradlew quarkusDev

# 4. Launch the TUI (Terminal 2)
./tui.sh                                   # default session=demo
./tui.sh http://localhost:8080 vik         # named session
```

`./tui.sh` execs `java` directly — never run the TUI through Gradle
JavaExec (stdin piping breaks raw mode → Enter dies + double echo).

---

## Round 1 — Basic agent + TUI

**Branch:** `main` (tag `round-1`)

```java
@RegisterAiService
public interface JClawAgent {
    @SystemMessage("You are JClaw, the Java cousin of NanoClaw...")
    String chat(String message);
}
```

**Talking points:**
- "I didn't write an implementation — the framework IS the implementation."
- Contrast with Baruch's `ChatClient.builder()` fluent API.

**Demo:** `./tui.sh` → *"Hi! What are you?"* → persona answer.

---

## Round 2 — Persistent memory

**Branch:** `round-2`

**What happened:**
- `JClawAgent.chat(@MemoryId String sessionId, @UserMessage String message)`
- `FileChatMemoryStore` (@ApplicationScoped) — JSON files under
  `~/.jclaw/memory/<id>.json`, serialized via LC4J's `ChatMessageSerializer`.
- `application.properties`:
  `quarkus.langchain4j.chat-memory.type=message-window` + `max-messages=20`

**Talking points:**
- "Memory isn't an advisor wrapping everything — it's just declared.
  You tell it the ID, the framework finds the store."
- Baruch: `MessageWindowChatMemoryAdvisor` (middleware advisor chain).

**Demo:**
1. `./tui.sh http://localhost:8080 vik`
2. *"My name is Viktor."* → ack.
3. Quit, relaunch, *"What's my name?"* → *"Viktor"* from disk.
4. `cat ~/.jclaw/memory/vik.json | jq`

---

## Round 3 — Tools + MCP

**Branch:** `round-3`

**⚠ BIG upgrade on this round** — Quarkus LC4J `0.25.0` shipped an
ancient `langchain4j-mcp:1.0.0-beta1` that does NOT have
`StreamableHttpMcpTransport`. The hosted server speaks
streamable-HTTP with `Mcp-Session-Id` headers. I bumped:

- `quarkusPlatformVersion`: `3.17.0` → `3.34.3`
- `quarkusLangchain4jVersion`: `0.25.0` → `1.8.4`

That pulls `langchain4j:1.12.2` + `langchain4j-mcp:1.12.2-beta22` and
enables `transport-type=streamable-http`. Rounds 1–2 APIs unchanged.

**If you mention versions on stage:** *"The hosted MCP server uses the
newer streamable-HTTP spec — session ID in a header. We needed a newer
MCP client to reach it, so we're on Quarkus LangChain4j 1.8.4."*

**New/changed files:**
- `tools/LocalTools.java` — `@ApplicationScoped` with two `@Tool`
  methods: `getCurrentDateTime`, `getCurrentTimezone`
- `JClawAgent.java`:
  ```java
  @RegisterAiService(tools = LocalTools.class)
  public interface JClawAgent {
      @McpToolBox("events")
      String chat(@MemoryId String sessionId, @UserMessage String message);
  }
  ```
- `application.properties`:
  ```properties
  quarkus.langchain4j.mcp.events.transport-type=streamable-http
  quarkus.langchain4j.mcp.events.url=https://developer-events-mcp-54127830651.europe-west2.run.app/mcp
  quarkus.langchain4j.mcp.events.log-requests=true
  quarkus.langchain4j.mcp.events.log-responses=true
  ```

**MCP tools exposed by the server (verified via JSON-RPC probe):**
- `list_open_cfps`
- `find_closing_cfps`
- `search_cfps_by_keyword`
- `search_cfps_by_location`

**Talking points:**
- "Two tool sources, one annotation each. `@Tool` for local methods,
  `@McpToolBox` for every tool exposed by the MCP server."
- "The MCP server is a black box to JClaw — it just discovers whatever
  tools the server advertises. Add a tool server-side, JClaw picks it
  up on the next restart."
- Contrast with Baruch: Spring AI MCP Boot Starter auto-discovery. Both
  frameworks converged on the same abstraction; the plumbing differs.

**Demo:**
1. *"What time is it?"* → local `getCurrentDateTime` tool fires
   (watch the server log: `[tool] getCurrentDateTime -> ...`).
2. *"Find me Java conferences with open CFPs."* → MCP
   `search_cfps_by_keyword` fires against the hosted server.
3. *"Which ones close this month?"* → `find_closing_cfps` follow-up.

**Fallback if MCP is flaky on stage:** the local tools still work
without MCP. Worst case the model answers from general knowledge —
still on-topic. *"LangChain4j happily degrades — so does my talk."*

---

## Demo failure playbook

**TUI renders garbage / Enter dies** → `./tui.sh`, not `./gradlew runTui`.

**`./tui.sh` class-not-found** → `./gradlew classes tuiClasspath` once.

**Memory not persisting** → `ls -la ~/.jclaw/memory/`; confirm the TUI
passes the session id you think it does (`Loaded N messages for id=<X>`
in logs).

**MCP server unreachable** → Agent falls back to LLM knowledge. On-topic,
just not using tools.

**Local tool never fires** → Restate the question to be unambiguous
about needing "current time" or "right now".

---

## Quick command cheat sheet (Rounds 1–3)

```bash
# Run the agent
./gradlew quarkusDev

# Run the TUI
./tui.sh                                        # default session
./tui.sh http://localhost:8080 vik              # named session

# Hit the agent directly
curl -X POST -H 'Content-Type: text/plain' \
     -d 'Hello!' 'http://localhost:8080/chat?sessionId=vik'

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
