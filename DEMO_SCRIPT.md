# Codepocalypse Now — LangChain4j Side Demo Script

Speaker notes + live-demo runbook for the Java/LangChain4j half of
**Codepocalypse Now: LangChain4j vs Spring AI**.

> Paired with Baruch's Spring AI track. Both sides build the same 6
> features against Claude. This file only covers the LangChain4j side.
>
> **You are on branch `round-2` (Rounds 1–2).** This script covers
> Rounds 1 and 2. Check out `round-3`..`round-6` to see later rounds.

---

## Stack (Round 2)

| Layer       | Version                   | Notes                      |
|-------------|---------------------------|----------------------------|
| JDK         | 21                        | `sdkman` default           |
| Build       | Gradle 8.10 (wrapper)     | `./gradlew`                |
| Runtime     | Quarkus 3.17.0            | `quarkus-bom`              |
| LangChain4j | quarkus-langchain4j 0.25.0 |                            |
| LLM         | Claude Sonnet 4           | `claude-sonnet-4-20250514` |
| TUI client  | TUI4J 0.3.3               | Bubble Tea port for Java   |

## Branch layout

Stacked branches — each round builds on the previous. Checking out
`round-N` gives you everything through round N.

```
  round-1 (main)    Basic agent + TUI
* round-2           Persistent memory    (~/.jclaw/memory/<id>.json)
  round-3           (next)
  round-4           (next)
  round-5           (next)
  round-6           (next)
```

Switch rounds live:

```bash
git checkout round-3   # next
./tui.sh               # pick up the new code
```

## Before you go on stage

```bash
# 1. API key
export ANTHROPIC_API_KEY=sk-ant-...

# 2. Start Quarkus on the branch you want to demo (Terminal 1)
git checkout round-2
./gradlew quarkusDev

# 3. Launch the TUI (Terminal 2)
./tui.sh                                   # default session=demo
./tui.sh http://localhost:8080 vik         # named session
```

`./tui.sh` execs `java` directly — never run the TUI through Gradle
JavaExec (stdin piping breaks raw mode → Enter dies + double echo).

---

## Round 1 — Basic agent + TUI

**Branch:** `main` (tag `round-1`)

**Files:**
- `src/main/java/com/codepocalypse/JClawAgent.java` — `@RegisterAiService` interface, `@SystemMessage` persona
- `src/main/java/com/codepocalypse/ChatResource.java` — `POST /chat` text-in/text-out
- `src/main/java/com/codepocalypse/tui/JClawTui.java` — TUI4J Model (Textarea + Viewport)
- `tui.sh` — standalone JVM launcher

**Key LC4J code — the "one interface, zero impl" beat:**

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

**Demo:**
1. `./tui.sh`
2. Type: *"Hi! What are you?"* → JClaw persona answer.

---

## Round 2 — Persistent memory

**Branch:** `round-2`

**New/changed files:**
- `JClawAgent.java` — `chat(@MemoryId String sessionId, @UserMessage String message)`
- `ChatResource.java` — threads `?sessionId` query param
- `src/main/java/com/codepocalypse/memory/FileChatMemoryStore.java` — `@ApplicationScoped ChatMemoryStore`
- `application.properties`:
  ```properties
  quarkus.langchain4j.chat-memory.type=message-window
  quarkus.langchain4j.chat-memory.memory-window.max-messages=20
  ```

**How it works:**
- `FileChatMemoryStore` is the only `ChatMemoryStore` bean in the CDI
  container, so Quarkus LC4J auto-wires it into the default memory
  provider.
- One JSON file per `sessionId` under `~/.jclaw/memory/<id>.json`, using
  LC4J's `ChatMessageSerializer`/`Deserializer`.
- 20-message sliding window (`message-window` provider).

**Talking points:**
- "In LangChain4j, memory isn't an advisor wrapping everything — it's
  just... declared. You tell it the ID, the framework finds the store."
- Baruch's equivalent: `MessageWindowChatMemoryAdvisor`. Different
  philosophy (middleware advisor chain vs declarative injection).

**Demo:**
1. Start a TUI session: `./tui.sh http://localhost:8080 vik`
2. *"My name is Viktor and I love Quarkus."*
3. Quit TUI (`Esc`).
4. Relaunch: `./tui.sh http://localhost:8080 vik`
5. *"What's my name?"* → should return "Viktor" from disk.
6. Show the file: `cat ~/.jclaw/memory/vik.json | jq`

**Contrast shot** (optional): relaunch with a different session id:
`./tui.sh http://localhost:8080 baruch` → "What's my name?" → no memory.

---

## Demo failure playbook

**TUI renders garbage / Enter dies** → You ran it through Gradle by
accident. Use `./tui.sh`, never `./gradlew runTui`.

**`./tui.sh` exits immediately with class-not-found** → Someone
pruned `build/`. Run `./gradlew classes tuiClasspath` manually once.

**Memory not persisting** → Check `~/.jclaw/memory/` exists and the
process has write access. `ls -la ~/.jclaw/memory/`.

**Memory "leaking" between sessions** → Different `sessionId`s = different
files. Confirm the TUI is actually passing the id you think it is:
server log shows `Loaded N messages for id=<X>`.

---

## Quick command cheat sheet (Rounds 1–2)

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

# Inspect memory file
cat ~/.jclaw/memory/vik.json | jq
```

---

## Talking points cheat sheet (so far)

| Round | LC4J one-liner | Spring AI contrast |
|---|---|---|
| 1 | "Interface is the implementation" | `ChatClient.builder()` fluent |
| 2 | Declarative memory injection | `ChatMemoryAdvisor` middleware |
