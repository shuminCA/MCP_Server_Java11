package modelcontextprotocol.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import modelcontextprotocol.server.McpServerFeatures.Async;
import modelcontextprotocol.spec.McpSchema;
import modelcontextprotocol.spec.McpServerTransportProvider;
import modelcontextprotocol.util.Assert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import reactor.core.publisher.Mono;

public interface McpServer {
    static SyncSpecification sync(McpServerTransportProvider transportProvider) {
        return new SyncSpecification(transportProvider);
    }

    static AsyncSpecification async(McpServerTransportProvider transportProvider) {
        return new AsyncSpecification(transportProvider);
    }

    public static class SyncSpecification {
        private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server", "1.0.0");
        private final McpServerTransportProvider transportProvider;
        private ObjectMapper objectMapper;
        private McpSchema.Implementation serverInfo;
        private McpSchema.ServerCapabilities serverCapabilities;
        private String instructions;
        private final List<McpServerFeatures.SyncToolSpecification> tools;
        private final Map<String, McpServerFeatures.SyncResourceSpecification> resources;
        private final List<McpSchema.ResourceTemplate> resourceTemplates;
        private final Map<String, McpServerFeatures.SyncPromptSpecification> prompts;
        private final List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeHandlers;

        private SyncSpecification(McpServerTransportProvider transportProvider) {
            this.serverInfo = DEFAULT_SERVER_INFO;
            this.tools = new ArrayList();
            this.resources = new HashMap();
            this.resourceTemplates = new ArrayList();
            this.prompts = new HashMap();
            this.rootsChangeHandlers = new ArrayList();
            Assert.notNull(transportProvider, "Transport provider must not be null");
            this.transportProvider = transportProvider;
        }

        public SyncSpecification serverInfo(McpSchema.Implementation serverInfo) {
            Assert.notNull(serverInfo, "Server info must not be null");
            this.serverInfo = serverInfo;
            return this;
        }

        public SyncSpecification serverInfo(String name, String version) {
            Assert.hasText(name, "Name must not be null or empty");
            Assert.hasText(version, "Version must not be null or empty");
            this.serverInfo = new McpSchema.Implementation(name, version);
            return this;
        }

        public SyncSpecification instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public SyncSpecification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
            Assert.notNull(serverCapabilities, "Server capabilities must not be null");
            this.serverCapabilities = serverCapabilities;
            return this;
        }

        public SyncSpecification tool(McpSchema.Tool tool, BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler) {
            Assert.notNull(tool, "Tool must not be null");
            Assert.notNull(handler, "Handler must not be null");
            this.tools.add(new McpServerFeatures.SyncToolSpecification(tool, handler));
            return this;
        }

        public SyncSpecification tools(List<McpServerFeatures.SyncToolSpecification> toolSpecifications) {
            Assert.notNull(toolSpecifications, "Tool handlers list must not be null");
            this.tools.addAll(toolSpecifications);
            return this;
        }

        public SyncSpecification tools(McpServerFeatures.SyncToolSpecification... toolSpecifications) {
            Assert.notNull(toolSpecifications, "Tool handlers list must not be null");
            McpServerFeatures.SyncToolSpecification[] var2 = toolSpecifications;
            int var3 = toolSpecifications.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.SyncToolSpecification tool = var2[var4];
                this.tools.add(tool);
            }

