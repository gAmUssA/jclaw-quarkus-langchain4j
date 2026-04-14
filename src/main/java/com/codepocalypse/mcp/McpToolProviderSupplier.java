package com.codepocalypse.mcp;

import dev.langchain4j.service.tool.ToolProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * Supplier referenced from
 * {@code @RegisterAiService(toolProviderSupplier = McpToolProviderSupplier.class)}.
 * Quarkus LangChain4j looks this up as a CDI bean at AI-service build time and
 * calls {@link #get()} — which returns the {@link ToolProvider} produced by
 * {@link McpToolProviderProducer}.
 *
 * <p>Must be a proper CDI bean ({@code @Singleton}) because Quarkus ArC discovers
 * the {@code toolProviderSupplier} via bean lookup, not reflection.
 */
@Singleton
public class McpToolProviderSupplier implements Supplier<ToolProvider> {

    @Inject
    ToolProvider mcpToolProvider;

    @Override
    public ToolProvider get() {
        return mcpToolProvider;
    }
}
