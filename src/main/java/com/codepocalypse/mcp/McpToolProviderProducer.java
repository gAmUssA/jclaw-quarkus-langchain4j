package com.codepocalypse.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Builds the MCP client + tool provider programmatically against the hosted
 * developer-events MCP server. This replaces the {@code quarkus-langchain4j-mcp}
 * extension so we can:
 * <ul>
 *     <li>Control the transport (streamable HTTP, not HTTP+SSE)</li>
 *     <li>Expose the {@link ToolProvider} as a plain CDI bean so both the
 *         monolithic {@code JClawAgent} AND the agentic sub-agents in
 *         {@code SupervisorProducer} can share it.</li>
 * </ul>
 *
 * <p>The produced {@code ToolProvider} is wired into {@code JClawAgent} via
 * {@code @RegisterAiService(toolProviderSupplier = McpToolProviderSupplier.class)}.
 */
@ApplicationScoped
public class McpToolProviderProducer {

    private static final Logger LOG = Logger.getLogger(McpToolProviderProducer.class);

    @Inject
    @ConfigProperty(name = "jclaw.mcp.events.url")
    String mcpUrl;

    @Inject
    @ConfigProperty(name = "jclaw.mcp.events.log-requests", defaultValue = "false")
    boolean logRequests;

    @Inject
    @ConfigProperty(name = "jclaw.mcp.events.log-responses", defaultValue = "false")
    boolean logResponses;

    private McpClient mcpClient;

    @Produces
    @ApplicationScoped
    public ToolProvider mcpToolProvider() {
        LOG.infof("Starting MCP client for developer-events at %s", mcpUrl);

        McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url(mcpUrl)
                .timeout(Duration.ofSeconds(30))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();

        this.mcpClient = new DefaultMcpClient.Builder()
                .clientName("JClaw")
                .clientVersion("1.0")
                .transport(transport)
                .build();

        ToolProvider provider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .failIfOneServerFails(false)
                .build();

        LOG.info("MCP tool provider ready");
        return provider;
    }

    @PreDestroy
    void shutdown() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
                LOG.info("MCP client closed");
            } catch (Exception e) {
                LOG.warnf(e, "Failed to close MCP client cleanly");
            }
        }
    }
}
