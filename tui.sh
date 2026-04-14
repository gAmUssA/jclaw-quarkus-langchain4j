#!/usr/bin/env bash
#
# Run the JClaw TUI chat client as a standalone JVM process.
#
# Why not `./gradlew runTui`?
#   Gradle JavaExec pipes stdin through its own streams, so the JVM never
#   sees a real TTY. TUI4J then can't enable raw mode: Enter is swallowed
#   by the terminal and typed characters get double-echoed. Running java
#   directly from this shell via `exec` hands the TTY to the JVM cleanly.
#
# Usage:
#   ./tui.sh                                    # default: http://localhost:8080 / session=demo
#   ./tui.sh http://localhost:8080 vik          # custom base URL / session id
#

set -euo pipefail

cd "$(dirname "$0")"

# Build classes and emit the runtime classpath file. Fast no-op when up-to-date.
./gradlew --quiet --console=plain tuiClasspath

CP_FILE="build/tui-classpath.txt"
if [ ! -s "$CP_FILE" ]; then
    echo "tui.sh: $CP_FILE missing or empty — gradle tuiClasspath failed" >&2
    exit 1
fi
CP="$(cat "$CP_FILE")"

BASE_URL="${1:-http://localhost:8080}"
SESSION_ID="${2:-demo}"

# exec so the java process replaces this shell and inherits the TTY directly.
exec java \
    -Djava.util.logging.manager=java.util.logging.LogManager \
    -cp "$CP" \
    com.codepocalypse.tui.JClawTui \
    "$BASE_URL" \
    "$SESSION_ID"
