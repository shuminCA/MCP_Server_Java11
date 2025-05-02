import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonschema.JsonSchema;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Converts a list of tool definitions from Map<String, Object> format into a list of McpSchema.Tool objects.
     * 
     * @param toolDefinitions A list of map objects containing tool definitions
     * @return List of McpSchema.Tool objects
     */
    public List<McpSchema.Tool> convertToMcpTools(List<Map<String, Object>> toolDefinitions) {
        List<McpSchema.Tool> mcpTools = new ArrayList<>();
        
        for (Map<String, Object> toolData : toolDefinitions) {
            String name = (String) toolData.getOrDefault("name", "");
            String description = (String) toolData.getOrDefault("description", "");
            
            // Create a JsonSchema object from the parameters
            McpSchema.JsonSchema inputSchema = createJsonSchema(toolData);
            
            // Create the Tool object
            McpSchema.Tool tool = new McpSchema.Tool(name, description, inputSchema);
            mcpTools.add(tool);
        }
        
        return mcpTools;
    }

    /**
     * Creates a JsonSchema object directly from the parameters in the tool definition.
     * 
     * @param toolData The tool definition map
     * @return A JsonSchema object representing the tool's input schema
     */
    private McpSchema.JsonSchema createJsonSchema(Map<String, Object> toolData) {
        // Default type for the schema is "object"
        String type = "object";
        
        // Create properties map for the schema
        Map<String, McpSchema.JsonSchema.SchemaProperty> properties = new HashMap<>();
        
        // Create required list for the schema
        List<String> required = new ArrayList<>();
        
        // Set additionalProperties to false by default
        Boolean additionalProperties = false;
        
        // Process parameters if they exist
        if (toolData.containsKey("parameters") && toolData.get("parameters") instanceof List) {
            List<Map<String, Object>> params = (List<Map<String, Object>>) toolData.get("parameters");
            
            for (Map<String, Object> param : params) {
                String paramName = (String) param.getOrDefault("name", "");
                String paramType = (String) param.getOrDefault("type", "STRING");
                boolean isRequired = (Boolean) param.getOrDefault("required", false);
                
                // Add to required list if needed
                if (isRequired) {
                    required.add(paramName);
                }
                
                // Convert the parameter type to JSON schema type
                String jsonSchemaType;
                switch (paramType.toUpperCase()) {
                    case "NUMBER":
                        jsonSchemaType = "number";
                        break;
                    case "INTEGER":
                        jsonSchemaType = "integer";
                        break;
                    case "BOOLEAN":
                        jsonSchemaType = "boolean";
                        break;
                    case "ARRAY":
                        jsonSchemaType = "array";
                        break;
                    case "OBJECT":
                        jsonSchemaType = "object";
                        break;
                    case "STRING":
                    default:
                        jsonSchemaType = "string";
                        break;
                }
                
                // Create a SchemaProperty for this parameter
                McpSchema.JsonSchema.SchemaProperty propertySchema = new McpSchema.JsonSchema.SchemaProperty(jsonSchemaType);
                properties.put(paramName, propertySchema);
            }
        }
        
        // Create and return the JsonSchema
        return new McpSchema.JsonSchema(type, properties, required, additionalProperties);
    }

    /**
 * Utility method to handle HTTP requests to Snaplogic pipelines.
 * This combines the common functionality from toolsListRequestHandler and toolsCallRequestHandler.
 * 
 * @param url The endpoint URL to call
 * @param bearerToken The authorization token
 * @param requestParams The parameters to send in the request body
 * @return The response body as a String
 * @throws IOException If an I/O error occurs
 * @throws InterruptedException If the operation is interrupted
 */
private String callSnaplogicPipeline(Object requestParams) throws IOException, InterruptedException {
    final String url = "http://localhost:8888/api/1/rest/slsched/feed/snaplogic/projects/MCP/MCP_server%20Task";
    final String bearerToken = "DgRmatae0bB7NOudup7DSOXOPZfN0Jvn";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "MCP Weather Demo (your-email@example.com)")
        .header("Authorization", "Bearer " + bearerToken)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            requestParams == null ? Map.of().toString() : objectMapper.writeValueAsString(requestParams)
        ))
        .build();

    // Configure HttpClient to follow redirects
    HttpClient clientWithRedirects = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    HttpResponse<String> response = clientWithRedirects.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
}

    private McpServerSession.RequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler() {
        return (exchange, params) -> {
            try {
                String responseBody = callSnaplogicPipeline(params);
                JsonNode responseJson = objectMapper.readTree(responseBody);
                System.out.println("Response from list tools pipeline: " + responseJson);
                
                List<McpSchema.Tool> mcpTools = convertToMcpTools(
                    objectMapper.convertValue(responseJson.get(0).get("tools"), List.class));
                return Mono.just(new McpSchema.ListToolsResult(mcpTools, null));
            } catch (Exception e) {
                e.printStackTrace();
                return Mono.error(new McpError("Failed to list tools: " + e.getMessage()));
            }
        };
    }

    private McpServerSession.RequestHandler<McpSchema.CallToolResult> toolsCallRequestHandler() {
        return (exchange, params) -> {    
            try {
                McpSchema.CallToolRequest callToolRequest = objectMapper.convertValue(params,
                    new TypeReference<>() {});
                Map<String, Object> requestParams = callToolRequest.arguments();
                requestParams.put("sl_tool_name", callToolRequest.name());
                
                String responseBody = callSnaplogicPipeline(requestParams);
                
                JsonNode responseJson = objectMapper.readTree(responseBody);
                System.out.println("Response from server pipeline: " + responseJson);
                
                String responseJsonString = responseJson.get(0).toString();
                return Mono.just(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(responseJsonString)), null));
            } catch (Exception e) {
                e.printStackTrace();
                return Mono.error(new McpError("Failed to call tool: " + e.getMessage()));
            }
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
