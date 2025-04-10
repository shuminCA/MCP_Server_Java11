package storage;

public class McpSyncServer {
    private final McpAsyncServer asyncServer;

    public McpSyncServer(McpAsyncServer asyncServer) {
        Assert.notNull(asyncServer, "Async server must not be null");
        this.asyncServer = asyncServer;
    }

    public void addTool(McpServerFeatures.SyncToolSpecification toolHandler) {
        this.asyncServer.addTool(McpServerFeatures.AsyncToolSpecification.fromSync(toolHandler)).block();
    }
}
