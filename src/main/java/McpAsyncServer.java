import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

public class McpAsyncServer {
    private static final Logger logger = LoggerFactory.getLogger(McpAsyncServer.class);
    private final HttpServletSseServerTransportProvider mcpTransportProvider;
    private final ObjectMapper objectMapper;
    private final McpSchema.ServerCapabilities serverCapabilities;
    private final McpSchema.Implementation serverInfo;
    private final CopyOnWriteArrayList<AsyncToolSpecification> tools = new CopyOnWriteArrayList<>();
    private McpSchema.LoggingLevel minLoggingLevel = McpSchema.LoggingLevel.DEBUG;
    private List<String> protocolVersions = List.of(McpSchema.LATEST_PROTOCOL_VERSION);

    static Specification builder(HttpServletSseServerTransportProvider transportProvider) {
        return new Specification(transportProvider);
    }

    McpAsyncServer(HttpServletSseServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper,
                   McpSchema.Implementation serverInfo,
                   McpSchema.ServerCapabilities serverCapabilities,
                   List<AsyncToolSpecification> tools) {
        logger.info("Creating McpAsyncServer");
        this.mcpTransportProvider = mcpTransportProvider;
        this.objectMapper = objectMapper;
        this.serverInfo = serverInfo;
        this.serverCapabilities = serverCapabilities;
        this.tools.addAll(tools);

        Map<String, McpServerSession.RequestHandler<?>> requestHandlers = new HashMap<>();

        // Ping MUST respond with an empty data, but not NULL response.
        requestHandlers.put(McpSchema.METHOD_PING, (exchange, params) -> Mono.just(Collections.emptyMap()));

        if (this.serverCapabilities.tools() != null) {
            requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler());
            requestHandlers.put(McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler());
        }

        if (this.serverCapabilities.logging() != null) {
            requestHandlers.put(McpSchema.METHOD_LOGGING_SET_LEVEL, setLoggerRequestHandler());
        }

        Map<String, McpServerSession.NotificationHandler> notificationHandlers = new HashMap<>();
        notificationHandlers.put(McpSchema.METHOD_NOTIFICATION_INITIALIZED, (exchange, params) -> Mono.empty());

        mcpTransportProvider.setSessionFactory(transport ->
                new McpServerSession(
                        UUID.randomUUID().toString(),
                        transport,
                        this::asyncInitializeRequestHandler,
                        Mono::empty,
                        requestHandlers,
                        notificationHandlers
                )
        );
    }

    private McpServerSession.RequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler() {
        return (exchange, params) -> {
            List<McpSchema.Tool> tools = this.tools.stream()
                    .map(AsyncToolSpecification::tool)
                    .collect(Collectors.toList());

            return Mono.just(new McpSchema.ListToolsResult(tools, null));
        };
    }

    private McpServerSession.RequestHandler<McpSchema.CallToolResult> toolsCallRequestHandler() {
        return (exchange, params) -> {
            McpSchema.CallToolRequest callToolRequest = objectMapper.convertValue(params,
                    new TypeReference<>() {
                    });

            Optional<AsyncToolSpecification> toolSpecification = this.tools.stream()
                    .filter(tr -> callToolRequest.name().equals(tr.tool().name()))
                    .findAny();

            if (toolSpecification.isEmpty()) {
                return Mono.error(new McpError("Tool not found: " + callToolRequest.name()));
            }

            return toolSpecification.map(tool -> tool.call().apply(exchange, callToolRequest.arguments()))
                    .orElse(Mono.error(new McpError("Tool not found: " + callToolRequest.name())));
        };
    }

    private McpServerSession.RequestHandler<Void> setLoggerRequestHandler() {
        return (exchange, params) -> {
            this.minLoggingLevel = objectMapper.convertValue(params, new TypeReference<McpSchema.LoggingLevel>() {
            });

            return Mono.empty();
        };
    }

    private Mono<McpSchema.InitializeResult> asyncInitializeRequestHandler(
            McpSchema.InitializeRequest initializeRequest) {

        return Mono.defer(() -> {
            logger.info("Client initialize request - Protocol: {}, Capabilities: {}, Info: {}",
                    initializeRequest.protocolVersion(),
                    initializeRequest.capabilities(),
                    initializeRequest.clientInfo());

            // Default to highest supported version
            String serverProtocolVersion = this.protocolVersions.get(this.protocolVersions.size() - 1);

            if (this.protocolVersions.contains(initializeRequest.protocolVersion())) {
                serverProtocolVersion = initializeRequest.protocolVersion();
            } else {
                logger.warn("Client requested unsupported protocol version: {}, so the server will suggest the {} version instead",
                        initializeRequest.protocolVersion(), serverProtocolVersion);
            }

            McpSchema.InitializeResult result = new McpSchema.InitializeResult(
                    serverProtocolVersion,
                    this.serverCapabilities,
                    this.serverInfo,
                    null
            );

            return Mono.just(result);
        });
    }

    public Mono<Void> addTool(AsyncToolSpecification toolSpecification) {
        if (toolSpecification == null) {
            return Mono.error(new McpError("Tool specification must not be null"));
        }
        if (toolSpecification.tool() == null) {
            return Mono.error(new McpError("Tool must not be null"));
        }
        if (toolSpecification.call() == null) {
            return Mono.error(new McpError("Tool call handler must not be null"));
        }
        if (this.serverCapabilities.tools() == null) {
            return Mono.error(new McpError("Server must be configured with tool capabilities"));
        }

        return Mono.defer(() -> {
            // Check for duplicate tool names
            if (this.tools.stream().anyMatch(th -> th.tool().name().equals(toolSpecification.tool().name()))) {
                return Mono
                        .error(new McpError("Tool with name '" + toolSpecification.tool().name() + "' already exists"));
            }

            this.tools.add(toolSpecification);
            logger.debug("Added tool handler: {}", toolSpecification.tool().name());

            if (this.serverCapabilities.tools().listChanged()) {
                return notifyToolsListChanged();
            }
            return Mono.empty();
        });
    }

    public Mono<Void> notifyToolsListChanged() {
        return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null);
    }

    /**
     * Synchronous server specification.
     */
    static class Specification {

        private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server",
                "1.0.0");

        private final HttpServletSseServerTransportProvider transportProvider;

        private ObjectMapper objectMapper;

        private McpSchema.Implementation serverInfo = DEFAULT_SERVER_INFO;

        private McpSchema.ServerCapabilities serverCapabilities;

        /**
         * The Model Context Protocol (MCP) allows servers to expose tools that can be
         * invoked by language models. Tools enable models to interact with external
         * systems, such as querying databases, calling APIs, or performing computations.
         * Each tool is uniquely identified by a name and includes metadata describing its
         * schema.
         */
        private final List<AsyncToolSpecification> tools = new ArrayList<>();

        private Specification(HttpServletSseServerTransportProvider transportProvider) {
            if (transportProvider == null) {
                throw new IllegalArgumentException("transportProvider must not be null");
            }
            this.transportProvider = transportProvider;
        }

        public Specification serverInfo(String name, String version) {
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
            if (version == null) {
                throw new IllegalArgumentException("version must not be null");
            }
            this.serverInfo = new McpSchema.Implementation(name, version);
            return this;
        }

        public Specification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
            if (serverCapabilities == null) {
                throw new IllegalArgumentException("serverCapabilities must not be null");
            }
            this.serverCapabilities = serverCapabilities;
            return this;
        }

        public McpAsyncServer build() {
            var mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
            return new McpAsyncServer(this.transportProvider, mapper, this.serverInfo, this.serverCapabilities, this.tools);
        }
    }
}
