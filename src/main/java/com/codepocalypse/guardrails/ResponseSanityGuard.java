package com.codepocalypse.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Locale;

/**
 * Output guardrail: basic sanity on the agent's response.
 * <p>Uses {@link #retry(String)} for recoverable issues so the model gets
 * another shot, {@link #fatal(String)} for things that should never happen.
 */
@ApplicationScoped
public class ResponseSanityGuard implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(ResponseSanityGuard.class);

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 8000;

    @Override
    public OutputGuardrailResult validate(AiMessage response) {
        String text = response.text();

        if (text == null || text.isBlank()) {
            LOG.warn("[guardrail] empty response — requesting retry");
            return retry("Empty response from model");
        }

        if (text.length() < MIN_LENGTH) {
            LOG.warnf("[guardrail] response too short (%d chars) — retry", text.length());
            return retry("Response too short");
        }

        if (text.length() > MAX_LENGTH) {
            LOG.warnf("[guardrail] response too long (%d chars) — retry with shorter ask",
                    text.length());
            return retry("Response exceeded " + MAX_LENGTH + " chars; condense");
        }

        // Catch obvious leaks of the system prompt (not foolproof, just signal).
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("you are jclaw, the java cousin of nanoclaw")) {
            LOG.warn("[guardrail] response leaked system prompt — fatal");
            return fatal("Response leaked the system prompt");
        }

        LOG.debugf("[guardrail] output OK (%d chars)", text.length());
        return success();
    }
}
