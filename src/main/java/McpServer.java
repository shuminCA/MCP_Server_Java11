/*
 * Copyright 2024-2024 the original author or authors.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface McpServer {

    /**
     * Starts building a synchronous MCP server that provides blocking operations.
     * Synchronous servers block the current Thread's execution upon each request before
     * giving the control back to the caller, making them simpler to implement but
     * potentially less scalable for concurrent operations.
     * @param transportProvider The transport layer implementation for MCP communication.
     * @return A new instance of {@link SyncSpecification} for configuring the server.
     */
    static SyncSpecification sync(McpServerTransportProvider transportProvider) {
        return new SyncSpecification(transportProvider);
    }

    /**
     * Synchronous server specification.
     */
    class SyncSpecification {

        private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server",
                "1.0.0");

        private final McpServerTransportProvider transportProvider;

        private ObjectMapper objectMapper;

        private McpSchema.Implementation serverInfo = DEFAULT_SERVER_INFO;

        private McpSchema.ServerCapabilities serverCapabilities;

        private String instructions;

        /**
         * The Model Context Protocol (MCP) allows servers to expose tools that can be
         * invoked by language models. Tools enable models to interact with external
         * systems, such as querying databases, calling APIs, or performing computations.
         * Each tool is uniquely identified by a name and includes metadata describing its
         * schema.
         */
        private final List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        /**
         * The Model Context Protocol (MCP) provides a standardized way for servers to
         * expose resources to clients. Resources allow servers to share data that
         * provides context to language models, such as files, database schemas, or
         * application-specific information. Each resource is uniquely identified by a
         * URI.
         */
        private final Map<String, McpServerFeatures.SyncResourceSpecification> resources = new HashMap<>();

        private final List<McpSchema.ResourceTemplate> resourceTemplates = new ArrayList<>();

        /**
         * The Model Context Protocol (MCP) provides a standardized way for servers to
         * expose prompt templates to clients. Prompts allow servers to provide structured
         * messages and instructions for interacting with language models. Clients can
         * discover available prompts, retrieve their contents, and provide arguments to
         * customize them.
         */
        private final Map<String, McpServerFeatures.SyncPromptSpecification> prompts = new HashMap<>();

        private final List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeHandlers = new ArrayList<>();

        private SyncSpecification(McpServerTransportProvider transportProvider) {
            if (transportProvider == null) {
                throw new IllegalArgumentException("transportProvider must not be null");
            }
            this.transportProvider = transportProvider;
        }

        public SyncSpecification serverInfo(String name, String version) {
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
            if (version == null) {
                throw new IllegalArgumentException("version must not be null");
            }
            this.serverInfo = new McpSchema.Implementation(name, version);
            return this;
        }

        /**
         * Sets the server capabilities that will be advertised to clients during
         * connection initialization. Capabilities define what features the server
         * supports, such as:
         * <ul>
         * <li>Tool execution
         * <li>Resource access
         * <li>Prompt handling
         * </ul>
         * @param serverCapabilities The server capabilities configuration. Must not be
         * null.
         * @return This builder instance for method chaining
         * @throws IllegalArgumentException if serverCapabilities is null
         */
        public SyncSpecification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
            if (serverCapabilities == null) {
                throw new IllegalArgumentException("serverCapabilities must not be null");
            }
            this.serverCapabilities = serverCapabilities;
            return this;
        }

        /**
         * Builds a synchronous MCP server that provides blocking operations.
         * @return A new instance of {@link McpSyncServer} configured with this builder's
         * settings.
         */
        public McpSyncServer build() {
            McpServerFeatures.Sync syncFeatures = new McpServerFeatures.Sync(this.serverInfo, this.serverCapabilities,
                    this.tools, this.resources, this.resourceTemplates, this.prompts, this.rootsChangeHandlers);
            McpServerFeatures.Async asyncFeatures = McpServerFeatures.Async.fromSync(syncFeatures);
            var mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
            var asyncServer = new McpAsyncServer(this.transportProvider, mapper, asyncFeatures);

            return new McpSyncServer(asyncServer);
        }
    }
}
