package com.codepocalypse.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

/**
 * Sub-agent focused on developer conferences and CFPs.
 * Built programmatically via AgenticServices — not a CDI @RegisterAiService.
 */
public interface EventsSpecialist {

    @Agent(
            name = "eventsSpecialist",
            description = "Answers questions about developer conferences, meetups, " +
                    "and Call for Papers (CFPs). Use this agent when the user asks " +
                    "about conferences, CFP deadlines, where to submit talks, etc."
    )
    @SystemMessage("""
            You are the Events specialist inside JClaw, a Java AI assistant.
            Your job is to help with developer conferences and CFPs.
            Be concise and list only the facts that matter (conference name, link, deadline).
            If you don't know something, say so — don't invent conferences.
            """)
    String handle(@V("request") String request);
}
