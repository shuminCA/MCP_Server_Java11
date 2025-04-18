package modelcontextprotocol.spec;

import com.fasterxml.jackson.core.type.TypeReference;

import reactor.core.publisher.Mono;

public interface McpSession {
    <T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef);

    default Mono<Void> sendNotification(String method) {
        return this.sendNotification(method, (Object)null);
    }

    Mono<Void> sendNotification(String method, Object params);

    Mono<Void> closeGracefully();

    void close();
}