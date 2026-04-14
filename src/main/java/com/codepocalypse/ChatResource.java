package com.codepocalypse;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.Locale;

@Path("/chat")
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 500;

    @Inject
    JClawAgent agent;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        RuntimeException lastError = null;
        long delayMs = BASE_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return agent.chat(message);
            } catch (RuntimeException e) {
                lastError = e;
                if (!isRetriable(e) || attempt == MAX_ATTEMPTS) {
                    LOG.errorf(e, "Agent call failed on attempt %d/%d — giving up",
                            attempt, MAX_ATTEMPTS);
                    throw e;
                }
                LOG.warnf("Agent call attempt %d/%d failed (%s) — retrying in %d ms",
                        attempt, MAX_ATTEMPTS, rootMessage(e), delayMs);
                sleep(delayMs);
                delayMs *= 2;
            }
        }
        // Unreachable.
        throw lastError;
    }

    /**
     * Retry on Anthropic "Overloaded" (529), rate-limit (429), timeouts,
     * connection issues. Everything else fails fast.
     */
    private static boolean isRetriable(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("529")
                        || lower.contains("overloaded")
                        || lower.contains("429")
                        || lower.contains("rate limit")
                        || lower.contains("timeout")
                        || lower.contains("timed out")
                        || lower.contains("connection reset")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        String m = cur.getMessage();
        return m == null ? cur.getClass().getSimpleName() : m;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ie);
        }
    }
}
