package modelcontextprotocol.server;

import modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.util.Assert;

public class McpSyncServer {
    private final McpAsyncServer asyncServer;

    public McpSyncServer(McpAsyncServer asyncServer) {
        Assert.notNull(asyncServer, "Async server must not be null");
        this.asyncServer = asyncServer;
    }

    public void addTool(McpServerFeatures.SyncToolSpecification toolHandler) {
        this.asyncServer.addTool(AsyncToolSpecification.fromSync(toolHandler)).block();
    }

    public void removeTool(String toolName) {
        this.asyncServer.removeTool(toolName).block();
    }

    public void addResource(McpServerFeatures.SyncResourceSpecification resourceHandler) {
        this.asyncServer.addResource(AsyncResourceSpecification.fromSync(resourceHandler)).block();
    }

    public void removeResource(String resourceUri) {
        this.asyncServer.removeResource(resourceUri).block();
    }

    public void addPrompt(McpServerFeatures.SyncPromptSpecification promptSpecification) {
        this.asyncServer.addPrompt(AsyncPromptSpecification.fromSync(promptSpecification)).block();
    }

    public void removePrompt(String promptName) {
        this.asyncServer.removePrompt(promptName).block();
    }

    public void notifyToolsListChanged() {
        this.asyncServer.notifyToolsListChanged().block();
    }

    public McpSchema.ServerCapabilities getServerCapabilities() {
        return this.asyncServer.getServerCapabilities();
    }

    public McpSchema.Implementation getServerInfo() {
        return this.asyncServer.getServerInfo();
    }

    public void notifyResourcesListChanged() {
        this.asyncServer.notifyResourcesListChanged().block();
    }

    public void notifyPromptsListChanged() {
        this.asyncServer.notifyPromptsListChanged().block();
    }

    public void closeGracefully() {
        this.asyncServer.closeGracefully().block();
    }

    public void close() {
        this.asyncServer.close();
    }

    public McpAsyncServer getAsyncServer() {
        return this.asyncServer;
    }
}

