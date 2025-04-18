package modelcontextprotocol.server.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import modelcontextprotocol.spec.McpError;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.spec.McpServerSession;
import modelcontextprotocol.spec.McpServerTransport;
import modelcontextprotocol.spec.McpServerTransportProvider;
import modelcontextprotocol.util.Assert;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebServlet(
        asyncSupported = true
)
public class HttpServletSseServerTransportProvider extends HttpServlet implements McpServerTransportProvider {
    private static final Logger logger = LoggerFactory.getLogger(HttpServletSseServerTransportProvider.class);
    public static final String UTF_8 = "UTF-8";
    public static final String APPLICATION_JSON = "application/json";
    public static final String FAILED_TO_SEND_ERROR_RESPONSE = "Failed to send error response: {}";
    public static final String DEFAULT_SSE_ENDPOINT = "/sse";
    public static final String MESSAGE_EVENT_TYPE = "message";
    public static final String ENDPOINT_EVENT_TYPE = "endpoint";
    public static final String DEFAULT_BASE_URL = "";
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String messageEndpoint;
    private final String sseEndpoint;
    private final Map<String, McpServerSession> sessions;
    private final AtomicBoolean isClosing;
    private McpServerSession.Factory sessionFactory;

    public HttpServletSseServerTransportProvider(ObjectMapper objectMapper, String messageEndpoint, String sseEndpoint) {
        this(objectMapper, "", messageEndpoint, sseEndpoint);
    }

    public HttpServletSseServerTransportProvider(ObjectMapper objectMapper, String baseUrl, String messageEndpoint, String sseEndpoint) {
        this.sessions = new ConcurrentHashMap();
        this.isClosing = new AtomicBoolean(false);
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.messageEndpoint = messageEndpoint;
        this.sseEndpoint = sseEndpoint;
    }

    public HttpServletSseServerTransportProvider(ObjectMapper objectMapper, String messageEndpoint) {
        this(objectMapper, messageEndpoint, "/sse");
    }

    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Mono<Void> notifyClients(String method, Object params) {
        if (this.sessions.isEmpty()) {
            logger.debug("No active sessions to broadcast message to");
            return Mono.empty();
        } else {
            logger.debug("Attempting to broadcast message to {} active sessions", this.sessions.size());
            return Flux.fromIterable(this.sessions.values()).flatMap((session) -> {
                return session.sendNotification(method, params).doOnError((e) -> {
                    logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                }).onErrorComplete();
            }).then();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        if (!requestURI.endsWith(this.sseEndpoint)) {
            response.sendError(404);
        } else if (this.isClosing.get()) {
            response.sendError(503, "Server is shutting down");
        } else {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Access-Control-Allow-Origin", "*");
            String sessionId = UUID.randomUUID().toString();
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0L);
            PrintWriter writer = response.getWriter();
            HttpServletMcpSessionTransport sessionTransport = new HttpServletMcpSessionTransport(sessionId, asyncContext, writer);
            McpServerSession session = this.sessionFactory.create(sessionTransport);
            this.sessions.put(sessionId, session);
            this.sendEvent(writer, "endpoint", this.baseUrl + this.messageEndpoint + "?sessionId=" + sessionId);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (this.isClosing.get()) {
            response.sendError(503, "Server is shutting down");
        } else {
            String requestURI = request.getRequestURI();
            if (!requestURI.endsWith(this.messageEndpoint)) {
                response.sendError(404);
            } else {
                String sessionId = request.getParameter("sessionId");
                if (sessionId == null) {
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.setStatus(400);
                    String jsonError = this.objectMapper.writeValueAsString(new McpError("Session ID missing in message endpoint"));
                    PrintWriter writer = response.getWriter();
                    writer.write(jsonError);
                    writer.flush();
                } else {
                    McpServerSession session = (McpServerSession)this.sessions.get(sessionId);
                    if (session == null) {
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.setStatus(404);
                        String jsonError = this.objectMapper.writeValueAsString(new McpError("Session not found: " + sessionId));
                        PrintWriter writer = response.getWriter();
                        writer.write(jsonError);
                        writer.flush();
                    } else {
                        String jsonError;
                        try {
                            BufferedReader reader = request.getReader();
                            StringBuilder body = new StringBuilder();

                            while((jsonError = reader.readLine()) != null) {
                                body.append(jsonError);
                            }

                            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.objectMapper, body.toString());
                            session.handle(message).block();
                            response.setStatus(200);
                        } catch (Exception var11) {
                            Exception e = var11;
                            logger.error("Error processing message: {}", e.getMessage());

                            try {
                                McpError mcpError = new McpError(e.getMessage());
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.setStatus(500);
                                jsonError = this.objectMapper.writeValueAsString(mcpError);
                                PrintWriter writer = response.getWriter();
                                writer.write(jsonError);
                                writer.flush();
                            } catch (IOException var10) {
                                IOException ex = var10;
                                logger.error("Failed to send error response: {}", ex.getMessage());
                                response.sendError(500, "Error processing message");
                            }
                        }

                    }
                }
            }
        }
    }

