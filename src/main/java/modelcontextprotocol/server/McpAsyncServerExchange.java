package modelcontextprotocol.server;

import com.fasterxml.jackson.core.type.TypeReference;

import reactor.core.publisher.Mono;
import modelcontextprotocol.spec.McpError;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.spec.McpSchema.LoggingLevel;
import modelcontextprotocol.spec.McpServerSession;
import modelcontextprotocol.util.Assert;

public class McpAsyncServerExchange {
    private final McpServerSession session;
    private final McpSchema.ClientCapabilities clientCapabilities;
    private final McpSchema.Implementation clientInfo;
    private volatile LoggingLevel minLoggingLevel;
    private static final TypeReference<McpSchema.CreateMessageResult> CREATE_MESSAGE_RESULT_TYPE_REF = new TypeReference<McpSchema.CreateMessageResult>() {
    };
    private static final TypeReference<McpSchema.ListRootsResult> LIST_ROOTS_RESULT_TYPE_REF = new TypeReference<McpSchema.ListRootsResult>() {
    };

    public McpAsyncServerExchange(McpServerSession session, McpSchema.ClientCapabilities clientCapabilities, McpSchema.Implementation clientInfo) {
        this.minLoggingLevel = LoggingLevel.INFO;
        this.session = session;
        this.clientCapabilities = clientCapabilities;
        this.clientInfo = clientInfo;
    }

    public McpSchema.ClientCapabilities getClientCapabilities() {
        return this.clientCapabilities;
    }

    public McpSchema.Implementation getClientInfo() {
        return this.clientInfo;
    }

    public Mono<McpSchema.CreateMessageResult> createMessage(McpSchema.CreateMessageRequest createMessageRequest) {
        if (this.clientCapabilities == null) {
            return Mono.error(new McpError("Client must be initialized. Call the initialize method first!"));
        } else {
            return this.clientCapabilities.sampling() == null ? Mono.error(new McpError("Client must be configured with sampling capabilities")) : this.session.sendRequest("sampling/createMessage", createMessageRequest, CREATE_MESSAGE_RESULT_TYPE_REF);
        }
    }

    public Mono<McpSchema.ListRootsResult> listRoots() {
        return this.listRoots((String)null);
    }

    public Mono<McpSchema.ListRootsResult> listRoots(String cursor) {
        return this.session.sendRequest("roots/list", new McpSchema.PaginatedRequest(cursor), LIST_ROOTS_RESULT_TYPE_REF);
    }

    public Mono<Void> loggingNotification(McpSchema.LoggingMessageNotification loggingMessageNotification) {
        return loggingMessageNotification == null ? Mono.error(new McpError("Logging message must not be null")) : Mono.defer(() -> {
            return this.isNotificationForLevelAllowed(loggingMessageNotification.level()) ? this.session.sendNotification("notifications/message", loggingMessageNotification) : Mono.empty();
        });
    }

    public void setMinLoggingLevel(LoggingLevel minLoggingLevel) {
        Assert.notNull(minLoggingLevel, "minLoggingLevel must not be null");
        this.minLoggingLevel = minLoggingLevel;
    }

    private boolean isNotificationForLevelAllowed(LoggingLevel loggingLevel) {
        return loggingLevel.level() >= this.minLoggingLevel.level();
    }
}