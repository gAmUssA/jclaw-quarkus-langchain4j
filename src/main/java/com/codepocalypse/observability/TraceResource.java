package com.codepocalypse.observability;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exposes the agent trace recorded by {@link AgentTraceRecorder}:
 * <ul>
 *   <li>{@code GET  /agent/trace}        — raw JSON for dashboards / the TUI</li>
 *   <li>{@code GET  /agent/trace/report} — human-readable HTML topology report</li>
 *   <li>{@code DELETE /agent/trace}      — clear the buffer</li>
 * </ul>
 */
@Path("/agent/trace")
public class TraceResource {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_INSTANT;

    @Inject
    AgentTraceRecorder recorder;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<AgentTraceRecorder.TraceEvent> trace() {
        return recorder.snapshot();
    }

    @GET
    @Path("/report")
    @Produces(MediaType.TEXT_HTML)
    public String report() {
        List<AgentTraceRecorder.TraceEvent> events = recorder.snapshot();

        StringBuilder html = new StringBuilder(4096);
        html.append("""
                <!doctype html>
                <html><head><meta charset="utf-8">
                <title>JClaw agent trace</title>
                <style>
                  body { font-family: -apple-system, system-ui, sans-serif;
                         background: #1e1e2e; color: #cdd6f4; margin: 2em; }
                  h1 { color: #f38ba8; }
                  table { border-collapse: collapse; width: 100%; font-size: 13px; }
                  th, td { padding: 6px 10px; text-align: left;
                           border-bottom: 1px solid #313244; }
                  th { color: #89b4fa; position: sticky; top: 0; background: #1e1e2e; }
                  tr:hover { background: #313244; }
                  .AGENT_BEFORE { color: #a6e3a1; }
                  .AGENT_AFTER  { color: #94e2d5; }
                  .AGENT_ERROR  { color: #f38ba8; }
                  .TOOL_BEFORE  { color: #f9e2af; }
                  .TOOL_AFTER   { color: #f2cdcd; }
                  .muted { color: #6c7086; }
                </style>
                </head><body>
                <h1>JClaw agent trace</h1>
                """);
        html.append("<p class=\"muted\">").append(events.size()).append(" events</p>\n");
        html.append("""
                <table>
                  <thead><tr>
                    <th>#</th><th>at</th><th>type</th>
                    <th>agent</th><th>tool</th><th>dur (ms)</th><th>error</th>
                  </tr></thead>
                  <tbody>
                """);
        for (var e : events) {
            html.append("<tr>")
                .append("<td>").append(e.seq()).append("</td>")
                .append("<td>").append(ISO.format(e.at())).append("</td>")
                .append("<td class=\"").append(e.type().name()).append("\">")
                    .append(e.type()).append("</td>")
                .append("<td>").append(safe(e.agentName())).append("</td>")
                .append("<td>").append(safe(e.toolName())).append("</td>")
                .append("<td>").append(e.durationMs() == null ? "" : e.durationMs()).append("</td>")
                .append("<td>").append(safe(e.error())).append("</td>")
                .append("</tr>\n");
        }
        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    @DELETE
    public void clear() {
        recorder.clear();
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
