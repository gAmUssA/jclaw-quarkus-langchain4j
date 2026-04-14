package com.codepocalypse.tui;

import com.williamcallahan.tui4j.compat.bubbles.textarea.Textarea;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseAction;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseButton;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseMessage;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JClawTui implements Model {

    private static final String[] SPINNER = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };
    private static final long TICK_MS = 80;
    private static final int WHEEL_LINES = 3;

    private final String baseUrl;
    private String sessionId;          // mutable — /session <id>
    private String endpoint = "/chat"; // mutable — /mode chat|agent

    private final Textarea textarea;
    private final Viewport viewport;
    private final List<String> messages = new ArrayList<>();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    private final Style userStyle = Style.newStyle().foreground(Color.color("12"));
    private final Style agentStyle = Style.newStyle().foreground(Color.color("10"));
    private final Style dimStyle = Style.newStyle().foreground(Color.color("8"));

    private boolean waiting = false;
    private int spinnerFrame = 0;
    private CompletableFuture<String> pending;

    public JClawTui(String baseUrl, String sessionId) {
        this.baseUrl = baseUrl;
        this.sessionId = sessionId;

        this.textarea = new Textarea();
        textarea.setPlaceholder("Ask your agent...");
        textarea.focus();
        textarea.setPrompt("┃ ");
        textarea.setCharLimit(500);
        textarea.setWidth(70);
        textarea.setHeight(3);
        textarea.setShowLineNumbers(false);

        this.viewport = Viewport.create(70, 20);
        // Plain text here — Style.render() needs the Program's terminal info,
        // which isn't available until after the Program starts.
        viewport.setContent(
                "JClaw ready. Talking to " + baseUrl + " (session: " + sessionId + ")\n" +
                "Type a message and press Enter. Ctrl+C or Esc to quit.");
    }

    @Override
    public Command init() {
        return null;
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        // Mouse wheel -> scroll the viewport. Handled explicitly (not via
        // viewport.update) so behavior is predictable regardless of what the
        // Viewport's internal mouse handling happens to do.
        if (msg instanceof MouseMessage mm
                && mm.getAction() == MouseAction.MouseActionPress) {
            if (mm.getButton() == MouseButton.MouseButtonWheelUp) {
                viewport.scrollUp(WHEEL_LINES);
                return UpdateResult.from(this, null);
            }
            if (mm.getButton() == MouseButton.MouseButtonWheelDown) {
                viewport.scrollDown(WHEEL_LINES);
                return UpdateResult.from(this, null);
            }
        }

        // Intercept Enter / Esc / Ctrl+C BEFORE the textarea sees them — a
        // multi-line textarea would otherwise eat Enter as a newline and swallow it.
        if (msg instanceof KeyPressMessage kpm) {
            String key = kpm.key();

            if ("ctrl+c".equals(key) || "esc".equals(key)) {
                return UpdateResult.from(this, QuitMessage::new);
            }

            if (("enter".equals(key) || "return".equals(key)) && !waiting) {
                String input = textarea.value().trim();
                if (!input.isEmpty()) {
                    textarea.reset();

                    // Slash commands are handled locally — no HTTP, no spinner.
                    if (input.startsWith("/")) {
                        return handleCommand(input);
                    }

                    messages.add(userStyle.render("You: ") + input);
                    refreshViewport();

                    waiting = true;
                    spinnerFrame = 0;
                    pending = CompletableFuture.supplyAsync(() -> callAgentSync(input), io);

                    // Kick off the spinner tick loop — polling pending on each tick.
                    return UpdateResult.from(this, tickCmd());
                }
                // Enter with empty input: swallow it so the textarea doesn't add a newline.
                return UpdateResult.from(this, null);
            }
        }

        // Forward non-submit keys (and non-key messages) to the components.
        textarea.update(msg);
        viewport.update(msg);

        if (msg instanceof TickMessage) {
            if (waiting) {
                if (pending != null && pending.isDone()) {
                    String result;
                    try {
                        result = pending.get();
                    } catch (Exception e) {
                        result = "Error - " + e.getMessage();
                    }
                    messages.add(agentStyle.render("JClaw: ") + result);
                    refreshViewport();
                    waiting = false;
                    pending = null;
                    return UpdateResult.from(this, null);
                }
                spinnerFrame = (spinnerFrame + 1) % SPINNER.length;
                return UpdateResult.from(this, tickCmd());
            }
        }

        return UpdateResult.from(this, null);
    }

    @Override
    public String view() {
        String status;
        if (waiting) {
            status = "  " + dimStyle.render(SPINNER[spinnerFrame] + " Thinking...");
        } else {
            status = "";
        }
        return viewport.view() + "\n" + status + "\n" + textarea.view();
    }

    private void refreshViewport() {
        String content = String.join("\n", messages);
        int w = viewport.getWidth();
        if (w > 0) {
            content = Style.newStyle().width(w).render(content);
        }
        viewport.setContent(content);
        viewport.gotoBottom();
    }

    private Command tickCmd() {
        return () -> {
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new TickMessage();
        };
    }

    private String callAgentSync(String input) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint + "?sessionId=" + sessionId))
                    .header("Content-Type", "text/plain")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(input))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                return "[HTTP " + resp.statusCode() + "] " + resp.body();
            }
            return resp.body();
        } catch (Exception e) {
            return "Error - " + e.getMessage();
        }
    }

    // ---------- slash commands ----------

    private UpdateResult<? extends Model> handleCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        // Echo the command as a dim input line so the audience sees what was typed.
        messages.add(dimStyle.render("> " + input));

        switch (cmd) {
            case "/help", "/?" -> addSystem(helpText());
            case "/clear" -> {
                messages.clear();
                refreshViewport();
                return UpdateResult.from(this, null);
            }
            case "/quit", "/exit" -> {
                return UpdateResult.from(this, QuitMessage::new);
            }
            case "/session" -> {
                if (arg.isEmpty()) {
                    addSystem("session: " + sessionId);
                } else {
                    sessionId = arg;
                    addSystem("session -> " + sessionId);
                }
            }
            case "/mode" -> {
                if ("agent".equalsIgnoreCase(arg)) {
                    endpoint = "/agent/chat";
                    addSystem("mode -> agent  (POST " + endpoint + ")");
                } else if ("chat".equalsIgnoreCase(arg)) {
                    endpoint = "/chat";
                    addSystem("mode -> chat   (POST " + endpoint + ")");
                } else if (arg.isEmpty()) {
                    addSystem("mode: " + ("/chat".equals(endpoint) ? "chat" : "agent")
                            + "  (POST " + endpoint + ")");
                } else {
                    addSystem("usage: /mode [chat|agent]");
                }
            }
            case "/forget" -> {
                Path f = memoryFileFor(sessionId);
                try {
                    if (Files.deleteIfExists(f)) {
                        addSystem("forgot " + f);
                    } else {
                        addSystem("nothing to forget at " + f);
                    }
                } catch (IOException e) {
                    addSystem("forget failed: " + e.getMessage());
                }
            }
            default -> addSystem("unknown command: " + cmd + " — try /help");
        }

        refreshViewport();
        return UpdateResult.from(this, null);
    }

    private void addSystem(String text) {
        for (String line : text.split("\n")) {
            messages.add(dimStyle.render(line));
        }
    }

    private String helpText() {
        return """
                commands:
                  /help              show this
                  /clear             clear the viewport (server memory unaffected)
                  /session [id]      show or set the session id
                  /mode [chat|agent] show or set the target endpoint
                  /forget            delete this session's memory file
                  /quit              exit the TUI (same as Esc)""";
    }

    private Path memoryFileFor(String id) {
        String safe = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Path.of(System.getProperty("user.home"), ".jclaw", "memory", safe + ".json");
    }

    public record TickMessage() implements Message {}

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://localhost:8080";
        String session = args.length > 1 ? args[1] : "demo";
        new Program(new JClawTui(url, session))
                .withAltScreen()
                .withMouseCellMotion()
                .run();
    }
}
