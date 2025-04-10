public class McpError extends RuntimeException {

    private McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError;

    public McpError(McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError) {
        super(jsonRpcError.message());
        this.jsonRpcError = jsonRpcError;
    }

    public McpError(Object error) {
        super(error.toString());
    }

    public McpSchema.JSONRPCResponse.JSONRPCError getJsonRpcError() {
        return jsonRpcError;
    }

}