    public Mono<Void> closeGracefully() {
        this.isClosing.set(true);
        logger.debug("Initiating graceful shutdown with {} active sessions", this.sessions.size());
        return Flux.fromIterable(this.sessions.values()).flatMap(McpServerSession::closeGracefully).then();
    }

    private void sendEvent(PrintWriter writer, String eventType, String data) throws IOException {
        writer.write("event: " + eventType + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            throw new IOException("Client disconnected");
        }
    }

    public void destroy() {
        this.closeGracefully().block();
        super.destroy();
    }

    public static Builder builder() {
        return new Builder();
    }

    private class HttpServletMcpSessionTransport implements McpServerTransport {
        private final String sessionId;
        private final AsyncContext asyncContext;
        private final PrintWriter writer;

        HttpServletMcpSessionTransport(String sessionId, AsyncContext asyncContext, PrintWriter writer) {
            this.sessionId = sessionId;
            this.asyncContext = asyncContext;
            this.writer = writer;
            HttpServletSseServerTransportProvider.logger.debug("Session transport {} initialized with SSE writer", sessionId);
        }

        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.fromRunnable(() -> {
                try {
                    String jsonText = HttpServletSseServerTransportProvider.this.objectMapper.writeValueAsString(message);
                    HttpServletSseServerTransportProvider.this.sendEvent(this.writer, "message", jsonText);
                    HttpServletSseServerTransportProvider.logger.debug("Message sent to session {}", this.sessionId);
                } catch (Exception var3) {
                    Exception e = var3;
                    HttpServletSseServerTransportProvider.logger.error("Failed to send message to session {}: {}", this.sessionId, e.getMessage());
                    HttpServletSseServerTransportProvider.this.sessions.remove(this.sessionId);
                    this.asyncContext.complete();
                }

            });
        }

        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return HttpServletSseServerTransportProvider.this.objectMapper.convertValue(data, typeRef);
        }

        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(() -> {
                HttpServletSseServerTransportProvider.logger.debug("Closing session transport: {}", this.sessionId);

                try {
                    HttpServletSseServerTransportProvider.this.sessions.remove(this.sessionId);
                    this.asyncContext.complete();
                    HttpServletSseServerTransportProvider.logger.debug("Successfully completed async context for session {}", this.sessionId);
                } catch (Exception var2) {
                    Exception e = var2;
                    HttpServletSseServerTransportProvider.logger.warn("Failed to complete async context for session {}: {}", this.sessionId, e.getMessage());
                }

            });
        }

        public void close() {
            try {
                HttpServletSseServerTransportProvider.this.sessions.remove(this.sessionId);
                this.asyncContext.complete();
                HttpServletSseServerTransportProvider.logger.debug("Successfully completed async context for session {}", this.sessionId);
            } catch (Exception var2) {
                Exception e = var2;
                HttpServletSseServerTransportProvider.logger.warn("Failed to complete async context for session {}: {}", this.sessionId, e.getMessage());
            }

        }
    }

    public static class Builder {
        private ObjectMapper objectMapper = new ObjectMapper();
        private String baseUrl = "";
        private String messageEndpoint;
        private String sseEndpoint = "/sse";

        public Builder() {
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            Assert.notNull(objectMapper, "ObjectMapper must not be null");
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            Assert.notNull(baseUrl, "Base URL must not be null");
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder messageEndpoint(String messageEndpoint) {
            Assert.hasText(messageEndpoint, "Message endpoint must not be empty");
            this.messageEndpoint = messageEndpoint;
            return this;
        }

        public Builder sseEndpoint(String sseEndpoint) {
            Assert.hasText(sseEndpoint, "SSE endpoint must not be empty");
            this.sseEndpoint = sseEndpoint;
            return this;
        }

        public HttpServletSseServerTransportProvider build() {
            if (this.objectMapper == null) {
                throw new IllegalStateException("ObjectMapper must be set");
            } else if (this.messageEndpoint == null) {
                throw new IllegalStateException("MessageEndpoint must be set");
            } else {
                return new HttpServletSseServerTransportProvider(this.objectMapper, this.baseUrl, this.messageEndpoint, this.sseEndpoint);
            }
        }
    }
}

