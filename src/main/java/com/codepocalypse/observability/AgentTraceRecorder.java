package com.codepocalypse.observability;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Records every agent invocation + tool execution routed through the
 * supervisor into an in-memory, bounded ring buffer. Exposed as a bean
 * so {@link com.codepocalypse.observability.TraceResource} can render it.
 *
 * <p>This is LangChain4j's "purpose-built agent observability" — compare
 * with Spring AI's Micrometer-based Actuator story. Same goal
 * (know what the agent did), different granularity.
 */
@ApplicationScoped
public class AgentTraceRecorder implements AgentListener {

    private static final Logger LOG = Logger.getLogger(AgentTraceRecorder.class);
    private static final int MAX_EVENTS = 500;

    private final ConcurrentLinkedDeque<TraceEvent> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Long> startNanos = new ConcurrentHashMap<>();

    public enum EventType { AGENT_BEFORE, AGENT_AFTER, AGENT_ERROR, TOOL_BEFORE, TOOL_AFTER }

    public record TraceEvent(
            long seq,
            Instant at,
            EventType type,
            String agentName,
            String toolName,
            Long durationMs,
            String error
    ) {}

    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        String key = request.agentId();
        startNanos.put(key, System.nanoTime());
        record(new TraceEvent(
                SEQ.incrementAndGet(), Instant.now(),
                EventType.AGENT_BEFORE, request.agentName(), null, null, null));
        LOG.infof("[trace] -> agent %s", request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Long started = startNanos.remove(response.agentId());
        Long ms = started != null ? (System.nanoTime() - started) / 1_000_000 : null;
        record(new TraceEvent(
                SEQ.incrementAndGet(), Instant.now(),
                EventType.AGENT_AFTER, response.agentName(), null, ms, null));
        LOG.infof("[trace] <- agent %s (%s ms)", response.agentName(), ms);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        record(new TraceEvent(
                SEQ.incrementAndGet(), Instant.now(),
                EventType.AGENT_ERROR,
                safeAgentName(error), null, null,
                String.valueOf(error)));
        LOG.errorf("[trace] !! agent error %s", error);
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution exec) {
        String tool = String.valueOf(exec);
        record(new TraceEvent(
                SEQ.incrementAndGet(), Instant.now(),
                EventType.TOOL_BEFORE, null, tool, null, null));
        LOG.infof("[trace] -> tool %s", tool);
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution exec) {
        String tool = String.valueOf(exec);
        record(new TraceEvent(
                SEQ.incrementAndGet(), Instant.now(),
                EventType.TOOL_AFTER, null, tool, null, null));
        LOG.infof("[trace] <- tool %s", tool);
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    public List<TraceEvent> snapshot() {
        return events.stream().toList();
    }

    public void clear() {
        events.clear();
        startNanos.clear();
        LOG.info("[trace] cleared");
    }

    private void record(TraceEvent e) {
        events.add(e);
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
    }

    private static String safeAgentName(Object error) {
        // AgentInvocationError is a record; use its toString for now.
        return Objects.toString(error, "unknown");
    }
}
