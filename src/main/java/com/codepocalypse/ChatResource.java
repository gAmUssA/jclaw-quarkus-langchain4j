package com.codepocalypse;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/chat")
public class ChatResource {

    @Inject
    JClawAgent agent;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("sessionId") String sessionId, String message) {
        String id = (sessionId == null || sessionId.isBlank()) ? "demo" : sessionId;
        return agent.chat(id, message);
    }
}
