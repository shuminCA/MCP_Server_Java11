import com.fasterxml.jackson.core.type.TypeReference;
import reactor.core.publisher.Mono;

/**
 * Represents an asynchronous exchange with a Model Context Protocol (MCP) client. The
 * exchange provides methods to interact with the client and query its capabilities.
 *
 * @author Dariusz Jędrzejczyk
 */
public class McpAsyncServerExchange {

    private final McpServerSession session;

    private final McpSchema.ClientCapabilities clientCapabilities;

    private final McpSchema.Implementation clientInfo;

    private static final TypeReference<McpSchema.ListRootsResult> LIST_ROOTS_RESULT_TYPE_REF = new TypeReference<>() {
    };

    /**
     * Create a new asynchronous exchange with the client.
     * @param session The server session representing a 1-1 interaction.
     * @param clientCapabilities The client capabilities that define the supported
     * features and functionality.
     * @param clientInfo The client implementation information.
     */
    public McpAsyncServerExchange(McpServerSession session, McpSchema.ClientCapabilities clientCapabilities,
                                  McpSchema.Implementation clientInfo) {
        this.session = session;
        this.clientCapabilities = clientCapabilities;
        this.clientInfo = clientInfo;
    }

    /**
     * Retrieves the list of all roots provided by the client.
     * @return A Mono that emits the list of roots result.
     */
    public Mono<McpSchema.ListRootsResult> listRoots() {
        return this.listRoots(null);
    }

    /**
     * Retrieves a paginated list of roots provided by the client.
     * @param cursor Optional pagination cursor from a previous list request
     * @return A Mono that emits the list of roots result containing
     */
    public Mono<McpSchema.ListRootsResult> listRoots(String cursor) {
        return this.session.sendRequest(McpSchema.METHOD_ROOTS_LIST, new McpSchema.PaginatedRequest(cursor),
                LIST_ROOTS_RESULT_TYPE_REF);
    }
}