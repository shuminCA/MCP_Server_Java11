package modelcontextprotocol.spec;

import com.fasterxml.jackson.core.type.TypeReference;

import reactor.core.publisher.Mono;

public interface McpTransport {
    default void close() {
        this.closeGracefully().subscribe();
    }

    Mono<Void> closeGracefully();

    Mono<Void> sendMessage(McpSchema.JSONRPCMessage message);

    <T> T unmarshalFrom(Object data, TypeReference<T> typeRef);
}
