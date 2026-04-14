# Codepocalypse Now — LangChain4j Side Demo Script

Speaker notes + live-demo runbook for the Java/LangChain4j half of
**Codepocalypse Now: LangChain4j vs Spring AI**.

> Paired with Baruch's Spring AI track. Both sides build the same 6
> features against Claude. This file only covers the LangChain4j side.
>
> **You are on branch `main` (Round 1).** This script only covers
> Round 1. Check out `round-2`..`round-6` to see later rounds.

---

## Stack (Round 1)

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
  round-1 (main)  Basic agent + TUI
  round-2         (next)
  round-3         (next)
  round-4         (next)
  round-5         (next)
  round-6         (next)
```

Switch rounds live:

```bash
git checkout round-2   # or round-3, round-4, ...
./tui.sh               # pick up the new code
```

## Before you go on stage

```bash
# 1. API key
export ANTHROPIC_API_KEY=sk-ant-...

# 2. Start Quarkus on the branch you want to demo (Terminal 1)
git checkout main              # Round 1
./gradlew quarkusDev

# 3. Launch the TUI (Terminal 2)
./tui.sh                       # defaults: localhost:8080 / session=demo
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
- Point out that everything else (HTTP, JSON, retries) is Quarkus — not LC4J.

**Demo:**
1. `./gradlew quarkusDev` in one terminal.
2. `./tui.sh` in another.
3. Type: *"Hi! What are you?"*
4. Show the JClaw persona answer.
5. Optional follow-up: *"In one sentence, who built you and with what framework?"*
   → shows the `@SystemMessage` leaking through.

**Fallback if Claude is flaky:** nothing cleaner than the error from
`/chat` — it'll show in the TUI's viewport as `Error - ...`. Laugh
it off: *"Even 3 billion devices running Java can't save us from
rate limits."*

---

## Demo failure playbook (Round 1)

**TUI renders garbage / Enter dies** → You ran it through Gradle by
accident. Use `./tui.sh`, never `./gradlew runTui` (the task is gone
anyway — the comment in `build.gradle` explains why).

**`./tui.sh` exits immediately with class-not-found** → Someone
pruned `build/`. Run `./gradlew classes tuiClasspath` manually once.

**`ANTHROPIC_API_KEY` not set** → Quarkus startup fails with a clear
error. Export it, restart `quarkusDev`.

---

## Quick command cheat sheet (Round 1)

```bash
# Run the agent
./gradlew quarkusDev

# Run the TUI
./tui.sh

# Hit the agent directly with curl
curl -X POST -H 'Content-Type: text/plain' \
     -d 'Hello!' http://localhost:8080/chat
```

---

## Talking points cheat sheet

| Round | LC4J one-liner | Spring AI contrast |
|---|---|---|
| 1 | "Interface is the implementation" | `ChatClient.builder()` fluent |
