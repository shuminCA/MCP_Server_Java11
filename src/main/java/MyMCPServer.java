import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MyMCPServer {
    private static final String NWS_API_BASE = "https://api.weather.gov";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String serverPipelineTriggerTaskUrl = "http://localhost:8888/api/1/rest/slsched/feed/snaplogic/projects/MCP/MCP_server%20Task";
    private static final String serverPipelineBearerToken = "DgRmatae0bB7NOudup7DSOXOPZfN0Jvn";


    public static void main(String[] args) throws Exception {
        HttpServletSseServerTransportProvider transportProvider =
                new HttpServletSseServerTransportProvider(new ObjectMapper(), "/", "/sse");

        McpAsyncServer syncServer = McpAsyncServer.builder(transportProvider)
                .serverInfo("custom-server", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .build();

        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("server");

        Server server = new Server(threadPool);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(45450);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transportProvider), "/*");

        server.setHandler(context);
        server.start();
    }
}
