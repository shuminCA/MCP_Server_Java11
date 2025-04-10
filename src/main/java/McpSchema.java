/*
 * Copyright 2024-2024 the original author or authors.
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on the <a href="http://www.jsonrpc.org/specification">JSON-RPC 2.0
 * specification</a> and the <a href=
 * "https://github.com/modelcontextprotocol/specification/blob/main/schema/2024-11-05/schema.ts">Model
 * Context Protocol Schema</a>.
 *
 * @author Christian Tzolov
 */
public final class McpSchema {

    private static final Logger logger = LoggerFactory.getLogger(McpSchema.class);

    public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

    public static final String JSONRPC_VERSION = "2.0";

    // ---------------------------
    // Method Names
    // ---------------------------

    // Lifecycle Methods
    public static final String METHOD_INITIALIZE = "initialize";

    public static final String METHOD_NOTIFICATION_INITIALIZED = "notifications/initialized";

    public static final String METHOD_PING = "ping";

    // Tool Methods
    public static final String METHOD_TOOLS_LIST = "tools/list";

    public static final String METHOD_TOOLS_CALL = "tools/call";

    public static final String METHOD_NOTIFICATION_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";

    // Resources Methods
    public static final String METHOD_RESOURCES_LIST = "resources/list";

    public static final String METHOD_RESOURCES_READ = "resources/read";

    public static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";

    // Prompt Methods
    public static final String METHOD_PROMPT_LIST = "prompts/list";

    public static final String METHOD_PROMPT_GET = "prompts/get";

    // Logging Methods
    public static final String METHOD_LOGGING_SET_LEVEL = "logging/setLevel";

    // Roots Methods
    public static final String METHOD_ROOTS_LIST = "roots/list";

    public static final String METHOD_NOTIFICATION_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";

    // Sampling Methods
    public static final String METHOD_SAMPLING_CREATE_MESSAGE = "sampling/createMessage";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ---------------------------
    // JSON-RPC Error Codes
    // ---------------------------

    /**
     * Standard error codes used in MCP JSON-RPC responses.
     */
    public static final class ErrorCodes {
        /**
         * The method does not exist / is not available.
         */
        public static final int METHOD_NOT_FOUND = -32601;

        /**
         * Internal JSON-RPC error.
         */
        public static final int INTERNAL_ERROR = -32603;

    }

    // Replacing sealed interface with regular interface
    public interface Request {

    }

    // Fix diamond operator with anonymous class by explicitly specifying type parameters
    private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<HashMap<String, Object>>() {
    };

    /**
     * Deserializes a JSON string into a JSONRPCMessage object.
     *
     * @param objectMapper The ObjectMapper instance to use for deserialization
     * @param jsonText     The JSON string to deserialize
     * @return A JSONRPCMessage instance using either the {@link JSONRPCRequest},
     * {@link JSONRPCNotification}, or {@link JSONRPCResponse} classes.
     * @throws IOException              If there's an error during deserialization
     * @throws IllegalArgumentException If the JSON structure doesn't match any known
     *                                  message type
     */
    public static JSONRPCMessage deserializeJsonRpcMessage(ObjectMapper objectMapper, String jsonText)
            throws IOException {

        logger.debug("Received JSON message: {}", jsonText);

        var map = objectMapper.readValue(jsonText, MAP_TYPE_REF);

        // Determine message type based on specific JSON structure
        if (map.containsKey("method") && map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCRequest.class);
        } else if (map.containsKey("method") && !map.containsKey("id")) {
            return objectMapper.convertValue(map, JSONRPCNotification.class);
        } else if (map.containsKey("result") || map.containsKey("error")) {
            return objectMapper.convertValue(map, JSONRPCResponse.class);
        }

