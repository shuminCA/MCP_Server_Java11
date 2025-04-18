package modelcontextprotocol.spec;

import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;
import modelcontextprotocol.server.McpAsyncServerExchange;

public class McpServerSession implements McpSession {
    private static final Logger logger = LoggerFactory.getLogger(McpServerSession.class);
    private final ConcurrentHashMap<Object, MonoSink<McpSchema.JSONRPCResponse>> pendingResponses = new ConcurrentHashMap<>();
    private final String id;
    private final AtomicLong requestCounter = new AtomicLong(0L);
    private final InitRequestHandler initRequestHandler;
    private final InitNotificationHandler initNotificationHandler;
    private final Map<String, RequestHandler<?>> requestHandlers;
    private final Map<String, NotificationHandler> notificationHandlers;
    private final McpServerTransport transport;
    private final Sinks.One<McpAsyncServerExchange> exchangeSink = Sinks.one();
    private final AtomicReference<McpSchema.ClientCapabilities> clientCapabilities = new AtomicReference();
    private final AtomicReference<McpSchema.Implementation> clientInfo = new AtomicReference();
    private static final int STATE_UNINITIALIZED = 0;
    private static final int STATE_INITIALIZING = 1;
    private static final int STATE_INITIALIZED = 2;
    private final AtomicInteger state = new AtomicInteger(0);

    public McpServerSession(String id, McpServerTransport transport, InitRequestHandler initHandler, InitNotificationHandler initNotificationHandler, Map<String, RequestHandler<?>> requestHandlers, Map<String, NotificationHandler> notificationHandlers) {
        this.id = id;
        this.transport = transport;
        this.initRequestHandler = initHandler;
        this.initNotificationHandler = initNotificationHandler;
        this.requestHandlers = requestHandlers;
        this.notificationHandlers = notificationHandlers;
    }

    public String getId() {
        return this.id;
    }

    public void init(McpSchema.ClientCapabilities clientCapabilities, McpSchema.Implementation clientInfo) {
        this.clientCapabilities.lazySet(clientCapabilities);
        this.clientInfo.lazySet(clientInfo);
    }

    private String generateRequestId() {
        String var10000 = this.id;
        return var10000 + "-" + this.requestCounter.getAndIncrement();
    }

