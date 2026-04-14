package com.codepocalypse.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

public interface GeneralAssistant {

    @Agent(
            name = "generalAssistant",
            description = "Handles anything that isn't conference/CFP related " +
                    "and doesn't need time/timezone lookups. Default fallback agent."
    )
    @SystemMessage("""
            You are JClaw, the Java cousin of NanoClaw.
            Proud Java heritage, snappy and a bit sarcastic, but actually helpful.
            Keep it short. This is the general fallback agent in an agentic system —
            the supervisor only routes here when no specialist fits.
            """)
    String handle(@V("request") String request);
}
