# JClaw — Quarkus + LangChain4j

JClaw is the Java cousin of NanoClaw: a personal AI assistant built with
**Quarkus** + **LangChain4j**, backed by **Claude** (Anthropic), driven
from a **TUI4J** chat client.

This repo is the LangChain4j half of the conference talk
*Codepocalypse Now: LangChain4j vs Spring AI*. The 6 rounds are laid out
as **stacked branches** — each round builds on the previous one, so
checking out `round-N` gives you everything through round N.

## Stack

| Layer       | Version                  |
|-------------|--------------------------|
| JDK         | 21                       |
| Build       | Gradle 8.x (wrapper)     |
| Runtime     | Quarkus 3.17             |
| LangChain4j | quarkus-langchain4j 0.25 |
| LLM         | Claude Sonnet 4          |
| TUI client  | TUI4J 0.3.3              |

## Prerequisites

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## Run

Start the Quarkus backend (dev mode, hot reload):

```bash
./gradlew quarkusDev
```

In a second terminal, launch the TUI chat client:

```bash
./tui.sh                 # session id defaults to "demo"
./tui.sh http://localhost:8080 vik   # custom session id
```

In the TUI: `/help` for commands, `/session <id>` to switch sessions,
`/mode agent` (round 4+) to route through the agentic supervisor,
`/forget` to wipe the current session's memory, `/quit` to exit.

## Branches — one per round

Each round is its own branch. They're **stacked**: `round-N` contains
everything from rounds 1 through N.

| Branch    | Round | What's new                                                                              |
|-----------|-------|-----------------------------------------------------------------------------------------|
| `main`    | 1     | Basic `@RegisterAiService` agent, TUI chat client, retry on 429/529                     |
| `round-2` | 2     | Persistent chat memory per session (`FileChatMemoryStore` → `~/.jclaw/memory/`)         |
| `round-3` | 3     | MCP tool provider (raw `langchain4j-mcp` client) for conference/CFP lookups             |
| `round-4` | 4     | Agentic supervisor — 3 specialist sub-agents routed by an LLM planner                   |
| `round-5` | 5     | Input/output guardrails — prompt-injection block, response sanity check                 |
| `round-6` | 6     | Agent-tree observability — `AgentListener` trace recorder + `/trace` TUI command        |

## Switching branches live

```bash
git checkout round-2     # or round-3, round-4, round-5, round-6
./gradlew quarkusDev     # restart dev mode so it picks up the new classes
```

> **Tip:** Quarkus dev mode does hot reload within a branch, but
> switching branches changes class graphs in ways hot reload can't
> always handle cleanly. **Restart dev mode after every
> `git checkout`** for a reliable demo.

After switching, your TUI client can stay running — just re-issue a
message and it will hit the new backend. If you want to clear state,
delete the memory directory:

```bash
rm -rf ~/.jclaw/memory
```

## Memory layout

- **Rounds 2–6, `/chat` path**: one file per session id —
  `~/.jclaw/memory/<sessionId>.json` (e.g. `demo.json`, `vik.json`).
- **Rounds 4–6, `/agent/chat` path**: one file per sub-agent —
  `events.json`, `calendar.json`, `general.json`. Shared across all
  sessions (framework limitation: `SupervisorAgent.invoke(String)`
  has no memoryId parameter).

## More

Speaker notes and the live-demo runbook live in
[`DEMO_SCRIPT.md`](DEMO_SCRIPT.md).
