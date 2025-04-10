package storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class McpServerFeatures {
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
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

        public McpSchema.Tool tool() {
            return tool;
        }

        public BiFunction<McpAsyncServerExchange, Map<String, Object>,
                Mono<McpSchema.CallToolResult>> call() {
            return call;
        }

        public static AsyncToolSpecification fromSync(SyncToolSpecification tool) {
            // FIXME: This is temporary, proper validation should be implemented
            if (tool == null) {
                return null;
            }
            return new AsyncToolSpecification(tool.tool(),
                    (exchange, map) -> Mono
                            .fromCallable(() -> tool.call().apply(new McpSyncServerExchange(exchange), map))
                            .subscribeOn(Schedulers.boundedElastic()));
        }
    }

    public static class SyncToolSpecification {

        private final McpSchema.Tool tool;
        private final BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call;

        public SyncToolSpecification(
                McpSchema.Tool tool,
                BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call) {
            this.tool = tool;
            this.call = call;
        }

        public McpSchema.Tool tool() {
            return tool;
        }

        public BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> call() {
            return call;
        }
    }

    public static class AsyncResourceSpecification {

        private final McpSchema.Resource resource;
        private final BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler;

        public AsyncResourceSpecification(
                McpSchema.Resource resource,
                BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler) {
            this.resource = resource;
            this.readHandler = readHandler;
        }

        public McpSchema.Resource resource() {
            return resource;
        }

        public BiFunction<McpAsyncServerExchange, McpSchema.ReadResourceRequest,
                Mono<McpSchema.ReadResourceResult>> readHandler() {
            return readHandler;
        }

        public static AsyncResourceSpecification fromSync(SyncResourceSpecification resource) {
            // FIXME: This is temporary, proper validation should be implemented
            if (resource == null) {
                return null;
            }

            return new AsyncResourceSpecification(
                    resource.resource(),
                    (exchange, req) -> Mono.fromCallable(() ->
                                    resource.readHandler().apply(new McpSyncServerExchange(exchange), req))
                            .subscribeOn(Schedulers.boundedElastic())
            );
        }
    }

    public static class SyncResourceSpecification {

        private final McpSchema.Resource resource;
        private final BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler;

        public SyncResourceSpecification(
                McpSchema.Resource resource,
                BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler) {
            this.resource = resource;
            this.readHandler = readHandler;
        }

        public McpSchema.Resource resource() {
            return resource;
        }

        public BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest,
                McpSchema.ReadResourceResult> readHandler() {
            return readHandler;
        }
    }

    public static class AsyncPromptSpecification {

        private final McpSchema.Prompt prompt;
        private final BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler;

        public AsyncPromptSpecification(
                McpSchema.Prompt prompt,
                BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler) {
            this.prompt = prompt;
            this.promptHandler = promptHandler;
        }

        public McpSchema.Prompt prompt() {
            return prompt;
        }

        public BiFunction<McpAsyncServerExchange, McpSchema.GetPromptRequest,
                Mono<McpSchema.GetPromptResult>> promptHandler() {
            return promptHandler;
        }

        public static AsyncPromptSpecification fromSync(SyncPromptSpecification prompt) {
            // FIXME: This is temporary, proper validation should be implemented
            if (prompt == null) {
                return null;
            }
            return new AsyncPromptSpecification(
                    prompt.prompt(),
                    (exchange, req) -> Mono.fromCallable(() ->
                                    prompt.getPromptHandler().apply(new McpSyncServerExchange(exchange), req))
                            .subscribeOn(Schedulers.boundedElastic())
            );
        }
    }
    public class SyncPromptSpecification {

        private final McpSchema.Prompt prompt;
        private final BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler;

        public SyncPromptSpecification(
                McpSchema.Prompt prompt,
                BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler) {
            this.prompt = prompt;
            this.promptHandler = promptHandler;
        }

        public McpSchema.Prompt prompt() {
            return prompt;
        }

        public BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> getPromptHandler() {
            return promptHandler;
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

        public Async(
                McpSchema.Implementation serverInfo,
                McpSchema.ServerCapabilities serverCapabilities,
                List<AsyncToolSpecification> tools,
                Map<String, AsyncResourceSpecification> resources,
                List<McpSchema.ResourceTemplate> resourceTemplates,
                Map<String, AsyncPromptSpecification> prompts,
                List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers
        ) {
            if (serverInfo == null) {
                throw new IllegalArgumentException("serverInfo cannot be null");
            }

            this.serverInfo = serverInfo;
            this.serverCapabilities = (serverCapabilities != null) ? serverCapabilities
                    : new McpSchema.ServerCapabilities(
                    null, // experimental
                    new McpSchema.ServerCapabilities.LoggingCapabilities(),
                    !Utils.isEmpty(prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
                    !Utils.isEmpty(resources)
                            ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
                    !Utils.isEmpty(tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null
            );

            this.tools = (tools != null) ? tools : Collections.emptyList();
            this.resources = (resources != null) ? resources : Collections.emptyMap();
            this.resourceTemplates = (resourceTemplates != null) ? resourceTemplates : Collections.emptyList();
            this.prompts = (prompts != null) ? prompts : Collections.emptyMap();
            this.rootsChangeConsumers = (rootsChangeConsumers != null) ? rootsChangeConsumers : Collections.emptyList();
        }

        public McpSchema.Implementation getServerInfo() {
            return serverInfo;
        }

        public McpSchema.ServerCapabilities getServerCapabilities() {
            return serverCapabilities;
        }

        public List<AsyncToolSpecification> getTools() {
            return tools;
        }

        public Map<String, AsyncResourceSpecification> getResources() {
            return resources;
        }

        public List<McpSchema.ResourceTemplate> getResourceTemplates() {
            return resourceTemplates;
        }

        public Map<String, AsyncPromptSpecification> getPrompts() {
            return prompts;
        }

        public List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> getRootsChangeConsumers() {
            return rootsChangeConsumers;
        }

        public static Async fromSync(Sync syncSpec) {
            List<AsyncToolSpecification> tools = new ArrayList<>();
            for (SyncToolSpecification tool : syncSpec.getTools()) {
                tools.add(AsyncToolSpecification.fromSync(tool));
            }

            Map<String, AsyncResourceSpecification> resources = new HashMap<>();
            syncSpec.getResources().forEach((key, resource) -> {
                resources.put(key, AsyncResourceSpecification.fromSync(resource));
            });

            Map<String, AsyncPromptSpecification> prompts = new HashMap<>();
            syncSpec.getPrompts().forEach((key, prompt) -> {
                prompts.put(key, AsyncPromptSpecification.fromSync(prompt));
            });

            List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootChangeConsumers = new ArrayList<>();

            for (var rootChangeConsumer : syncSpec.rootsChangeConsumers()) {
                rootChangeConsumers.add((exchange, list) -> Mono
                        .<Void>fromRunnable(() -> rootChangeConsumer.accept(new McpSyncServerExchange(exchange), list))
                        .subscribeOn(Schedulers.boundedElastic()));
            }

            return new Async(
                    syncSpec.getServerInfo(),
                    syncSpec.getServerCapabilities(),
                    tools,
                    resources,
                    syncSpec.getResourceTemplates(),
                    prompts,
                    rootChangeConsumers
            );
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

        public Sync(
                McpSchema.Implementation serverInfo,
                McpSchema.ServerCapabilities serverCapabilities,
                List<SyncToolSpecification> tools,
                Map<String, SyncResourceSpecification> resources,
                List<McpSchema.ResourceTemplate> resourceTemplates,
                Map<String, SyncPromptSpecification> prompts,
                List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers
        ) {
            if (serverCapabilities == null) {
                throw new NullPointerException("serverCapabilities is null");
            }

            this.serverInfo = serverInfo;

            this.serverCapabilities = (serverCapabilities != null) ? serverCapabilities
                    : new McpSchema.ServerCapabilities(
                    null, // experimental
                    new McpSchema.ServerCapabilities.LoggingCapabilities(), // Enable logging by default
                    !Utils.isEmpty(prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
                    !Utils.isEmpty(resources) ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
                    !Utils.isEmpty(tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null
            );

            this.tools = (tools != null) ? tools : new ArrayList<>();
            this.resources = (resources != null) ? resources : new HashMap<>();
            this.resourceTemplates = (resourceTemplates != null) ? resourceTemplates : new ArrayList<>();
            this.prompts = (prompts != null) ? prompts : new HashMap<>();
            this.rootsChangeConsumers = (rootsChangeConsumers != null) ? rootsChangeConsumers : new ArrayList<>();
        }

        public McpSchema.Implementation getServerInfo() {
            return serverInfo;
        }

        public McpSchema.ServerCapabilities getServerCapabilities() {
            return serverCapabilities;
        }

        public List<SyncToolSpecification> getTools() {
            return tools;
        }

        public Map<String, SyncResourceSpecification> getResources() {
            return resources;
        }

        public List<McpSchema.ResourceTemplate> getResourceTemplates() {
            return resourceTemplates;
        }

        public Map<String, SyncPromptSpecification> getPrompts() {
            return prompts;
        }

        public List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers() {
            return rootsChangeConsumers;
        }
    }
}
