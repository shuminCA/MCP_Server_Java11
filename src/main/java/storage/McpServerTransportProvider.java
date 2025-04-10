package storage;

import java.util.Map;

import reactor.core.publisher.Mono;

public interface McpServerTransportProvider {

    /**
     * Sets the session factory that will be used to create sessions for new clients. An
     * implementation of the MCP server MUST call this method before any MCP interactions
     * take place.
     * @param sessionFactory the session factory to be used for initiating client sessions
     */
    void setSessionFactory(McpServerSession.Factory sessionFactory);

    /**
     * Sends a notification to all connected clients.
     * @param method the name of the notification method to be called on the clients
     * @param params a map of parameters to be sent with the notification
     * @return a Mono that completes when the notification has been broadcast
     * @see McpSession#sendNotification(String, Map)
     */
    Mono<Void> notifyClients(String method, Map<String, Object> params);

    /**
     * Immediately closes all the transports with connected clients and releases any
     * associated resources.
     */
    default void close() {
        this.closeGracefully().subscribe();
    }

    /**
     * Gracefully closes all the transports with connected clients and releases any
     * associated resources asynchronously.
     * @return a {@link Mono<Void>} that completes when the connections have been closed.
     */
    Mono<Void> closeGracefully();

}
