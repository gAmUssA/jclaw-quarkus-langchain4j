package com.codepocalypse.agentic;

import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Round 4 endpoint: the agentic supervisor.
 *
 * <p>Contrast with {@code /chat} (ChatResource) which uses a single
 * {@code @RegisterAiService} with memory + MCP + local tools.
 * Here the work is split across three specialist sub-agents, and an
 * LLM-driven supervisor routes each message to the right one.
 */
@Path("/agent/chat")
public class AgenticResource {

    @Inject
    SupervisorAgent supervisor;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("sessionId") String sessionId, String message) {
        // The supervisor has its own agentic scope — sessionId is accepted for
        // API parity with /chat but currently unused here.
        return supervisor.invoke(message);
    }
}