        throw new IllegalArgumentException("Cannot deserialize JSONRPCMessage: " + jsonText);
    }

    // ---------------------------
    // JSON-RPC Message Types
    // ---------------------------
    // Replacing sealed interface with regular interface
    public interface JSONRPCMessage {
        String jsonrpc();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCRequest implements JSONRPCMessage { // @formatter:off
        @JsonProperty("jsonrpc") private final String jsonrpc;
        @JsonProperty("method") private final String method;
        @JsonProperty("id") private final Object id;
        @JsonProperty("params") private final Object params;

        public JSONRPCRequest(
                @JsonProperty("jsonrpc") String jsonrpc,
                @JsonProperty("method") String method,
                @JsonProperty("id") Object id,
                @JsonProperty("params") Object params) {
            this.jsonrpc = jsonrpc;
            this.method = method;
            this.id = id;
            this.params = params;
        }

        public String jsonrpc() {
            return jsonrpc;
        }

        public String method() {
            return method;
        }

        public Object id() {
            return id;
        }

        public Object params() {
            return params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JSONRPCRequest that = (JSONRPCRequest) o;
            return Objects.equals(jsonrpc, that.jsonrpc) &&
                   Objects.equals(method, that.method) &&
                   Objects.equals(id, that.id) &&
                   Objects.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jsonrpc, method, id, params);
        }

        @Override
        public String toString() {
            return "JSONRPCRequest{" +
                   "jsonrpc='" + jsonrpc + '\'' +
                   ", method='" + method + '\'' +
                   ", id=" + id +
                   ", params=" + params +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCNotification implements JSONRPCMessage { // @formatter:off
        @JsonProperty("jsonrpc") private final String jsonrpc;
        @JsonProperty("method") private final String method;
        @JsonProperty("params") private final Map<String, Object> params;

        public JSONRPCNotification(
                @JsonProperty("jsonrpc") String jsonrpc,
                @JsonProperty("method") String method,
                @JsonProperty("params") Map<String, Object> params) {
            this.jsonrpc = jsonrpc;
            this.method = method;
            this.params = params;
        }

        public String jsonrpc() {
            return jsonrpc;
        }

        public String method() {
            return method;
        }

        public Map<String, Object> params() {
            return params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JSONRPCNotification that = (JSONRPCNotification) o;
            return Objects.equals(jsonrpc, that.jsonrpc) &&
                   Objects.equals(method, that.method) &&
                   Objects.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jsonrpc, method, params);
        }

        @Override
        public String toString() {
            return "JSONRPCNotification{" +
                   "jsonrpc='" + jsonrpc + '\'' +
                   ", method='" + method + '\'' +
                   ", params=" + params +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONRPCResponse implements JSONRPCMessage { // @formatter:off
        @JsonProperty("jsonrpc") private final String jsonrpc;
        @JsonProperty("id") private final Object id;
        @JsonProperty("result") private final Object result;
        @JsonProperty("error") private final McpSchema.JSONRPCResponse.JSONRPCError error;

        public JSONRPCResponse(
                @JsonProperty("jsonrpc") String jsonrpc,
                @JsonProperty("id") Object id,
                @JsonProperty("result") Object result,
                @JsonProperty("error") McpSchema.JSONRPCResponse.JSONRPCError error) {
            this.jsonrpc = jsonrpc;
            this.id = id;
            this.result = result;
            this.error = error;
        }

        public String jsonrpc() {
            return jsonrpc;
        }

        public Object id() {
            return id;
        }

        public Object result() {
            return result;
        }

        public McpSchema.JSONRPCResponse.JSONRPCError error() {
            return error;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JSONRPCResponse that = (JSONRPCResponse) o;
            return Objects.equals(jsonrpc, that.jsonrpc) &&
                   Objects.equals(id, that.id) &&
                   Objects.equals(result, that.result) &&
                   Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jsonrpc, id, result, error);
        }

        @Override
        public String toString() {
            return "JSONRPCResponse{" +
                   "jsonrpc='" + jsonrpc + '\'' +
                   ", id=" + id +
                   ", result=" + result +
                   ", error=" + error +
                   '}';
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class JSONRPCError {
            @JsonProperty("code") private final int code;
            @JsonProperty("message") private final String message;
            @JsonProperty("data") private final Object data;

            public JSONRPCError(
                    @JsonProperty("code") int code,
                    @JsonProperty("message") String message,
                    @JsonProperty("data") Object data) {
                this.code = code;
                this.message = message;
                this.data = data;
            }

            public int code() {
                return code;
            }

            public String message() {
                return message;
            }

            public Object data() {
                return data;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                JSONRPCError that = (JSONRPCError) o;
                return code == that.code &&
                       Objects.equals(message, that.message) &&
                       Objects.equals(data, that.data);
            }

            @Override
            public int hashCode() {
                return Objects.hash(code, message, data);
            }

            @Override
            public String toString() {
                return "JSONRPCError{" +
                       "code=" + code +
                       ", message='" + message + '\'' +
                       ", data=" + data +
                       '}';
            }
        }
    }// @formatter:on

    // ---------------------------
    // Initialization
    // ---------------------------
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeRequest implements Request { // @formatter:off
        @JsonProperty("protocolVersion") private final String protocolVersion;
        @JsonProperty("capabilities") private final ClientCapabilities capabilities;
        @JsonProperty("clientInfo") private final Implementation clientInfo;

        public InitializeRequest(
                @JsonProperty("protocolVersion") String protocolVersion,
                @JsonProperty("capabilities") ClientCapabilities capabilities,
                @JsonProperty("clientInfo") Implementation clientInfo) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.clientInfo = clientInfo;
        }

        public String protocolVersion() {
            return protocolVersion;
        }

        public ClientCapabilities capabilities() {
            return capabilities;
        }

        public Implementation clientInfo() {
            return clientInfo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InitializeRequest that = (InitializeRequest) o;
            return Objects.equals(protocolVersion, that.protocolVersion) &&
                   Objects.equals(capabilities, that.capabilities) &&
                   Objects.equals(clientInfo, that.clientInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocolVersion, capabilities, clientInfo);
        }

        @Override
        public String toString() {
            return "InitializeRequest{" +
                   "protocolVersion='" + protocolVersion + '\'' +
                   ", capabilities=" + capabilities +
                   ", clientInfo=" + clientInfo +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeResult { // @formatter:off
        @JsonProperty("protocolVersion") private final String protocolVersion;
        @JsonProperty("capabilities") private final ServerCapabilities capabilities;
        @JsonProperty("serverInfo") private final Implementation serverInfo;
        @JsonProperty("instructions") private final String instructions;

        public InitializeResult(
                @JsonProperty("protocolVersion") String protocolVersion,
                @JsonProperty("capabilities") ServerCapabilities capabilities,
                @JsonProperty("serverInfo") Implementation serverInfo,
                @JsonProperty("instructions") String instructions) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.serverInfo = serverInfo;
            this.instructions = instructions;
        }

        public String protocolVersion() {
            return protocolVersion;
        }

        public ServerCapabilities capabilities() {
            return capabilities;
        }

        public Implementation serverInfo() {
            return serverInfo;
        }

        public String instructions() {
            return instructions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InitializeResult that = (InitializeResult) o;
            return Objects.equals(protocolVersion, that.protocolVersion) &&
                   Objects.equals(capabilities, that.capabilities) &&
                   Objects.equals(serverInfo, that.serverInfo) &&
                   Objects.equals(instructions, that.instructions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocolVersion, capabilities, serverInfo, instructions);
        }

        @Override
        public String toString() {
            return "InitializeResult{" +
                   "protocolVersion='" + protocolVersion + '\'' +
                   ", capabilities=" + capabilities +
                   ", serverInfo=" + serverInfo +
                   ", instructions='" + instructions + '\'' +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientCapabilities {

        @JsonProperty("experimental")
        private final Map<String, Object> experimental;

        @JsonProperty("roots")
        private final RootCapabilities roots;

        @JsonProperty("sampling")
        private final Sampling sampling;

        @JsonCreator
        public ClientCapabilities(
                @JsonProperty("experimental") Map<String, Object> experimental,
                @JsonProperty("roots") RootCapabilities roots,
                @JsonProperty("sampling") Sampling sampling) {
            this.experimental = experimental;
            this.roots = roots;
            this.sampling = sampling;
        }

        public Map<String, Object> experimental() {
            return experimental;
        }

        public RootCapabilities roots() {
            return roots;
        }

        public Sampling sampling() {
            return sampling;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RootCapabilities {

            @JsonProperty("listChanged")
            private final Boolean listChanged;

            @JsonCreator
            public RootCapabilities(@JsonProperty("listChanged") Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class Sampling {

            public Sampling() {
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Map<String, Object> experimental;
            private RootCapabilities roots;
            private Sampling sampling;

            public Builder experimental(Map<String, Object> experimental) {
                this.experimental = experimental;
                return this;
            }

            public Builder roots(Boolean listChanged) {
                this.roots = new RootCapabilities(listChanged);
                return this;
            }

            public Builder sampling() {
                this.sampling = new Sampling();
                return this;
            }

            public ClientCapabilities build() {
                return new ClientCapabilities(experimental, roots, sampling);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerCapabilities {

        @JsonProperty("logging")
        private final McpSchema.ServerCapabilities.LoggingCapabilities logging;

        @JsonProperty("tools")
        private final McpSchema.ServerCapabilities.ToolCapabilities tools;

        public ServerCapabilities(
                McpSchema.ServerCapabilities.LoggingCapabilities logging,
                McpSchema.ServerCapabilities.ToolCapabilities tools) {
            this.logging = logging;
            this.tools = tools;
        }

        public McpSchema.ServerCapabilities.LoggingCapabilities logging() {
            return logging;
        }

        public McpSchema.ServerCapabilities.ToolCapabilities tools() {
            return tools;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class LoggingCapabilities {

            public LoggingCapabilities() {
            }
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public static class ToolCapabilities {

            @JsonProperty("listChanged")
            private final Boolean listChanged;

            public ToolCapabilities(Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean listChanged() {
                return listChanged;
            }
        }

        public static McpSchema.ServerCapabilities.Builder builder() {
            return new McpSchema.ServerCapabilities.Builder();
        }

        public static class Builder {

            private Map<String, Object> experimental;
            private McpSchema.ServerCapabilities.LoggingCapabilities logging = new McpSchema.ServerCapabilities.LoggingCapabilities();
            private McpSchema.ServerCapabilities.ToolCapabilities tools;

            public McpSchema.ServerCapabilities.Builder logging() {
                this.logging = new McpSchema.ServerCapabilities.LoggingCapabilities();
                return this;
            }

            public McpSchema.ServerCapabilities.Builder tools(Boolean listChanged) {
                this.tools = new McpSchema.ServerCapabilities.ToolCapabilities(listChanged);
                return this;
            }

            public McpSchema.ServerCapabilities build() {
                return new McpSchema.ServerCapabilities(logging, tools);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Implementation { // @formatter:off
        @JsonProperty("name") private final String name;
        @JsonProperty("version") private final String version;

        public Implementation(
                @JsonProperty("name") String name,
                @JsonProperty("version") String version) {
            this.name = name;
            this.version = version;
        }

        public String name() {
            return name;
        }

        public String version() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Implementation that = (Implementation) o;
            return Objects.equals(name, that.name) &&
                   Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, version);
        }

        @Override
        public String toString() {
            return "Implementation{" +
                   "name='" + name + '\'' +
                   ", version='" + version + '\'' +
                   '}';
        }
    } // @formatter:on

    // Existing Enums and Base Types (from previous implementation)
    public enum Role {// @formatter:off
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT
    }// @formatter:on

    // ---------------------------
    // Resource Interfaces
    // ---------------------------

    /**
     * Base for objects that include optional annotations for the client. The client can
     * use annotations to inform how objects are used or displayed
     */
    public interface Annotated {

        Annotations annotations();
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Annotations { // @formatter:off
        @JsonProperty("audience") private final List<Role> audience;
        @JsonProperty("priority") private final Double priority;

        public Annotations(
                @JsonProperty("audience") List<Role> audience,
                @JsonProperty("priority") Double priority) {
            this.audience = audience;
            this.priority = priority;
        }

        public List<Role> audience() {
            return audience;
        }

        public Double priority() {
            return priority;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Annotations that = (Annotations) o;
            return Objects.equals(audience, that.audience) &&
                   Objects.equals(priority, that.priority);
        }

        @Override
        public int hashCode() {
            return Objects.hash(audience, priority);
        }

        @Override
        public String toString() {
            return "Annotations{" +
                   "audience=" + audience +
                   ", priority=" + priority +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource implements Annotated { // @formatter:off
        @JsonProperty("uri") private final String uri;
        @JsonProperty("name") private final String name;
        @JsonProperty("description") private final String description;
        @JsonProperty("mimeType") private final String mimeType;
        @JsonProperty("annotations") private final Annotations annotations;

        public Resource(
                @JsonProperty("uri") String uri,
                @JsonProperty("name") String name,
                @JsonProperty("description") String description,
                @JsonProperty("mimeType") String mimeType,
                @JsonProperty("annotations") Annotations annotations) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }

        public String uri() {
            return uri;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public String mimeType() {
            return mimeType;
        }

        @Override
        public Annotations annotations() {
            return annotations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Resource that = (Resource) o;
            return Objects.equals(uri, that.uri) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(description, that.description) &&
                   Objects.equals(mimeType, that.mimeType) &&
                   Objects.equals(annotations, that.annotations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, name, description, mimeType, annotations);
        }

        @Override
        public String toString() {
            return "Resource{" +
                   "uri='" + uri + '\'' +
                   ", name='" + name + '\'' +
                   ", description='" + description + '\'' +
                   ", mimeType='" + mimeType + '\'' +
                   ", annotations=" + annotations +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceTemplate implements Annotated { // @formatter:off
        @JsonProperty("uriTemplate") private final String uriTemplate;
        @JsonProperty("name") private final String name;
        @JsonProperty("description") private final String description;
        @JsonProperty("mimeType") private final String mimeType;
        @JsonProperty("annotations") private final Annotations annotations;

        public ResourceTemplate(
                @JsonProperty("uriTemplate") String uriTemplate,
                @JsonProperty("name") String name,
                @JsonProperty("description") String description,
                @JsonProperty("mimeType") String mimeType,
                @JsonProperty("annotations") Annotations annotations) {
            this.uriTemplate = uriTemplate;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.annotations = annotations;
        }

        public String uriTemplate() {
            return uriTemplate;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public String mimeType() {
            return mimeType;
        }

        @Override
        public Annotations annotations() {
            return annotations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ResourceTemplate that = (ResourceTemplate) o;
            return Objects.equals(uriTemplate, that.uriTemplate) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(description, that.description) &&
                   Objects.equals(mimeType, that.mimeType) &&
                   Objects.equals(annotations, that.annotations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uriTemplate, name, description, mimeType, annotations);
        }

        @Override
        public String toString() {
            return "ResourceTemplate{" +
                   "uriTemplate='" + uriTemplate + '\'' +
                   ", name='" + name + '\'' +
                   ", description='" + description + '\'' +
                   ", mimeType='" + mimeType + '\'' +
                   ", annotations=" + annotations +
                   '}';
        }
    } // @formatter:on

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResourcesResult { // @formatter:off
        @JsonProperty("resources") private final List<Resource> resources;
        @JsonProperty("nextCursor") private final String nextCursor;

        public ListResourcesResult(
                @JsonProperty("resources") List<Resource> resources,
                @JsonProperty("nextCursor") String nextCursor) {
            this.resources = resources;
            this.nextCursor = nextCursor;
        }

        public List<Resource> resources() {
            return resources;
        }

        public String nextCursor() {
            return nextCursor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListResourcesResult that = (ListResourcesResult) o;
            return Objects.equals(resources, that.resources) &&
                   Objects.equals(nextCursor, that.nextCursor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resources, nextCursor);
        }

        @Override
        public String toString() {
            return "ListResourcesResult{" +
                   "resources=" + resources +
                   ", nextCursor='" + nextCursor + '\'' +
                   '}';
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallToolResult {

        @JsonProperty("content")
        private final List<Content> content;

        @JsonProperty("isError")
        private final Boolean isError;

        public CallToolResult(List<Content> content, Boolean isError) {
            this.content = content;
            this.isError = isError;
        }

        /**
         * Creates a new instance of {@link CallToolResult} with a string containing the tool result.
         *
         * @param content The content of the tool result. This will be mapped to a one-sized list
         *                with a {@link TextContent} element.
         * @param isError If true, indicates that the tool execution failed and the content contains error information.
         *                If false or absent, indicates successful execution.
         */
        public CallToolResult(String content, Boolean isError) {
            this.content = new ArrayList<>();
            this.content.add(new TextContent(content));
            this.isError = isError;
        }

        public List<Content> getContent() {
            return content;
        }

        public Boolean getIsError() {
            return isError;
        }

        /**
         * Creates a builder for {@link CallToolResult}.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for {@link CallToolResult}.
         */
        public static class Builder {
            private List<Content> content = new ArrayList<>();
            private Boolean isError;

            /**
             * Sets the content list for the tool result.
             *
             * @param content the content list
             * @return this builder
             */
            public Builder content(List<Content> content) {
                if (content != null) {
                    this.content.addAll(content);
                }
                this.content = content;
                return this;
            }

            /**
             * Sets the text content for the tool result.
             *
             * @param textContent the text content
             * @return this builder
             */
            public Builder textContent(List<String> textContent) {
                if (textContent != null) {
                    throw new IllegalArgumentException("textContent cannot be null");
                }
                textContent.stream()
                        .map(TextContent::new)
                        .forEach(this.content::add);
                return this;
            }

            /**
             * Adds a content item to the tool result.
             *
             * @param contentItem the content item to add
             * @return this builder
             */
            public Builder addContent(Content contentItem) {
                if (contentItem != null) {

                }
                if (this.content == null) {
                    this.content = new ArrayList<>();
                }
                this.content.add(contentItem);
                return this;
            }

            /**
             * Adds a text content item to the tool result.
             *
             * @param text the text content
             * @return this builder
             */
            public Builder addTextContent(String text) {
                if (text != null) {
                    throw new IllegalArgumentException("text cannot be null");
                }
                return addContent(new TextContent(text));
            }

            /**
             * Sets whether the tool execution resulted in an error.
             *
             * @param isError true if the tool execution failed, false otherwise
             * @return this builder
             */
            public Builder isError(Boolean isError) {
                if (isError != null) {
                    throw new IllegalArgumentException("isError cannot be null");
                }
                this.isError = isError;
                return this;
            }

            /**
             * Builds a new {@link CallToolResult} instance.
             *
             * @return a new CallToolResult instance
             */
            public CallToolResult build() {
                return new CallToolResult(content, isError);
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextContent.class, name = "text"),
            @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
            @JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource")
    })
    public interface Content {
        default String type() {
            if (this instanceof TextContent) {
                return "text";
            } else if (this instanceof ImageContent) {
                return "image";
            } else if (this instanceof EmbeddedResource) {
                return "resource";
            }
            throw new IllegalArgumentException("Unknown content type: " + this);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextContent implements Content {

        @JsonProperty("audience")
        private final List<Role> audience;

        @JsonProperty("priority")
        private final Double priority;

        @JsonProperty("text")
        private final String text;

        public TextContent(List<Role> audience, Double priority, String text) {
            this.audience = audience;
            this.priority = priority;
            this.text = text;
        }

        public TextContent(String content) {
            this(null, null, content);
        }

        public List<Role> getAudience() {
            return audience;
        }

        public Double getPriority() {
            return priority;
        }

        public String getText() {
            return text;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class ImageContent implements Content {

        @JsonProperty("audience")
        private final List<Role> audience;

        @JsonProperty("priority")
        private final Double priority;

        @JsonProperty("data")
        private final String data;

        @JsonProperty("mimeType")
        private final String mimeType;

        public ImageContent(List<Role> audience, Double priority, String data, String mimeType) {
            this.audience = audience;
            this.priority = priority;
            this.data = data;
            this.mimeType = mimeType;
        }

        public List<Role> getAudience() {
            return audience;
        }

        public Double getPriority() {
            return priority;
        }

        public String getData() {
            return data;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class EmbeddedResource implements Content {

        @JsonProperty("audience")
        private final List<Role> audience;

        @JsonProperty("priority")
        private final Double priority;

        @JsonProperty("resource")
        private final ResourceContents resource;

        public EmbeddedResource(List<Role> audience, Double priority, ResourceContents resource) {
            this.audience = audience;
            this.priority = priority;
            this.resource = resource;
        }

        public List<Role> getAudience() {
            return audience;
        }

        public Double getPriority() {
            return priority;
        }

        public ResourceContents getResource() {
            return resource;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextResourceContents.class, name = "text"),
            @JsonSubTypes.Type(value = BlobResourceContents.class, name = "blob")
    })
    public interface ResourceContents {

        /**
         * The URI of this resource.
         * @return the URI of this resource.
         */
        String uri();

        /**
         * The MIME type of this resource.
         * @return the MIME type of this resource.
         */
        String mimeType();
    }

    public class BlobResourceContents implements ResourceContents {

        @JsonProperty("uri")
        private final String uri;

        @JsonProperty("mimeType")
        private final String mimeType;

        @JsonProperty("blob")
        private final String blob;

        public BlobResourceContents(String uri, String mimeType, String blob) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.blob = blob;
        }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        public String getBlob() {
            return blob;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class TextResourceContents implements ResourceContents {

        @JsonProperty("uri")
        private final String uri;

        @JsonProperty("mimeType")
        private final String mimeType;

        @JsonProperty("text")
        private final String text;

        public TextResourceContents(String uri, String mimeType, String text) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.text = text;
        }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        public String getText() {
            return text;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tool {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("inputSchema")
        private final JsonSchema inputSchema;

        public Tool(String name, String description, JsonSchema inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public Tool(String name, String description, String schema) {
            this(name, description, parseSchema(schema));
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public JsonSchema getInputSchema() {
            return inputSchema;
        }

        private static JsonSchema parseSchema(String schema) {
            try {
                return OBJECT_MAPPER.readValue(schema, JsonSchema.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid schema: " + schema, e);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonSchema {

        private final String type;
        private final Map<String, SchemaProperty> properties;
        private final List<String> required;
        private final Boolean additionalProperties;

        @JsonCreator
        public JsonSchema(
                @JsonProperty("type") String type,
                @JsonProperty("properties") Map<String, SchemaProperty> properties,
                @JsonProperty("required") List<String> required,
                @JsonProperty("additionalProperties") Boolean additionalProperties) {
            this.type = type;
            this.properties = properties;
            this.required = required;
            this.additionalProperties = additionalProperties;
        }

        public String getType() {
            return type;
        }

        public Map<String, SchemaProperty> getProperties() {
            return properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public Boolean getAdditionalProperties() {
            return additionalProperties;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SchemaProperty {
            private final String type;

            @JsonCreator
            public SchemaProperty(@JsonProperty("type") String type) {
                this.type = type;
            }

            public String getType() {
                return type;
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadResourceRequest {

        @JsonProperty("uri")
        private final String uri;

        public ReadResourceRequest(String uri) {
            this.uri = uri;
        }

        public String uri() {
            return uri;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadResourceResult {

        @JsonProperty("contents")
        private final List<ResourceContents> contents;

        public ReadResourceResult(List<ResourceContents> contents) {
            this.contents = contents;
        }

        public List<ResourceContents> getContents() {
            return contents;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class GetPromptRequest implements Request {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("arguments")
        private final Map<String, Object> arguments;

        public GetPromptRequest(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String name() {
            return name;
        }

        public Map<String, Object> arguments() {
            return arguments;
        }
    }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class GetPromptResult {

        @JsonProperty("description")
        private final String description;

        @JsonProperty("messages")
        private final List<PromptMessage> messages;

        public GetPromptResult(String description, List<PromptMessage> messages) {
            this.description = description;
            this.messages = messages;
        }

        public String description() {
            return description;
        }

        public List<PromptMessage> messages() {
            return messages;
        }
    }


    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class PromptMessage {

        @JsonProperty("role")
        private final Role role;

        @JsonProperty("content")
        private final Content content;

        public PromptMessage(Role role, Content content) {
            this.role = role;
            this.content = content;
        }

        public Role getRole() {
            return role;
        }

        public Content getContent() {
            return content;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Prompt {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("arguments")
        private final List<PromptArgument> arguments;

        public Prompt(String name, String description, List<PromptArgument> arguments) {
            this.name = name;
            this.description = description;
            this.arguments = arguments;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public List<PromptArgument> arguments() {
            return arguments;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class PromptArgument {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("required")
        private final Boolean required;

        public PromptArgument(String name, String description, Boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public Boolean getRequired() {
            return required;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Root {

        @JsonProperty("uri")
        private final String uri;

        @JsonProperty("name")
        private final String name;

        public Root(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        public String uri() {
            return uri;
        }

        public String name() {
            return name;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateMessageResult {

        @JsonProperty("role")
        private final Role role;

        @JsonProperty("content")
        private final Content content;

        @JsonProperty("model")
        private final String model;

        @JsonProperty("stopReason")
        private final StopReason stopReason;

        public CreateMessageResult(Role role, Content content, String model, StopReason stopReason) {
            this.role = role;
            this.content = content;
            this.model = model;
            this.stopReason = stopReason;
        }

        public Role getRole() {
            return role;
        }

        public Content getContent() {
            return content;
        }

        public String getModel() {
            return model;
        }

        public StopReason getStopReason() {
            return stopReason;
        }

        public enum StopReason {
            @JsonProperty("endTurn") END_TURN,
            @JsonProperty("stopSequence") STOP_SEQUENCE,
            @JsonProperty("maxTokens") MAX_TOKENS
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Role role = Role.ASSISTANT;
            private Content content;
            private String model;
            private StopReason stopReason = StopReason.END_TURN;

            public Builder role(Role role) {
                this.role = role;
                return this;
            }

            public Builder content(Content content) {
                this.content = content;
                return this;
            }

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder stopReason(StopReason stopReason) {
                this.stopReason = stopReason;
                return this;
            }

            public Builder message(String message) {
                this.content = new TextContent(message);
                return this;
            }

            public CreateMessageResult build() {
                return new CreateMessageResult(role, content, model, stopReason);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListRootsResult {

        @JsonProperty("roots")
        private final List<Root> roots;

        public ListRootsResult(List<Root> roots) {
            this.roots = roots;
        }

        public List<Root> roots() {
            return roots;
        }
    }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateMessageRequest implements Request {

        @JsonProperty("messages")
        private final List<SamplingMessage> messages;

        @JsonProperty("modelPreferences")
        private final ModelPreferences modelPreferences;

        @JsonProperty("systemPrompt")
        private final String systemPrompt;

        @JsonProperty("includeContext")
        private final ContextInclusionStrategy includeContext;

        @JsonProperty("temperature")
        private final Double temperature;

        @JsonProperty("maxTokens")
        private final int maxTokens;

        @JsonProperty("stopSequences")
        private final List<String> stopSequences;

        @JsonProperty("metadata")
        private final Map<String, Object> metadata;

        public CreateMessageRequest(
                List<SamplingMessage> messages,
                ModelPreferences modelPreferences,
                String systemPrompt,
                ContextInclusionStrategy includeContext,
                Double temperature,
                int maxTokens,
                List<String> stopSequences,
                Map<String, Object> metadata
        ) {
            this.messages = messages;
            this.modelPreferences = modelPreferences;
            this.systemPrompt = systemPrompt;
            this.includeContext = includeContext;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.stopSequences = stopSequences;
            this.metadata = metadata;
        }

        public List<SamplingMessage> messages() {
            return messages;
        }

        public ModelPreferences getModelPreferences() {
            return modelPreferences;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public ContextInclusionStrategy getIncludeContext() {
            return includeContext;
        }

        public Double getTemperature() {
            return temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public List<String> getStopSequences() {
            return stopSequences;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public enum ContextInclusionStrategy {
            @JsonProperty("none") NONE,
            @JsonProperty("thisServer") THIS_SERVER,
            @JsonProperty("allServers") ALL_SERVERS
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<SamplingMessage> messages;
            private ModelPreferences modelPreferences;
            private String systemPrompt;
            private ContextInclusionStrategy includeContext;
            private Double temperature;
            private int maxTokens;
            private List<String> stopSequences;
            private Map<String, Object> metadata;

            public Builder messages(List<SamplingMessage> messages) {
                this.messages = messages;
                return this;
            }

            public Builder modelPreferences(ModelPreferences modelPreferences) {
                this.modelPreferences = modelPreferences;
                return this;
            }

            public Builder systemPrompt(String systemPrompt) {
                this.systemPrompt = systemPrompt;
                return this;
            }

            public Builder includeContext(ContextInclusionStrategy includeContext) {
                this.includeContext = includeContext;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder maxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder stopSequences(List<String> stopSequences) {
                this.stopSequences = stopSequences;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public CreateMessageRequest build() {
                return new CreateMessageRequest(messages, modelPreferences, systemPrompt,
                        includeContext, temperature, maxTokens, stopSequences, metadata);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class SamplingMessage {

        @JsonProperty("role")
        private final Role role;

        @JsonProperty("content")
        private final Content content;

        public SamplingMessage(Role role, Content content) {
            this.role = role;
            this.content = content;
        }

        public Role getRole() {
            return role;
        }

        public Content getContent() {
            return content;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelPreferences {

        @JsonProperty("hints")
        private final List<ModelHint> hints;

        @JsonProperty("costPriority")
        private final Double costPriority;

        @JsonProperty("speedPriority")
        private final Double speedPriority;

        @JsonProperty("intelligencePriority")
        private final Double intelligencePriority;

        public ModelPreferences(
                List<ModelHint> hints,
                Double costPriority,
                Double speedPriority,
                Double intelligencePriority
        ) {
            this.hints = hints;
            this.costPriority = costPriority;
            this.speedPriority = speedPriority;
            this.intelligencePriority = intelligencePriority;
        }

        public List<ModelHint> getHints() {
            return hints;
        }

        public Double getCostPriority() {
            return costPriority;
        }

        public Double getSpeedPriority() {
            return speedPriority;
        }

        public Double getIntelligencePriority() {
            return intelligencePriority;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<ModelHint> hints;
            private Double costPriority;
            private Double speedPriority;
            private Double intelligencePriority;

            public Builder hints(List<ModelHint> hints) {
                this.hints = hints;
                return this;
            }

            public Builder addHint(String name) {
                if (this.hints == null) {
                    this.hints = new ArrayList<>();
                }
                this.hints.add(new ModelHint(name));
                return this;
            }

            public Builder costPriority(Double costPriority) {
                this.costPriority = costPriority;
                return this;
            }

            public Builder speedPriority(Double speedPriority) {
                this.speedPriority = speedPriority;
                return this;
            }

            public Builder intelligencePriority(Double intelligencePriority) {
                this.intelligencePriority = intelligencePriority;
                return this;
            }

            public ModelPreferences build() {
                return new ModelPreferences(hints, costPriority, speedPriority, intelligencePriority);
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelHint {

        @JsonProperty("name")
        private final String name;

        public ModelHint(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public static ModelHint of(String name) {
            return new ModelHint(name);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaginatedRequest {

        @JsonProperty("cursor")
        private final String cursor;

        public PaginatedRequest(String cursor) {
            this.cursor = cursor;
        }

        public String getCursor() {
            return cursor;
        }
    }

    public enum LoggingLevel {

        @JsonProperty("debug")
        DEBUG(0),

        @JsonProperty("info")
        INFO(1),

        @JsonProperty("notice")
        NOTICE(2),

        @JsonProperty("warning")
        WARNING(3),

        @JsonProperty("error")
        ERROR(4),

        @JsonProperty("critical")
        CRITICAL(5),

        @JsonProperty("alert")
        ALERT(6),

        @JsonProperty("emergency")
        EMERGENCY(7);

        private final int level;

        LoggingLevel(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListToolsResult {

        @JsonProperty("tools")
        private final List<Tool> tools;

        @JsonProperty("nextCursor")
        private final String nextCursor;

        public ListToolsResult(List<Tool> tools, String nextCursor) {
            this.tools = tools;
            this.nextCursor = nextCursor;
        }

        public List<Tool> getTools() {
            return tools;
        }

        public String getNextCursor() {
            return nextCursor;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallToolRequest implements Request {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("arguments")
        private final Map<String, Object> arguments;

        public CallToolRequest(@JsonProperty("name") String name,
                               @JsonProperty("arguments") Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String name() {
            return name;
        }

        public Map<String, Object> arguments() {
            return arguments;
        }
    }


    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResourceTemplatesResult {

        @JsonProperty("resourceTemplates")
        private final List<ResourceTemplate> resourceTemplates;

        @JsonProperty("nextCursor")
        private final String nextCursor;

        public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, String nextCursor) {
            this.resourceTemplates = resourceTemplates;
            this.nextCursor = nextCursor;
        }

        public List<ResourceTemplate> resourceTemplates() {
            return resourceTemplates;
        }

        public String nextCursor() {
            return nextCursor;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListPromptsResult {

        @JsonProperty("prompts")
        private final List<Prompt> prompts;

        @JsonProperty("nextCursor")
        private final String nextCursor;

        public ListPromptsResult(List<Prompt> prompts, String nextCursor) {
            this.prompts = prompts;
            this.nextCursor = nextCursor;
        }

        public List<Prompt> prompts() {
            return prompts;
        }

        public String nextCursor() {
            return nextCursor;
        }
    }
}
