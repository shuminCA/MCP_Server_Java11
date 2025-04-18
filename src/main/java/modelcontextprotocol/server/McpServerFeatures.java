package modelcontextprotocol.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.util.Assert;
import modelcontextprotocol.util.Utils;

public class McpServerFeatures {
    public McpServerFeatures() {
    }

    public static class SyncPromptSpecification {
        private final McpSchema.Prompt prompt;
        private final BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler;

        public SyncPromptSpecification(
                McpSchema.Prompt prompt,
                BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler
        ) {
            this.prompt = prompt;
            this.promptHandler = promptHandler;
        }

        public McpSchema.Prompt prompt() {
            return this.prompt;
        }

        public BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler() {
            return this.promptHandler;
        }
    }

    public static class SyncResourceSpecification {
        private final McpSchema.Resource resource;
        private final BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler;

        public SyncResourceSpecification(
                McpSchema.Resource resource,
                BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler
        ) {
            this.resource = resource;
            this.readHandler = readHandler;
        }

        public McpSchema.Resource resource() {
            return this.resource;
        }

        public BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler() {
            return this.readHandler;
        }
    }

    public static class SyncToolSpecification {
        private final McpSchema.Tool tool;
        private final BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call;

        public SyncToolSpecification(
                McpSchema.Tool tool,
                BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call
        ) {
            this.tool = tool;
            this.call = call;
        }

        public McpSchema.Tool tool() {
            return this.tool;
        }

        public BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call() {
            return this.call;
        }
    }

    public static class AsyncPromptSpecification {
        private final McpSchema.Prompt prompt;
        private final BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler;

        public AsyncPromptSpecification(
                McpSchema.Prompt prompt,
                BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler
        ) {
            this.prompt = prompt;
            this.promptHandler = promptHandler;
        }

        static AsyncPromptSpecification fromSync(SyncPromptSpecification prompt) {
            return prompt == null ? null : new AsyncPromptSpecification(prompt.prompt(), (exchange, req) -> {
                return Mono.fromCallable(() -> {
                    return (McpSchema.GetPromptResult)prompt.promptHandler().apply(new McpSyncServerExchange(exchange), req);
                }).subscribeOn(Schedulers.boundedElastic());
            });
        }

        public McpSchema.Prompt prompt() {
            return this.prompt;
        }

        public BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler() {
            return this.promptHandler;
        }
    }

    public static class AsyncResourceSpecification {
        private final McpSchema.Resource resource;
        private final BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler;

        public AsyncResourceSpecification(
                McpSchema.Resource resource,
                BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler
        ) {
            this.resource = resource;
            this.readHandler = readHandler;
        }

        static AsyncResourceSpecification fromSync(SyncResourceSpecification resource) {
            return resource == null ? null : new AsyncResourceSpecification(resource.resource(), (exchange, req) -> {
                return Mono.fromCallable(() -> {
                    return (McpSchema.ReadResourceResult)resource.readHandler().apply(new McpSyncServerExchange(exchange), req);
                }).subscribeOn(Schedulers.boundedElastic());
            });
        }

        public McpSchema.Resource resource() {
            return this.resource;
        }

        public BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler() {
            return this.readHandler;
        }
    }

    public static class AsyncToolSpecification {
        private final McpSchema.Tool tool;
        private final BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> call;

        public AsyncToolSpecification(
                McpSchema.Tool tool,
                BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> call
        ) {
            this.tool = tool;
            this.call = call;
        }

        static AsyncToolSpecification fromSync(SyncToolSpecification tool) {
            return tool == null ? null : new AsyncToolSpecification(tool.tool(), (exchange, map) -> {
                return Mono.fromCallable(() -> {
                    return (McpSchema.CallToolResult)tool.call().apply(new McpSyncServerExchange(exchange), map);
                }).subscribeOn(Schedulers.boundedElastic());
            });
        }

        public McpSchema.Tool tool() {
            return this.tool;
        }

        public BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> call() {
            return this.call;
        }
    }

    public static class Sync {
        private final McpSchema.Implementation serverInfo;
        private final McpSchema.ServerCapabilities serverCapabilities;
        private final List<SyncToolSpecification> tools;
        private final Map<String, SyncResourceSpecification> resources;
        private final List<McpSchema.ResourceTemplate> resourceTemplates;
        private final Map<String, SyncPromptSpecification> prompts;
        private final List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers;
        private final String instructions;

