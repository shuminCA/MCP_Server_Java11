//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import jakarta.servlet.AsyncContext;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.annotation.WebServlet;
//import jakarta.servlet.http.HttpServlet;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
///**
// * This class implements the transport layer using HTTP and Server-Sent Events (SSE).
// * <p>
// * Key components:
// * HTTP servlet implementation for handling client connections
// * SSE event handling for server-to-client communication
// * Session management and message routing
// * Transport configuration and setup
// * <p>
// * It provides the network communication layer that allows clients to connect to the MCP server.
// */
//
//@WebServlet(asyncSupported = true)
//public class StreamableHttpServerTransportProvider extends HttpServlet {
//
//    private static final Logger logger = LoggerFactory.getLogger(StreamableHttpServerTransportProvider.class);
//    public static final String UTF_8 = "UTF-8";
//    public static final String APPLICATION_JSON = "application/json";
//    public static final String FAILED_TO_SEND_ERROR_RESPONSE = "Failed to send error response: {}";
//    public static final String DEFAULT_MCP_ENDPOINT = "/mcp";
//    public static final String MESSAGE_EVENT_TYPE = "message";
//    public static final String ENDPOINT_EVENT_TYPE = "endpoint";
//    public static final String DEFAULT_BASE_URL = "";
//    private final ObjectMapper objectMapper;
//    private final String baseUrl;
//    private final String endpoint;
//    private final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();
//    private final AtomicBoolean isClosing = new AtomicBoolean(false);
//    private McpServerSession.Factory sessionFactory;
//
//    public StreamableHttpServerTransportProvider(ObjectMapper objectMapper, String endpoint) {
//        this(objectMapper, DEFAULT_BASE_URL, endpoint);
//    }
//
//    public StreamableHttpServerTransportProvider(ObjectMapper objectMapper, String baseUrl,
//                                                 String endpoint) {
//        logger.info("Creating HttpServletSseServerTransportProvider");
//        this.objectMapper = objectMapper;
//        this.baseUrl = baseUrl;
//        this.endpoint = endpoint;
//    }
//
//    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
//        this.sessionFactory = sessionFactory;
//    }
//
//    public Mono<Void> notifyClients(String method, Map<String, Object> params) {
//        if (sessions.isEmpty()) {
//            logger.debug("No active sessions to broadcast message to");
//            return Mono.empty();
//        }
//
//        logger.debug("Attempting to broadcast message to {} active sessions", sessions.size());
//
//        return Flux.fromIterable(sessions.values())
//                .flatMap(session -> session.sendNotification(method, params)
//                        .doOnError(
//                                e -> logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage()))
//                        .onErrorComplete())
//                .then();
//    }
//
//    protected void doGet(HttpServletRequest request, HttpServletResponse response)
//            throws IOException {
//        logger.info("doGet");
//        String requestURI = request.getRequestURI();
//        if (!requestURI.endsWith(endpoint)) {
//            response.sendError(HttpServletResponse.SC_NOT_FOUND);
//            return;
//        }
//
//        if (isClosing.get()) {
//            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
//            return;
//        }
//
//        // sse Header
//        response.setContentType("text/event-stream");
//        response.setCharacterEncoding(UTF_8);
//        response.setHeader("Cache-Control", "no-cache");
//        response.setHeader("Connection", "keep-alive");
//        response.setHeader("Access-Control-Allow-Origin", "*");
//
//        String sessionId = UUID.randomUUID().toString();
//        AsyncContext asyncContext = request.startAsync();
//        asyncContext.setTimeout(0);
//        response.setHeader("mcp-session-id", sessionId);
//
//        PrintWriter writer = response.getWriter();
//        StreamableHttpMcpSessionTransport sessionTransport = new StreamableHttpMcpSessionTransport(sessionId, asyncContext,
//                writer);
//        McpServerSession session = sessionFactory.create(sessionTransport);
//        this.sessions.put(sessionId, session);
//        this.sendEvent(writer, ENDPOINT_EVENT_TYPE,
//                this.baseUrl + this.endpoint + "?sessionId=" + sessionId);
//    }
//
//    @Override
//    protected void doPost(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//        logger.info("doPost");
//
//        if (isClosing.get()) {
//            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
//            return;
//        }
//
//        String requestURI = request.getRequestURI();
//        if (!requestURI.endsWith(endpoint)) {
//            response.sendError(HttpServletResponse.SC_NOT_FOUND);
//            return;
//        }
//
//        // Get the session ID from the request parameter
//        String sessionId = request.getParameter("sessionId");
//        if (sessionId == null) {
//            response.setContentType(APPLICATION_JSON);
//            response.setCharacterEncoding(UTF_8);
//            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//            String jsonError = objectMapper.writeValueAsString(new McpError("Session ID missing in message endpoint"));
//            PrintWriter writer = response.getWriter();
//            writer.write(jsonError);
//            writer.flush();
//            return;
//        }
//
//        // Get the session from the sessions map
//        McpServerSession session = sessions.get(sessionId);
//        if (session == null) {
//            response.setContentType(APPLICATION_JSON);
//            response.setCharacterEncoding(UTF_8);
//            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
//            String jsonError = objectMapper.writeValueAsString(new McpError("Session not found: " + sessionId));
//            PrintWriter writer = response.getWriter();
//            writer.write(jsonError);
//            writer.flush();
//            return;
//        }
//
//        try {
//            BufferedReader reader = request.getReader();
//            StringBuilder body = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                body.append(line);
//            }
//
//            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body.toString());
//
//            // Process the message through the session's handle method
//            session.handle(message).block(); // Block for Servlet compatibility
//
//            response.setStatus(HttpServletResponse.SC_OK);
//        }
//        catch (Exception e) {
//            logger.error("Error processing message: {}", e.getMessage());
//            try {
//                McpError mcpError = new McpError(e.getMessage());
//                response.setContentType(APPLICATION_JSON);
//                response.setCharacterEncoding(UTF_8);
//                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//                String jsonError = objectMapper.writeValueAsString(mcpError);
//                PrintWriter writer = response.getWriter();
//                writer.write(jsonError);
//                writer.flush();
//            }
//            catch (IOException ex) {
//                logger.error(FAILED_TO_SEND_ERROR_RESPONSE, ex.getMessage());
//                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing message");
//            }
//        }
//    }
//
//    /**
//     * Initiates a graceful shutdown of the transport.
//     * <p>
//     * This method marks the transport as closing and closes all active client sessions.
//     * New connection attempts will be rejected during shutdown.
//     * @return A Mono that completes when all sessions have been closed
//     */
//    public Mono<Void> closeGracefully() {
//        isClosing.set(true);
//        logger.debug("Initiating graceful shutdown with {} active sessions", sessions.size());
//
//        return Flux.fromIterable(sessions.values()).flatMap(McpServerSession::closeGracefully).then();
//    }
//
//    /**
//     * Sends an SSE event to a client.
//     * @param writer The writer to send the event through
//     * @param eventType The type of event (message or endpoint)
//     * @param data The event data
//     * @throws IOException If an error occurs while writing the event
//     */
//    private void sendEvent(PrintWriter writer, String eventType, String data) throws IOException {
//        writer.write("event: " + eventType + "\n");
//        writer.write("data: " + data + "\n\n");
//        writer.flush();
//
//        if (writer.checkError()) {
//            throw new IOException("Client disconnected");
//        }
//    }
//
//    /**
//     * Cleans up resources when the servlet is being destroyed.
//     * <p>
//     * This method ensures a graceful shutdown by closing all client connections before
//     * calling the parent's destroy method.
//     */
//    @Override
//    public void destroy() {
//        closeGracefully().block();
//        super.destroy();
//    }
//
//    private class StreamableHttpMcpSessionTransport {
//
//        private final String sessionId;
//        private final AsyncContext asyncContext;
//        private final PrintWriter writer;
//
//        StreamableHttpMcpSessionTransport(String sessionId, AsyncContext asyncContext, PrintWriter writer) {
//            logger.info("Create a new session transport");
//            this.sessionId = sessionId;
//            this.asyncContext = asyncContext;
//            this.writer = writer;
//            logger.debug("Session transport {} initialized with SSE writer", sessionId);
//        }
//
//        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
//            return Mono.fromRunnable(() -> {
//                try {
//                    String jsonText = objectMapper.writeValueAsString(message);
//                    sendEvent(writer, MESSAGE_EVENT_TYPE, jsonText);
//                    logger.debug("Message sent to session {}", sessionId);
//                }
//                catch (Exception e) {
//                    logger.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
//                    sessions.remove(sessionId);
//                    asyncContext.complete();
//                }
//            });
//        }
//
//        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
//            return objectMapper.convertValue(data, typeRef);
//        }
//
//        public Mono<Void> closeGracefully() {
//            return Mono.fromRunnable(() -> {
//                logger.debug("Closing session transport: {}", sessionId);
//                try {
//                    sessions.remove(sessionId);
//                    asyncContext.complete();
//                    logger.debug("Successfully completed async context for session {}", sessionId);
//                }
//                catch (Exception e) {
//                    logger.warn("Failed to complete async context for session {}: {}", sessionId, e.getMessage());
//                }
//            });
//        }
//
//        public void close() {
//            try {
//                sessions.remove(sessionId);
//                asyncContext.complete();
//                logger.debug("Successfully completed async context for session {}", sessionId);
//            }
//            catch (Exception e) {
//                logger.warn("Failed to complete async context for session {}: {}", sessionId, e.getMessage());
//            }
//        }
//    }
//
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    public static class Builder {
//
//        private ObjectMapper objectMapper = new ObjectMapper();
//
//        private String baseUrl = DEFAULT_BASE_URL;
//
//        private String endpoint = DEFAULT_MCP_ENDPOINT;
//
//        public Builder() {
//        }
//
//        public Builder objectMapper(ObjectMapper objectMapper) {
//            if (objectMapper == null) {
//                throw new IllegalArgumentException("ObjectMapper must not be null");
//            }
//            this.objectMapper = objectMapper;
//            return this;
//        }
//
//        public Builder baseUrl(String baseUrl) {
//            if (baseUrl == null) {
//                throw new IllegalArgumentException("BaseUrl must not be null");
//            }
//            this.baseUrl = baseUrl;
//            return this;
//        }
//
//        public Builder endpoint(String sseEndpoint) {
//            if (sseEndpoint == null) {
//                throw new IllegalArgumentException("SSEEndpoint must not be null");
//            }
//            this.endpoint = sseEndpoint;
//            return this;
//        }
//
//        /**
//         * Builds a new instance of HttpServletSseServerTransportProvider with the
//         * configured settings.
//         * @return A new HttpServletSseServerTransportProvider instance
//         * @throws IllegalStateException if objectMapper or messageEndpoint is not set
//         */
//        public StreamableHttpServerTransportProvider build() {
//            if (objectMapper == null) {
//                throw new IllegalStateException("ObjectMapper must be set");
//            }
//            if (endpoint == null) {
//                throw new IllegalStateException("Endpoint must be set");
//            }
//            return new StreamableHttpServerTransportProvider(objectMapper, baseUrl, endpoint);
//        }
//    }
//}