            return this;
        }

        public SyncSpecification resources(Map<String, McpServerFeatures.SyncResourceSpecification> resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers map must not be null");
            this.resources.putAll(resourceSpecifications);
            return this;
        }

        public SyncSpecification resources(List<McpServerFeatures.SyncResourceSpecification> resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
            Iterator var2 = resourceSpecifications.iterator();

            while(var2.hasNext()) {
                McpServerFeatures.SyncResourceSpecification resource = (McpServerFeatures.SyncResourceSpecification)var2.next();
                this.resources.put(resource.resource().uri(), resource);
            }

            return this;
        }

        public SyncSpecification resources(McpServerFeatures.SyncResourceSpecification... resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
            McpServerFeatures.SyncResourceSpecification[] var2 = resourceSpecifications;
            int var3 = resourceSpecifications.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.SyncResourceSpecification resource = var2[var4];
                this.resources.put(resource.resource().uri(), resource);
            }

            return this;
        }

        public SyncSpecification resourceTemplates(List<McpSchema.ResourceTemplate> resourceTemplates) {
            Assert.notNull(resourceTemplates, "Resource templates must not be null");
            this.resourceTemplates.addAll(resourceTemplates);
            return this;
        }

        public SyncSpecification resourceTemplates(McpSchema.ResourceTemplate... resourceTemplates) {
            Assert.notNull(resourceTemplates, "Resource templates must not be null");
            McpSchema.ResourceTemplate[] var2 = resourceTemplates;
            int var3 = resourceTemplates.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpSchema.ResourceTemplate resourceTemplate = var2[var4];
                this.resourceTemplates.add(resourceTemplate);
            }

            return this;
        }

        public SyncSpecification prompts(Map<String, McpServerFeatures.SyncPromptSpecification> prompts) {
            Assert.notNull(prompts, "Prompts map must not be null");
            this.prompts.putAll(prompts);
            return this;
        }

        public SyncSpecification prompts(List<McpServerFeatures.SyncPromptSpecification> prompts) {
            Assert.notNull(prompts, "Prompts list must not be null");
            Iterator var2 = prompts.iterator();

            while(var2.hasNext()) {
                McpServerFeatures.SyncPromptSpecification prompt = (McpServerFeatures.SyncPromptSpecification)var2.next();
                this.prompts.put(prompt.prompt().name(), prompt);
            }

            return this;
        }

        public SyncSpecification prompts(McpServerFeatures.SyncPromptSpecification... prompts) {
            Assert.notNull(prompts, "Prompts list must not be null");
            McpServerFeatures.SyncPromptSpecification[] var2 = prompts;
            int var3 = prompts.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.SyncPromptSpecification prompt = var2[var4];
                this.prompts.put(prompt.prompt().name(), prompt);
            }

            return this;
        }

        public SyncSpecification rootsChangeHandler(BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> handler) {
            Assert.notNull(handler, "Consumer must not be null");
            this.rootsChangeHandlers.add(handler);
            return this;
        }

        public SyncSpecification rootsChangeHandlers(List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> handlers) {
            Assert.notNull(handlers, "Handlers list must not be null");
            this.rootsChangeHandlers.addAll(handlers);
            return this;
        }

        public SyncSpecification rootsChangeHandlers(BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>... handlers) {
            Assert.notNull(handlers, "Handlers list must not be null");
            return this.rootsChangeHandlers(List.of(handlers));
        }

        public SyncSpecification objectMapper(ObjectMapper objectMapper) {
            Assert.notNull(objectMapper, "ObjectMapper must not be null");
            this.objectMapper = objectMapper;
            return this;
        }

        public McpSyncServer build() {
            McpServerFeatures.Sync syncFeatures = new McpServerFeatures.Sync(this.serverInfo, this.serverCapabilities, this.tools, this.resources, this.resourceTemplates, this.prompts, this.rootsChangeHandlers, this.instructions);
            McpServerFeatures.Async asyncFeatures = Async.fromSync(syncFeatures);
            ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
            McpAsyncServer asyncServer = new McpAsyncServer(this.transportProvider, mapper, asyncFeatures);
            return new McpSyncServer(asyncServer);
        }
    }

    public static class AsyncSpecification {
        private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server", "1.0.0");
        private final McpServerTransportProvider transportProvider;
        private ObjectMapper objectMapper;
        private McpSchema.Implementation serverInfo;
        private McpSchema.ServerCapabilities serverCapabilities;
        private String instructions;
        private final List<McpServerFeatures.AsyncToolSpecification> tools;
        private final Map<String, McpServerFeatures.AsyncResourceSpecification> resources;
        private final List<McpSchema.ResourceTemplate> resourceTemplates;
        private final Map<String, McpServerFeatures.AsyncPromptSpecification> prompts;
        private final List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeHandlers;

        private AsyncSpecification(McpServerTransportProvider transportProvider) {
            this.serverInfo = DEFAULT_SERVER_INFO;
            this.tools = new ArrayList();
            this.resources = new HashMap();
            this.resourceTemplates = new ArrayList();
            this.prompts = new HashMap();
            this.rootsChangeHandlers = new ArrayList();
            Assert.notNull(transportProvider, "Transport provider must not be null");
            this.transportProvider = transportProvider;
        }

        public AsyncSpecification serverInfo(McpSchema.Implementation serverInfo) {
            Assert.notNull(serverInfo, "Server info must not be null");
            this.serverInfo = serverInfo;
            return this;
        }

        public AsyncSpecification serverInfo(String name, String version) {
            Assert.hasText(name, "Name must not be null or empty");
            Assert.hasText(version, "Version must not be null or empty");
            this.serverInfo = new McpSchema.Implementation(name, version);
            return this;
        }

        public AsyncSpecification instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public AsyncSpecification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
            Assert.notNull(serverCapabilities, "Server capabilities must not be null");
            this.serverCapabilities = serverCapabilities;
            return this;
        }

        public AsyncSpecification tool(McpSchema.Tool tool, BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
            Assert.notNull(tool, "Tool must not be null");
            Assert.notNull(handler, "Handler must not be null");
            this.tools.add(new McpServerFeatures.AsyncToolSpecification(tool, handler));
            return this;
        }

        public AsyncSpecification tools(List<McpServerFeatures.AsyncToolSpecification> toolSpecifications) {
            Assert.notNull(toolSpecifications, "Tool handlers list must not be null");
            this.tools.addAll(toolSpecifications);
            return this;
        }

        public AsyncSpecification tools(McpServerFeatures.AsyncToolSpecification... toolSpecifications) {
            Assert.notNull(toolSpecifications, "Tool handlers list must not be null");
            McpServerFeatures.AsyncToolSpecification[] var2 = toolSpecifications;
            int var3 = toolSpecifications.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.AsyncToolSpecification tool = var2[var4];
                this.tools.add(tool);
            }

            return this;
        }

        public AsyncSpecification resources(Map<String, McpServerFeatures.AsyncResourceSpecification> resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers map must not be null");
            this.resources.putAll(resourceSpecifications);
            return this;
        }

        public AsyncSpecification resources(List<McpServerFeatures.AsyncResourceSpecification> resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
            Iterator var2 = resourceSpecifications.iterator();

            while(var2.hasNext()) {
                McpServerFeatures.AsyncResourceSpecification resource = (McpServerFeatures.AsyncResourceSpecification)var2.next();
                this.resources.put(resource.resource().uri(), resource);
            }

            return this;
        }

        public AsyncSpecification resources(McpServerFeatures.AsyncResourceSpecification... resourceSpecifications) {
            Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
            McpServerFeatures.AsyncResourceSpecification[] var2 = resourceSpecifications;
            int var3 = resourceSpecifications.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.AsyncResourceSpecification resource = var2[var4];
                this.resources.put(resource.resource().uri(), resource);
            }

            return this;
        }

        public AsyncSpecification resourceTemplates(List<McpSchema.ResourceTemplate> resourceTemplates) {
            Assert.notNull(resourceTemplates, "Resource templates must not be null");
            this.resourceTemplates.addAll(resourceTemplates);
            return this;
        }

        public AsyncSpecification resourceTemplates(McpSchema.ResourceTemplate... resourceTemplates) {
            Assert.notNull(resourceTemplates, "Resource templates must not be null");
            McpSchema.ResourceTemplate[] var2 = resourceTemplates;
            int var3 = resourceTemplates.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpSchema.ResourceTemplate resourceTemplate = var2[var4];
                this.resourceTemplates.add(resourceTemplate);
            }

            return this;
        }

        public AsyncSpecification prompts(Map<String, McpServerFeatures.AsyncPromptSpecification> prompts) {
            Assert.notNull(prompts, "Prompts map must not be null");
            this.prompts.putAll(prompts);
            return this;
        }

        public AsyncSpecification prompts(List<McpServerFeatures.AsyncPromptSpecification> prompts) {
            Assert.notNull(prompts, "Prompts list must not be null");
            Iterator var2 = prompts.iterator();

            while(var2.hasNext()) {
                McpServerFeatures.AsyncPromptSpecification prompt = (McpServerFeatures.AsyncPromptSpecification)var2.next();
                this.prompts.put(prompt.prompt().name(), prompt);
            }

            return this;
        }

        public AsyncSpecification prompts(McpServerFeatures.AsyncPromptSpecification... prompts) {
            Assert.notNull(prompts, "Prompts list must not be null");
            McpServerFeatures.AsyncPromptSpecification[] var2 = prompts;
            int var3 = prompts.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                McpServerFeatures.AsyncPromptSpecification prompt = var2[var4];
                this.prompts.put(prompt.prompt().name(), prompt);
            }

            return this;
        }

        public AsyncSpecification rootsChangeHandler(BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>> handler) {
            Assert.notNull(handler, "Consumer must not be null");
            this.rootsChangeHandlers.add(handler);
            return this;
        }

        public AsyncSpecification rootsChangeHandlers(List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> handlers) {
            Assert.notNull(handlers, "Handlers list must not be null");
            this.rootsChangeHandlers.addAll(handlers);
            return this;
        }

        public AsyncSpecification rootsChangeHandlers(BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>... handlers) {
            Assert.notNull(handlers, "Handlers list must not be null");
            return this.rootsChangeHandlers(Arrays.asList(handlers));
        }

        public AsyncSpecification objectMapper(ObjectMapper objectMapper) {
            Assert.notNull(objectMapper, "ObjectMapper must not be null");
            this.objectMapper = objectMapper;
            return this;
        }

        public McpAsyncServer build() {
            McpServerFeatures.Async features = new McpServerFeatures.Async(this.serverInfo, this.serverCapabilities, this.tools, this.resources, this.resourceTemplates, this.prompts, this.rootsChangeHandlers, this.instructions);
            ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
            return new McpAsyncServer(this.transportProvider, mapper, features);
        }
    }
}

