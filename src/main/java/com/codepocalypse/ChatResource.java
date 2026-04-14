package com.codepocalypse;

import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/chat")
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    @Inject
    JClawAgent agent;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("sessionId") String sessionId, String message) {
        String id = (sessionId == null || sessionId.isBlank()) ? "demo" : sessionId;
        try {
            return agent.chat(id, message);
        } catch (InputGuardrailException e) {
            LOG.warnf("[guardrail] input blocked for session=%s: %s", id, e.getMessage());
            return "🛡️  Guardrail tripped on your input: " + e.getMessage()
                    + "\n(Try rephrasing — I won't act on prompt-injection or destructive requests.)";
        } catch (OutputGuardrailException e) {
            LOG.warnf("[guardrail] output blocked for session=%s: %s", id, e.getMessage());
            return "🛡️  Guardrail tripped on my response: " + e.getMessage()
                    + "\n(My draft reply failed a safety check. Try asking again.)";
        }
    }
}
