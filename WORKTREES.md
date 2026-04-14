# Git Worktrees — running every round simultaneously

Switching branches in a single checkout forces a full Quarkus restart and
often serves stale classes until you notice. Worktrees give each round
its own directory, its own `build/`, and its own dev mode on its own
port — so you can have all six rounds running at once and jump between
them in seconds.

## Layout

```
~/Library/.../Codepocalypse Now/codepocalypse-langchain4j   ← main   (port 8080)
~/jclaw-worktrees/round-2                                    ← round-2 (port 8082)
~/jclaw-worktrees/round-3                                    ← round-3 (port 8083)
~/jclaw-worktrees/round-4                                    ← round-4 (port 8084)
~/jclaw-worktrees/round-5                                    ← round-5 (port 8085)
~/jclaw-worktrees/round-6                                    ← round-6 (port 8086)
```

Worktrees live **outside** Google Drive on purpose — `build/` and
`.gradle/` write churn should not be cloud-synced.

## Create the worktrees (one-time)

```bash
cd /path/to/codepocalypse-langchain4j
mkdir -p ~/jclaw-worktrees
for r in 2 3 4 5 6; do
  git worktree add ~/jclaw-worktrees/round-$r round-$r
done
git worktree list
```

## Run a round

Each round uses a different port so they can all run at once:

```bash
# Round 4
cd ~/jclaw-worktrees/round-4
./gradlew quarkusDev -Dquarkus.http.port=8084

# Round 5 (new terminal tab)
cd ~/jclaw-worktrees/round-5
./gradlew quarkusDev -Dquarkus.http.port=8085
```

And point the TUI at whichever round you want:

```bash
./tui.sh http://localhost:8084 vik   # round 4
./tui.sh http://localhost:8085 vik   # round 5
```

## Things to know

- **A branch can only be checked out in one worktree at a time.** The
  main checkout currently has `main`, so `round-2..round-6` are each
  locked to their worktree. Switching branches inside the main checkout
  to one of those will fail — just `cd` to the worktree instead.

- **Shared state across worktrees:**
  - `~/.jclaw/memory/` — chat memory files. Round-2 writing `vik.json`
    is visible to round-3..round-6 too. This is usually what you want
    (start a session in one round, continue it in another), but if you
    want isolation between rounds, `rm -rf ~/.jclaw/memory` between runs.
  - `git stash` — stashes are a single stack per repo, not per worktree.
    `git stash pop` in any worktree pops the top of the shared stack.

- **Per-worktree state:**
  - `build/`, `.gradle/`, `.quarkus/` — each worktree has its own, so
    rebuilds in round-4 don't invalidate round-5's cache.

- **Removing a worktree:** use `git worktree remove ~/jclaw-worktrees/round-N`.
  Don't just `rm -rf` the directory — that leaks metadata. If you do,
  `git worktree prune` cleans up.

## Quick cheatsheet

```bash
git worktree list                                     # show all
git worktree add ~/jclaw-worktrees/round-N round-N    # add one
git worktree remove ~/jclaw-worktrees/round-N         # remove one
git worktree prune                                    # clean up stale entries
```
