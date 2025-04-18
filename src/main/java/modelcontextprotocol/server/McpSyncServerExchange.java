package modelcontextprotocol.server;

import modelcontextprotocol.spec.McpSchema;

public class McpSyncServerExchange {
    private final McpAsyncServerExchange exchange;

    public McpSyncServerExchange(McpAsyncServerExchange exchange) {
        this.exchange = exchange;
    }

    public McpSchema.ClientCapabilities getClientCapabilities() {
        return this.exchange.getClientCapabilities();
    }

    public McpSchema.Implementation getClientInfo() {
        return this.exchange.getClientInfo();
    }

    public McpSchema.CreateMessageResult createMessage(McpSchema.CreateMessageRequest createMessageRequest) {
        return (McpSchema.CreateMessageResult)this.exchange.createMessage(createMessageRequest).block();
    }

    public McpSchema.ListRootsResult listRoots() {
        return (McpSchema.ListRootsResult)this.exchange.listRoots().block();
    }

    public McpSchema.ListRootsResult listRoots(String cursor) {
        return (McpSchema.ListRootsResult)this.exchange.listRoots(cursor).block();
    }

    public void loggingNotification(McpSchema.LoggingMessageNotification loggingMessageNotification) {
        this.exchange.loggingNotification(loggingMessageNotification).block();
    }
}

