package com.codepocalypse.agentic;

import com.codepocalypse.observability.AgentTraceRecorder;
import com.codepocalypse.tools.LocalTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/**
 * Builds the agentic supervisor from three specialist sub-agents.
 * Uses Quarkus's injected {@link ChatModel} so the same Claude credentials
 * from application.properties back the whole system.
 */
@ApplicationScoped
public class SupervisorProducer {

    private static final Logger LOG = Logger.getLogger(SupervisorProducer.class);

    @Inject
    ChatModel chatModel;

    @Inject
    LocalTools localTools;

    @Inject
    AgentTraceRecorder traceRecorder;

    @Inject
    ToolProvider mcpToolProvider;

    /**
     * Qualified with {@code @Named("jclawSupervisor")} to avoid colliding with
     * the synthetic {@code SupervisorAgent} bean that the
     * {@code quarkus-langchain4j-agentic} extension auto-generates at build time.
     */
    @Produces
    @ApplicationScoped
    @Named("jclawSupervisor")
    public SupervisorAgent supervisor() {
        LOG.info("Building agentic supervisor with 3 specialist sub-agents (events -> MCP) + trace listener");

        // Events specialist gets the same MCP tool provider that the monolithic
        // /chat agent uses — so /mode agent can also hit the CFP MCP server.
        EventsSpecialist events = AgenticServices.agentBuilder(EventsSpecialist.class)
                .chatModel(chatModel)
                .toolProvider(mcpToolProvider)
                .build();

        CalendarSpecialist calendar = AgenticServices.agentBuilder(CalendarSpecialist.class)
                .chatModel(chatModel)
                .tools(localTools)
                .build();

        GeneralAssistant general = AgenticServices.agentBuilder(GeneralAssistant.class)
                .chatModel(chatModel)
                .build();

        return AgenticServices.supervisorBuilder()
                .chatModel(chatModel)
                .subAgents(events, calendar, general)
                .listener(traceRecorder)
                .supervisorContext("""
                        You are the supervisor for JClaw, a Java AI assistant.
                        Route each user request to the most appropriate specialist
                        based on the topic. Prefer the eventsSpecialist for anything
                        about conferences, talks, CFPs, or submission deadlines.
                        Prefer the calendarSpecialist for time and timezone questions.
                        Fall back to the generalAssistant only when nothing else fits.
                        Keep the supervisor output concise — just the specialist's answer.
                        """)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .maxAgentsInvocations(3)
                .build();
    }
}
