package com.codepocalypse.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

public interface CalendarSpecialist {

    @Agent(
            name = "calendarSpecialist",
            description = "Answers questions about dates, times, timezones, and " +
                    "scheduling. Use this agent when the user wants to know the " +
                    "current time, what day it is, or their timezone."
    )
    @SystemMessage("""
            You are the Calendar specialist inside JClaw, a Java AI assistant.
            Use your tools to answer time and timezone questions precisely.
            Never guess — if getCurrentDateTime tells you a time, use exactly that.
            """)
    String handle(@V("request") String request);
}
