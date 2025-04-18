package modelcontextprotocol.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import modelcontextprotocol.spec.McpError;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.spec.McpServerSession;
import modelcontextprotocol.spec.McpServerTransportProvider;
import modelcontextprotocol.util.Utils;

public class McpAsyncServer {
    private static final Logger logger = LoggerFactory.getLogger(McpAsyncServer.class);
    private final McpAsyncServer delegate;

    McpAsyncServer() {
        this.delegate = null;
    }

    McpAsyncServer(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper, McpServerFeatures.Async features) {
        this.delegate = new AsyncServerImpl(mcpTransportProvider, objectMapper, features);
    }

    public McpSchema.ServerCapabilities getServerCapabilities() {
        return this.delegate.getServerCapabilities();
    }

    public McpSchema.Implementation getServerInfo() {
        return this.delegate.getServerInfo();
    }

    public Mono<Void> closeGracefully() {
        return this.delegate.closeGracefully();
    }

    public void close() {
        this.delegate.close();
    }

    public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
        return this.delegate.addTool(toolSpecification);
    }

    public Mono<Void> removeTool(String toolName) {
        return this.delegate.removeTool(toolName);
    }

    public Mono<Void> notifyToolsListChanged() {
        return this.delegate.notifyToolsListChanged();
    }

    public Mono<Void> addResource(McpServerFeatures.AsyncResourceSpecification resourceHandler) {
        return this.delegate.addResource(resourceHandler);
    }

    public Mono<Void> removeResource(String resourceUri) {
        return this.delegate.removeResource(resourceUri);
    }

    public Mono<Void> notifyResourcesListChanged() {
        return this.delegate.notifyResourcesListChanged();
    }

    public Mono<Void> addPrompt(McpServerFeatures.AsyncPromptSpecification promptSpecification) {
        return this.delegate.addPrompt(promptSpecification);
    }

    public Mono<Void> removePrompt(String promptName) {
        return this.delegate.removePrompt(promptName);
    }

    public Mono<Void> notifyPromptsListChanged() {
        return this.delegate.notifyPromptsListChanged();
    }

    void setProtocolVersions(List<String> protocolVersions) {
        this.delegate.setProtocolVersions(protocolVersions);
    }

    private static class AsyncServerImpl extends McpAsyncServer {
        private final McpServerTransportProvider mcpTransportProvider;
        private final ObjectMapper objectMapper;
        private final McpSchema.ServerCapabilities serverCapabilities;
        private final McpSchema.Implementation serverInfo;
        private final String instructions;
        private final CopyOnWriteArrayList<McpServerFeatures.AsyncToolSpecification> tools = new CopyOnWriteArrayList();
        private final CopyOnWriteArrayList<McpSchema.ResourceTemplate> resourceTemplates = new CopyOnWriteArrayList();
        private final ConcurrentHashMap<String, McpServerFeatures.AsyncResourceSpecification> resources = new ConcurrentHashMap();
        private final ConcurrentHashMap<String, McpServerFeatures.AsyncPromptSpecification> prompts = new ConcurrentHashMap();
        private McpSchema.LoggingLevel minLoggingLevel;
        private List<String> protocolVersions;

        AsyncServerImpl(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper, McpServerFeatures.Async features) {
            this.minLoggingLevel = McpSchema.LoggingLevel.DEBUG;
            this.protocolVersions = List.of("2024-11-05");
            this.mcpTransportProvider = mcpTransportProvider;
            this.objectMapper = objectMapper;
            this.serverInfo = features.serverInfo();
            this.serverCapabilities = features.serverCapabilities();
            this.instructions = features.instructions();
            this.tools.addAll(features.tools());
            this.resources.putAll(features.resources());
            this.resourceTemplates.addAll(features.resourceTemplates());
            this.prompts.putAll(features.prompts());
            Map<String, McpServerSession.RequestHandler<?>> requestHandlers = new HashMap();
            requestHandlers.put("ping", (exchange, params) -> {
                return Mono.just(Map.of());
            });
            if (this.serverCapabilities.tools() != null) {
                requestHandlers.put("tools/list", this.toolsListRequestHandler());
                requestHandlers.put("tools/call", this.toolsCallRequestHandler());
            }

            if (this.serverCapabilities.resources() != null) {
                requestHandlers.put("resources/list", this.resourcesListRequestHandler());
                requestHandlers.put("resources/read", this.resourcesReadRequestHandler());
                requestHandlers.put("resources/templates/list", this.resourceTemplateListRequestHandler());
            }

            if (this.serverCapabilities.prompts() != null) {
                requestHandlers.put("prompts/list", this.promptsListRequestHandler());
                requestHandlers.put("prompts/get", this.promptsGetRequestHandler());
            }

            if (this.serverCapabilities.logging() != null) {
                requestHandlers.put("logging/setLevel", this.setLoggerRequestHandler());
            }

            Map<String, McpServerSession.NotificationHandler> notificationHandlers = new HashMap();
            notificationHandlers.put("notifications/initialized", (exchange, params) -> {
                return Mono.empty();
            });
            List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers = features.rootsChangeConsumers();
            if (Utils.isEmpty(rootsChangeConsumers)) {
                rootsChangeConsumers = List.of((exchange, roots) -> {
                    return Mono.fromRunnable(() -> {
                        McpAsyncServer.logger.warn("Roots list changed notification, but no consumers provided. Roots list changed: {}", roots);
                    });
                });
            }

            notificationHandlers.put("notifications/roots/list_changed", this.asyncRootsListChangedNotificationHandler(rootsChangeConsumers));
            mcpTransportProvider.setSessionFactory((transport) -> {
                return new McpServerSession(UUID.randomUUID().toString(), transport, this::asyncInitializeRequestHandler, Mono::empty, requestHandlers, notificationHandlers);
            });
        }

        private Mono<McpSchema.InitializeResult> asyncInitializeRequestHandler(McpSchema.InitializeRequest initializeRequest) {
            return Mono.defer(() -> {
                McpAsyncServer.logger.info("Client initialize request - Protocol: {}, Capabilities: {}, Info: {}", new Object[]{initializeRequest.protocolVersion(), initializeRequest.capabilities(), initializeRequest.clientInfo()});
                String serverProtocolVersion = (String)this.protocolVersions.get(this.protocolVersions.size() - 1);
                if (this.protocolVersions.contains(initializeRequest.protocolVersion())) {
                    serverProtocolVersion = initializeRequest.protocolVersion();
                } else {
                    McpAsyncServer.logger.warn("Client requested unsupported protocol version: {}, so the server will sugggest the {} version instead", initializeRequest.protocolVersion(), serverProtocolVersion);
                }

                return Mono.just(new McpSchema.InitializeResult(serverProtocolVersion, this.serverCapabilities, this.serverInfo, this.instructions));
            });
        }

        public McpSchema.ServerCapabilities getServerCapabilities() {
            return this.serverCapabilities;
        }

        public McpSchema.Implementation getServerInfo() {
            return this.serverInfo;
        }

        public Mono<Void> closeGracefully() {
            return this.mcpTransportProvider.closeGracefully();
        }

        public void close() {
            this.mcpTransportProvider.close();
        }

//        private McpServerSession.NotificationHandler asyncRootsListChangedNotificationHandler(List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers) {
//            return (exchange, params) -> {
//                return exchange.listRoots().flatMap((listRootsResult) -> {
//                    return Flux.fromIterable(rootsChangeConsumers).flatMap((consumer) -> {
//                        return (Publisher)consumer.apply(exchange, listRootsResult.roots());
//                    }).onErrorResume((error) -> {
//                        McpAsyncServer.logger.error("Error handling roots list change notification", error);
//                        return Mono.empty();
//                    }).then();
//                });
//            };
//        }

        private McpServerSession.NotificationHandler asyncRootsListChangedNotificationHandler(
                List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers
        ) {
            return (exchange, params) -> {
                return exchange.listRoots().flatMap((listRootsResult) -> {
                    return Flux.fromIterable(rootsChangeConsumers)
                            .flatMap((consumer) -> consumer.apply(exchange,
                                    listRootsResult.roots()))
                            .onErrorResume((error) -> {
                                McpAsyncServer.logger.error("Error handling roots list change notification", error);
                                return Mono.empty();
                            })
                            .then();
                });
            };
        }

        public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
            if (toolSpecification == null) {
                return Mono.error(new McpError("Tool specification must not be null"));
            } else if (toolSpecification.tool() == null) {
                return Mono.error(new McpError("Tool must not be null"));
            } else if (toolSpecification.call() == null) {
                return Mono.error(new McpError("Tool call handler must not be null"));
            } else {
                return this.serverCapabilities.tools() == null ? Mono.error(new McpError("Server must be configured with tool capabilities")) : Mono.defer(() -> {
                    if (this.tools.stream().anyMatch((th) -> {
                        return th.tool().name().equals(toolSpecification.tool().name());
                    })) {
                        return Mono.error(new McpError("Tool with name '" + toolSpecification.tool().name() + "' already exists"));
                    } else {
                        this.tools.add(toolSpecification);
                        McpAsyncServer.logger.debug("Added tool handler: {}", toolSpecification.tool().name());
                        return this.serverCapabilities.tools().listChanged() ? this.notifyToolsListChanged() : Mono.empty();
                    }
                });
            }
        }

        public Mono<Void> removeTool(String toolName) {
            if (toolName == null) {
                return Mono.error(new McpError("Tool name must not be null"));
            } else {
                return this.serverCapabilities.tools() == null ? Mono.error(new McpError("Server must be configured with tool capabilities")) : Mono.defer(() -> {
                    boolean removed = this.tools.removeIf((toolSpecification) -> {
                        return toolSpecification.tool().name().equals(toolName);
                    });
                    if (removed) {
                        McpAsyncServer.logger.debug("Removed tool handler: {}", toolName);
                        return this.serverCapabilities.tools().listChanged() ? this.notifyToolsListChanged() : Mono.empty();
                    } else {
                        return Mono.error(new McpError("Tool with name '" + toolName + "' not found"));
                    }
                });
            }
        }

        public Mono<Void> notifyToolsListChanged() {
            return this.mcpTransportProvider.notifyClients("notifications/tools/list_changed", (Object)null);
        }

        private McpServerSession.RequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler() {
            return (exchange, params) -> {
                List<McpSchema.Tool> tools = this.tools.stream().map(McpServerFeatures.AsyncToolSpecification::tool).collect(Collectors.toList());
                return Mono.just(new McpSchema.ListToolsResult(tools, (String)null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.CallToolResult> toolsCallRequestHandler() {
            return (exchange, params) -> {
                McpSchema.CallToolRequest callToolRequest = (McpSchema.CallToolRequest)this.objectMapper.convertValue(params, new TypeReference<McpSchema.CallToolRequest>() {
                });
                Optional<McpServerFeatures.AsyncToolSpecification> toolSpecification = this.tools.stream().filter((tr) -> {
                    return callToolRequest.name().equals(tr.tool().name());
                }).findAny();
                return toolSpecification.isEmpty() ? Mono.error(new McpError("Tool not found: " + callToolRequest.name())) : (Mono)toolSpecification.map((tool) -> {
                    return (Mono)tool.call().apply(exchange, callToolRequest.arguments());
                }).orElse(Mono.error(new McpError("Tool not found: " + callToolRequest.name())));
            };
        }

        public Mono<Void> addResource(McpServerFeatures.AsyncResourceSpecification resourceSpecification) {
            if (resourceSpecification != null && resourceSpecification.resource() != null) {
                return this.serverCapabilities.resources() == null ? Mono.error(new McpError("Server must be configured with resource capabilities")) : Mono.defer(() -> {
                    if (this.resources.putIfAbsent(resourceSpecification.resource().uri(), resourceSpecification) != null) {
                        return Mono.error(new McpError("Resource with URI '" + resourceSpecification.resource().uri() + "' already exists"));
                    } else {
                        McpAsyncServer.logger.debug("Added resource handler: {}", resourceSpecification.resource().uri());
                        return this.serverCapabilities.resources().listChanged() ? this.notifyResourcesListChanged() : Mono.empty();
                    }
                });
            } else {
                return Mono.error(new McpError("Resource must not be null"));
            }
        }

        public Mono<Void> removeResource(String resourceUri) {
            if (resourceUri == null) {
                return Mono.error(new McpError("Resource URI must not be null"));
            } else {
                return this.serverCapabilities.resources() == null ? Mono.error(new McpError("Server must be configured with resource capabilities")) : Mono.defer(() -> {
                    McpServerFeatures.AsyncResourceSpecification removed = (McpServerFeatures.AsyncResourceSpecification)this.resources.remove(resourceUri);
                    if (removed != null) {
                        McpAsyncServer.logger.debug("Removed resource handler: {}", resourceUri);
                        return this.serverCapabilities.resources().listChanged() ? this.notifyResourcesListChanged() : Mono.empty();
                    } else {
                        return Mono.error(new McpError("Resource with URI '" + resourceUri + "' not found"));
                    }
                });
            }
        }

        public Mono<Void> notifyResourcesListChanged() {
            return this.mcpTransportProvider.notifyClients("notifications/resources/list_changed", (Object)null);
        }

        private McpServerSession.RequestHandler<McpSchema.ListResourcesResult> resourcesListRequestHandler() {
            return (exchange, params) -> {
                List<McpSchema.Resource> resourceList = this.resources.values().stream().map(McpServerFeatures.AsyncResourceSpecification::resource).collect(Collectors.toList());
                return Mono.just(new McpSchema.ListResourcesResult(resourceList, (String)null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.ListResourceTemplatesResult> resourceTemplateListRequestHandler() {
            return (exchange, params) -> {
                return Mono.just(new McpSchema.ListResourceTemplatesResult(this.resourceTemplates, (String)null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.ReadResourceResult> resourcesReadRequestHandler() {
            return (exchange, params) -> {
                McpSchema.ReadResourceRequest resourceRequest = (McpSchema.ReadResourceRequest)this.objectMapper.convertValue(params, new TypeReference<McpSchema.ReadResourceRequest>() {
                });
                String resourceUri = resourceRequest.uri();
                McpServerFeatures.AsyncResourceSpecification specification = (McpServerFeatures.AsyncResourceSpecification)this.resources.get(resourceUri);
                return specification != null ? (Mono)specification.readHandler().apply(exchange, resourceRequest) : Mono.error(new McpError("Resource not found: " + resourceUri));
            };
        }

        public Mono<Void> addPrompt(McpServerFeatures.AsyncPromptSpecification promptSpecification) {
            if (promptSpecification == null) {
                return Mono.error(new McpError("Prompt specification must not be null"));
            } else {
                return this.serverCapabilities.prompts() == null ? Mono.error(new McpError("Server must be configured with prompt capabilities")) : Mono.defer(() -> {
                    McpServerFeatures.AsyncPromptSpecification specification = (McpServerFeatures.AsyncPromptSpecification)this.prompts.putIfAbsent(promptSpecification.prompt().name(), promptSpecification);
                    if (specification != null) {
                        return Mono.error(new McpError("Prompt with name '" + promptSpecification.prompt().name() + "' already exists"));
                    } else {
                        McpAsyncServer.logger.debug("Added prompt handler: {}", promptSpecification.prompt().name());
                        return this.serverCapabilities.prompts().listChanged() ? this.notifyPromptsListChanged() : Mono.empty();
                    }
                });
            }
        }

        public Mono<Void> removePrompt(String promptName) {
            if (promptName == null) {
                return Mono.error(new McpError("Prompt name must not be null"));
            } else {
                return this.serverCapabilities.prompts() == null ? Mono.error(new McpError("Server must be configured with prompt capabilities")) : Mono.defer(() -> {
                    McpServerFeatures.AsyncPromptSpecification removed = (McpServerFeatures.AsyncPromptSpecification)this.prompts.remove(promptName);
                    if (removed != null) {
                        McpAsyncServer.logger.debug("Removed prompt handler: {}", promptName);
                        return this.serverCapabilities.prompts().listChanged() ? this.notifyPromptsListChanged() : Mono.empty();
                    } else {
                        return Mono.error(new McpError("Prompt with name '" + promptName + "' not found"));
                    }
                });
            }
        }

        public Mono<Void> notifyPromptsListChanged() {
            return this.mcpTransportProvider.notifyClients("notifications/prompts/list_changed", (Object)null);
        }

        private McpServerSession.RequestHandler<McpSchema.ListPromptsResult> promptsListRequestHandler() {
            return (exchange, params) -> {
                List<McpSchema.Prompt> promptList = this.prompts.values().stream().map(McpServerFeatures.AsyncPromptSpecification::prompt).collect(Collectors.toList());
                return Mono.just(new McpSchema.ListPromptsResult(promptList, (String)null));
            };
        }

        private McpServerSession.RequestHandler<McpSchema.GetPromptResult> promptsGetRequestHandler() {
            return (exchange, params) -> {
                McpSchema.GetPromptRequest promptRequest = (McpSchema.GetPromptRequest)this.objectMapper.convertValue(params, new TypeReference<McpSchema.GetPromptRequest>() {
                });
                McpServerFeatures.AsyncPromptSpecification specification = (McpServerFeatures.AsyncPromptSpecification)this.prompts.get(promptRequest.name());
                return specification == null ? Mono.error(new McpError("Prompt not found: " + promptRequest.name())) : (Mono)specification.promptHandler().apply(exchange, promptRequest);
            };
        }

        public Mono<Void> loggingNotification(McpSchema.LoggingMessageNotification loggingMessageNotification) {
            if (loggingMessageNotification == null) {
                return Mono.error(new McpError("Logging message must not be null"));
            } else {
                return loggingMessageNotification.level().level() < this.minLoggingLevel.level() ? Mono.empty() : this.mcpTransportProvider.notifyClients("notifications/message", loggingMessageNotification);
            }
        }

        private McpServerSession.RequestHandler<Object> setLoggerRequestHandler() {
            return (exchange, params) -> Mono.defer(() -> {
                McpSchema.SetLevelRequest newMinLoggingLevel =
                        objectMapper.convertValue(params, new TypeReference<McpSchema.SetLevelRequest>() {});

                exchange.setMinLoggingLevel(newMinLoggingLevel.level());
                this.minLoggingLevel = newMinLoggingLevel.level();

                return Mono.just(Map.of());
            });
        }

        void setProtocolVersions(List<String> protocolVersions) {
            this.protocolVersions = protocolVersions;
        }
    }
}

