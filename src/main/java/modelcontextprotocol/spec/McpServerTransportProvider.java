package modelcontextprotocol.spec;

import reactor.core.publisher.Mono;

public interface McpServerTransportProvider {
    void setSessionFactory(McpServerSession.Factory sessionFactory);

    Mono<Void> notifyClients(String method, Object params);

    default void close() {
        this.closeGracefully().subscribe();
    }

    Mono<Void> closeGracefully();
}