        public Sync(
                McpSchema.Implementation serverInfo,
                McpSchema.ServerCapabilities serverCapabilities,
                List<SyncToolSpecification> tools,
                Map<String, SyncResourceSpecification> resources,
                List<McpSchema.ResourceTemplate> resourceTemplates,
                Map<String, SyncPromptSpecification> prompts,
                List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers,
                String instructions
        ) {
            Assert.notNull(serverInfo, "Server info must not be null");
            this.serverInfo = serverInfo;

            this.serverCapabilities = serverCapabilities != null
                    ? serverCapabilities
                    : new McpSchema.ServerCapabilities(
                    null,
                    new McpSchema.ServerCapabilities.LoggingCapabilities(),
                    !Utils.isEmpty(prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
                    !Utils.isEmpty(resources) ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
                    !Utils.isEmpty(tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null
            );

            this.tools = tools != null ? tools : new ArrayList<>();
            this.resources = resources != null ? resources : new HashMap<>();
            this.resourceTemplates = resourceTemplates != null ? resourceTemplates : new ArrayList<>();
            this.prompts = prompts != null ? prompts : new HashMap<>();
            this.rootsChangeConsumers = rootsChangeConsumers != null ? rootsChangeConsumers : new ArrayList<>();
            this.instructions = instructions;
        }

        public McpSchema.Implementation serverInfo() {
            return this.serverInfo;
        }

        public McpSchema.ServerCapabilities serverCapabilities() {
            return this.serverCapabilities;
        }

        public List<SyncToolSpecification> tools() {
            return this.tools;
        }

        public Map<String, SyncResourceSpecification> resources() {
            return this.resources;
        }

        public List<McpSchema.ResourceTemplate> resourceTemplates() {
            return this.resourceTemplates;
        }

        public Map<String, SyncPromptSpecification> prompts() {
            return this.prompts;
        }

        public List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers() {
            return this.rootsChangeConsumers;
        }

        public String instructions() {
            return this.instructions;
        }
    }

    public static class Async {
        private final McpSchema.Implementation serverInfo;
        private final McpSchema.ServerCapabilities serverCapabilities;
        private final List<AsyncToolSpecification> tools;
        private final Map<String, AsyncResourceSpecification> resources;
        private final List<McpSchema.ResourceTemplate> resourceTemplates;
        private final Map<String, AsyncPromptSpecification> prompts;
        private final List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers;
        private final String instructions;

        public Async(
                McpSchema.Implementation serverInfo,
                McpSchema.ServerCapabilities serverCapabilities,
                List<AsyncToolSpecification> tools,
                Map<String, AsyncResourceSpecification> resources,
                List<McpSchema.ResourceTemplate> resourceTemplates,
                Map<String, AsyncPromptSpecification> prompts,
                List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers,
                String instructions
        ) {
            Assert.notNull(serverInfo, "Server info must not be null");
            this.serverInfo = serverInfo;

            this.serverCapabilities = serverCapabilities != null
                    ? serverCapabilities
                    : new McpSchema.ServerCapabilities(
                    null,
                    new McpSchema.ServerCapabilities.LoggingCapabilities(),
                    !Utils.isEmpty(prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
                    !Utils.isEmpty(resources) ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
                    !Utils.isEmpty(tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null
            );

            this.tools = tools != null ? tools : Collections.emptyList();
            this.resources = resources != null ? resources : Collections.emptyMap();
            this.resourceTemplates = resourceTemplates != null ? resourceTemplates : Collections.emptyList();
            this.prompts = prompts != null ? prompts : Collections.emptyMap();
            this.rootsChangeConsumers = rootsChangeConsumers != null ? rootsChangeConsumers : Collections.emptyList();
            this.instructions = instructions;
        }

        static Async fromSync(Sync syncSpec) {
            List<AsyncToolSpecification> tools = new ArrayList();
            Iterator var2 = syncSpec.tools().iterator();

            while(var2.hasNext()) {
                SyncToolSpecification tool = (SyncToolSpecification)var2.next();
                tools.add(AsyncToolSpecification.fromSync(tool));
            }

            Map<String, AsyncResourceSpecification> resources = new HashMap();
            syncSpec.resources().forEach((key, resource) -> {
                resources.put(key, AsyncResourceSpecification.fromSync(resource));
            });
            Map<String, AsyncPromptSpecification> prompts = new HashMap();
            syncSpec.prompts().forEach((key, prompt) -> {
                prompts.put(key, AsyncPromptSpecification.fromSync(prompt));
            });
            List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootChangeConsumers = new ArrayList();
            Iterator var5 = syncSpec.rootsChangeConsumers().iterator();

            while (var5.hasNext()) {
                BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootChangeConsumer = (BiConsumer) var5.next();
                rootChangeConsumers.add((exchange, list) -> {
                    return Mono.fromRunnable(() -> {
                        rootChangeConsumer.accept(new McpSyncServerExchange(exchange), list);
                    }).subscribeOn(Schedulers.boundedElastic()).then();
                });
            }

            return new Async(syncSpec.serverInfo(), syncSpec.serverCapabilities(), tools, resources, syncSpec.resourceTemplates(), prompts, rootChangeConsumers, syncSpec.instructions());
        }

        public McpSchema.Implementation serverInfo() {
            return this.serverInfo;
        }

        public McpSchema.ServerCapabilities serverCapabilities() {
            return this.serverCapabilities;
        }

        public List<AsyncToolSpecification> tools() {
            return this.tools;
        }

        public Map<String, AsyncResourceSpecification> resources() {
            return this.resources;
        }

        public List<McpSchema.ResourceTemplate> resourceTemplates() {
            return this.resourceTemplates;
        }

        public Map<String, AsyncPromptSpecification> prompts() {
            return this.prompts;
        }

        public List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers() {
            return this.rootsChangeConsumers;
        }

        public String instructions() {
            return this.instructions;
        }
    }
}