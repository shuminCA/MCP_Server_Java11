package storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class McpAsyncServer {
    private static final Logger logger = LoggerFactory.getLogger(McpAsyncServer.class);
    private final McpAsyncServer delegate;

    McpAsyncServer() {
        this.delegate = null;
    }

    McpAsyncServer(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper,
                   McpServerFeatures.Async features) {
        logger.info("Creating McpAsyncServer");
        this.delegate = new AsyncServerImpl(mcpTransportProvider, objectMapper, features);
    }

    public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
        return this.delegate.addTool(toolSpecification);
    }

    public Mono<Void> notifyToolsListChanged() {
        return this.delegate.notifyToolsListChanged();
    }

    public static class AsyncServerImpl extends McpAsyncServer {

        private final McpServerTransportProvider mcpTransportProvider;

        private final ObjectMapper objectMapper;

        private final McpSchema.ServerCapabilities serverCapabilities;

        private final McpSchema.Implementation serverInfo;

        private final CopyOnWriteArrayList<McpServerFeatures.AsyncToolSpecification> tools = new CopyOnWriteArrayList<>();

        private final CopyOnWriteArrayList<McpSchema.ResourceTemplate> resourceTemplates = new CopyOnWriteArrayList<>();

        private final ConcurrentHashMap<String, McpServerFeatures.AsyncResourceSpecification> resources = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<String, McpServerFeatures.AsyncPromptSpecification> prompts = new ConcurrentHashMap<>();

        private McpSchema.LoggingLevel minLoggingLevel = McpSchema.LoggingLevel.DEBUG;

        private List<String> protocolVersions = List.of(McpSchema.LATEST_PROTOCOL_VERSION);

        public AsyncServerImpl(
                McpServerTransportProvider mcpTransportProvider,
                ObjectMapper objectMapper,
                McpServerFeatures.Async features
        ) {
            this.mcpTransportProvider = mcpTransportProvider;
            this.objectMapper = objectMapper;
            this.serverInfo = features.getServerInfo();
            this.serverCapabilities = features.getServerCapabilities();
            this.tools.addAll(features.getTools());
            this.resources.putAll(features.getResources());
            this.resourceTemplates.addAll(features.getResourceTemplates());
            this.prompts.putAll(features.getPrompts());

            Map<String, McpServerSession.RequestHandler<?>> requestHandlers = new HashMap<>();

            // Ping MUST respond with an empty data, but not NULL response.
            requestHandlers.put(McpSchema.METHOD_PING, (exchange, params) -> Mono.just(Collections.emptyMap()));

            if (this.serverCapabilities.tools() != null) {
                requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler());
                requestHandlers.put(McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler());
            }

            if (this.serverCapabilities.resources() != null) {
                requestHandlers.put(McpSchema.METHOD_RESOURCES_LIST, resourcesListRequestHandler());
                requestHandlers.put(McpSchema.METHOD_RESOURCES_READ, resourcesReadRequestHandler());
                requestHandlers.put(McpSchema.METHOD_RESOURCES_TEMPLATES_LIST, resourceTemplateListRequestHandler());
            }

            if (this.serverCapabilities.prompts() != null) {
                requestHandlers.put(McpSchema.METHOD_PROMPT_LIST, promptsListRequestHandler());
                requestHandlers.put(McpSchema.METHOD_PROMPT_GET, promptsGetRequestHandler());
            }

            if (this.serverCapabilities.logging() != null) {
                requestHandlers.put(McpSchema.METHOD_LOGGING_SET_LEVEL, setLoggerRequestHandler());
            }

            Map<String, McpServerSession.NotificationHandler> notificationHandlers = new HashMap<>();
            notificationHandlers.put(McpSchema.METHOD_NOTIFICATION_INITIALIZED, (exchange, params) -> Mono.empty());

            List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers =
                    features.getRootsChangeConsumers();

            if (Utils.isEmpty(rootsChangeConsumers)) {
                rootsChangeConsumers = Collections.singletonList((exchange, roots) ->
                        Mono.fromRunnable(() ->
                                logger.warn("Roots list changed notification, but no consumers provided. Roots list changed: {}", roots)
                        )
                );
            }

            notificationHandlers.put(
                    McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
                    asyncRootsListChangedNotificationHandler(rootsChangeConsumers)
            );

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
                        .map(McpServerFeatures.AsyncToolSpecification::tool)
                        .collect(Collectors.toList());

                return Mono.just(new McpSchema.ListToolsResult(tools, null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.CallToolResult> toolsCallRequestHandler() {
            return (exchange, params) -> {
                McpSchema.CallToolRequest callToolRequest = objectMapper.convertValue(params,
                        new TypeReference<McpSchema.CallToolRequest>() {
                        });

                Optional<McpServerFeatures.AsyncToolSpecification> toolSpecification = this.tools.stream()
                        .filter(tr -> callToolRequest.name().equals(tr.tool().name()))
                        .findAny();

                if (toolSpecification.isEmpty()) {
                    return Mono.error(new McpError("Tool not found: " + callToolRequest.name()));
                }

                return toolSpecification.map(tool -> tool.call().apply(exchange, callToolRequest.arguments()))
                        .orElse(Mono.error(new McpError("Tool not found: " + callToolRequest.name())));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.ListResourcesResult> resourcesListRequestHandler() {
            return (exchange, params) -> {
                List<McpSchema.Resource> resourceList = this.resources.values()
                        .stream()
                        .map(McpServerFeatures.AsyncResourceSpecification::resource)
                        .collect(Collectors.toList());
                return Mono.just(new McpSchema.ListResourcesResult(resourceList, null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.ListResourceTemplatesResult> resourceTemplateListRequestHandler() {
            return (exchange, params) -> Mono
                    .just(new McpSchema.ListResourceTemplatesResult(this.resourceTemplates, null));

        }

        private McpServerSession.RequestHandler<McpSchema.ReadResourceResult> resourcesReadRequestHandler() {
            return (exchange, params) -> {
                McpSchema.ReadResourceRequest resourceRequest = objectMapper.convertValue(params,
                        new TypeReference<McpSchema.ReadResourceRequest>() {
                        });
                var resourceUri = resourceRequest.uri();
                McpServerFeatures.AsyncResourceSpecification specification = this.resources.get(resourceUri);
                if (specification != null) {
                    return specification.readHandler().apply(exchange, resourceRequest);
                }
                return Mono.error(new McpError("Resource not found: " + resourceUri));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.ListPromptsResult> promptsListRequestHandler() {
            return (exchange, params) -> {
                List<McpSchema.Prompt> promptList = this.prompts.values()
                        .stream()
                        .map(McpServerFeatures.AsyncPromptSpecification::prompt)
                        .collect(Collectors.toList());
                return Mono.just(new McpSchema.ListPromptsResult(promptList, null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.GetPromptResult> promptsGetRequestHandler() {
            return (exchange, params) -> {
                McpSchema.GetPromptRequest promptRequest = objectMapper.convertValue(params,
                        new TypeReference<McpSchema.GetPromptRequest>() {
                        });

                // Implement prompt retrieval logic here
                McpServerFeatures.AsyncPromptSpecification specification = this.prompts.get(promptRequest.name());
                if (specification == null) {
                    return Mono.error(new McpError("Prompt not found: " + promptRequest.name()));
                }

                return specification.promptHandler().apply(exchange, promptRequest);
            };
        }

        private McpServerSession.RequestHandler<Void> setLoggerRequestHandler() {
            return (exchange, params) -> {
                this.minLoggingLevel = objectMapper.convertValue(params, new TypeReference<McpSchema.LoggingLevel>() {
                });

                return Mono.empty();
            };
        }

        private McpServerSession.NotificationHandler asyncRootsListChangedNotificationHandler(
                List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers) {
            return (exchange, params) -> exchange.listRoots()
                    .flatMap(listRootsResult -> Flux.fromIterable(rootsChangeConsumers)
                            .flatMap(consumer -> consumer.apply(exchange, listRootsResult.roots()))
                            .onErrorResume(error -> {
                                logger.error("Error handling roots list change notification", error);
                                return Mono.empty();
                            })
                            .then());
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

        @Override
        public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
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

        @Override
        public Mono<Void> notifyToolsListChanged() {
            return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null);
        }

    }
}