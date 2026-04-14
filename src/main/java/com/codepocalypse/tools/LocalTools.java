package com.codepocalypse.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Local, process-bound tools that don't need an MCP server.
 * These live right inside JClaw so the LLM can call them synchronously.
 */
@ApplicationScoped
public class LocalTools {

    private static final Logger LOG = Logger.getLogger(LocalTools.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    @Tool("Get the current date and time in the user's local timezone")
    public String getCurrentDateTime() {
        String now = LocalDateTime.now().atZone(ZoneId.systemDefault()).format(FMT);
        LOG.infof("[tool] getCurrentDateTime -> %s", now);
        return now;
    }

    @Tool("Get the user's current timezone id (e.g. Europe/Berlin)")
    public String getCurrentTimezone() {
        String tz = ZoneId.systemDefault().getId();
        LOG.infof("[tool] getCurrentTimezone -> %s", tz);
        return tz;
    }
}