    public <T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef) {
        String requestId = this.generateRequestId();
        return Mono.create((MonoSink<McpSchema.JSONRPCResponse> sink) -> {
            this.pendingResponses.put(requestId, sink);
            McpSchema.JSONRPCRequest jsonrpcRequest = new McpSchema.JSONRPCRequest("2.0", method, requestId, requestParams);
            this.transport.sendMessage(jsonrpcRequest).subscribe((v) -> {
            }, (error) -> {
                this.pendingResponses.remove(requestId);
                sink.error(error);
            });
        }).timeout(Duration.ofSeconds(10L)).handle((jsonRpcResponse, sink) -> {
            if (jsonRpcResponse.error() != null) {
                sink.error(new McpError(jsonRpcResponse.error()));
            } else if (typeRef.getType().equals(Void.class)) {
                sink.complete();
            } else {
                sink.next(this.transport.unmarshalFrom(jsonRpcResponse.result(), typeRef));
            }

        });
    }

    public Mono<Void> sendNotification(String method, Object params) {
        McpSchema.JSONRPCNotification jsonrpcNotification = new McpSchema.JSONRPCNotification("2.0", method, (Map<String, Object>) params);
        return this.transport.sendMessage(jsonrpcNotification);
    }

    public Mono<Void> handle(McpSchema.JSONRPCMessage message) {
        return Mono.defer(() -> {
            if (message instanceof McpSchema.JSONRPCResponse) {
                McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) message;
                logger.debug("Received Response: {}", response);
                MonoSink<McpSchema.JSONRPCResponse> sink = (MonoSink)this.pendingResponses.remove(response.id());
                if (sink == null) {
                    logger.warn("Unexpected response for unknown id {}", response.id());
                } else {
                    sink.success(response);
                }

                return Mono.empty();
            } else if (message instanceof McpSchema.JSONRPCRequest) {
                McpSchema.JSONRPCRequest request = (McpSchema.JSONRPCRequest) message;
                logger.debug("Received request: {}", request);
                Mono var10000 = this.handleIncomingRequest(request).onErrorResume((error) -> {
                    McpSchema.JSONRPCResponse errorResponse = new McpSchema.JSONRPCResponse("2.0", request.id(), (Object)null, new McpSchema.JSONRPCResponse.JSONRPCError(-32603, error.getMessage(), (Object)null));
                    return this.transport.sendMessage(errorResponse).then(Mono.empty());
                });
                McpServerTransport var10001 = this.transport;
                Objects.requireNonNull(var10001);
                return var10000.flatMap(obj -> var10001.sendMessage((McpSchema.JSONRPCMessage) obj));
            } else if (message instanceof McpSchema.JSONRPCNotification) {
                McpSchema.JSONRPCNotification notification = (McpSchema.JSONRPCNotification) message;
                logger.debug("Received notification: {}", notification);
                return this.handleIncomingNotification(notification).doOnError((error) -> {
                    logger.error("Error handling notification: {}", error.getMessage());
                });
            } else {
                logger.warn("Received unknown message type: {}", message);
                return Mono.empty();
            }
        });
    }

    private Mono<McpSchema.JSONRPCResponse> handleIncomingRequest(McpSchema.JSONRPCRequest request) {
        return Mono.defer(() -> {
            Mono resultMono;
            if ("initialize".equals(request.method())) {
                McpSchema.InitializeRequest initializeRequest = this.transport.unmarshalFrom(request.params(), new TypeReference<McpSchema.InitializeRequest>() {
                });
                this.state.lazySet(1);
                this.init(initializeRequest.capabilities(), initializeRequest.clientInfo());
                resultMono = this.initRequestHandler.handle(initializeRequest);
            } else {
                RequestHandler<?> handler = this.requestHandlers.get(request.method());
                if (handler == null) {
                    MethodNotFoundError error = getMethodNotFoundError(request.method());
                    return Mono.just(new McpSchema.JSONRPCResponse("2.0", request.id(), (Object)null, new McpSchema.JSONRPCResponse.JSONRPCError(-32601, error.message(), error.data())));
                }

                resultMono = this.exchangeSink.asMono().flatMap((exchange) -> handler.handle(exchange, request.params()));
            }

            return resultMono
                    .map(result -> new McpSchema.JSONRPCResponse("2.0", request.id(), result, null))
                    .onErrorResume(errorx -> {
                        String message = (errorx instanceof Exception) ? ((Exception) errorx).getMessage() : "Unexpected error";
                        return Mono.just(new McpSchema.JSONRPCResponse(
                                "2.0",
                                request.id(),
                                null,
                                new McpSchema.JSONRPCResponse.JSONRPCError(-32603, message, null)
                        ));
                    });
        });
    }

    private Mono<Void> handleIncomingNotification(McpSchema.JSONRPCNotification notification) {
        return Mono.defer(() -> {
            if ("notifications/initialized".equals(notification.method())) {
                this.state.lazySet(2);
                this.exchangeSink.tryEmitValue(new McpAsyncServerExchange(this, (McpSchema.ClientCapabilities)this.clientCapabilities.get(), (McpSchema.Implementation)this.clientInfo.get()));
                return this.initNotificationHandler.handle();
            } else {
                NotificationHandler handler = (NotificationHandler)this.notificationHandlers.get(notification.method());
                if (handler == null) {
                    logger.error("No handler registered for notification method: {}", notification.method());
                    return Mono.empty();
                } else {
                    return this.exchangeSink.asMono().flatMap((exchange) -> {
                        return handler.handle(exchange, notification.params());
                    });
                }
            }
        });
    }

    public class MethodNotFoundError {
        private final String method;
        private final String message;
        private final Object data;

        public MethodNotFoundError(String method, String message, Object data) {
            this.method = method;
            this.message = message;
            this.data = data;
        }

        public String method() {
            return method;
        }

        public String message() {
            return message;
        }

        public Object data() {
            return data;
        }
    }

    public MethodNotFoundError getMethodNotFoundError(String method) {
        if ("roots/list".equals(method)) {
            return new MethodNotFoundError(
                    method,
                    "Roots not supported",
                    Map.of("reason", "Client does not have roots capability")
            );
        } else {
            return new MethodNotFoundError(
                    method,
                    "Method not found: " + method,
                    null
            );
        }
    }

    public Mono<Void> closeGracefully() {
        return this.transport.closeGracefully();
    }

    public void close() {
        this.transport.close();
    }

    public interface InitRequestHandler {
        Mono<McpSchema.InitializeResult> handle(McpSchema.InitializeRequest initializeRequest);
    }

    public interface InitNotificationHandler {
        Mono<Void> handle();
    }

    public interface NotificationHandler {
        Mono<Void> handle(McpAsyncServerExchange exchange, Object params);
    }

    public interface RequestHandler<T> {
        Mono<T> handle(McpAsyncServerExchange exchange, Object params);
    }

    @FunctionalInterface
    public interface Factory {
        McpServerSession create(McpServerTransport sessionTransport);
    }
}
