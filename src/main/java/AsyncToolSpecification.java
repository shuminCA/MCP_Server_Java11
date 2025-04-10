import java.util.Map;
import java.util.function.BiFunction;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AsyncToolSpecification {

    private final McpSchema.Tool tool;
    private final BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> call;

    public AsyncToolSpecification(
            McpSchema.Tool tool,
            BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> call
    ) {
        this.tool = tool;
        this.call = call;
    }

    public McpSchema.Tool tool() {
        return tool;
    }

    public BiFunction<McpAsyncServerExchange, Map<String, Object>,
            Mono<McpSchema.CallToolResult>> call() {
        return call;
    }

    public static AsyncToolSpecification Sync(McpSchema.Tool tool,
                                                  BiFunction<McpAsyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call) {
        // FIXME: This is temporary, proper validation should be implemented
        if (tool == null) {
            return null;
        }
        return new AsyncToolSpecification(tool,
                (exchange, map) -> Mono
                        .fromCallable(() -> call.apply(exchange, map))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
