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

public class MyMCPServer {
    private static final String NWS_API_BASE = "https://api.weather.gov";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gets weather forecast for a location using the National Weather Service API.
     *
     * @param latitude Latitude of the location
     * @param longitude Longitude of the location
     * @return Formatted weather forecast
     * @throws IOException If there's an error with the API request
     * @throws InterruptedException If the API request is interrupted
     */
    private static String getWeatherForecast(double latitude, double longitude)
            throws IOException, InterruptedException {
        // Round coordinates to 4 decimal places as required by NWS API
        double roundedLat = Math.round(latitude * 10000.0) / 10000.0;
        double roundedLon = Math.round(longitude * 10000.0) / 10000.0;

        // First get the forecast grid endpoint
        String pointsUrl = String.format("%s/points/%.4f,%.4f", NWS_API_BASE, roundedLat, roundedLon);
        System.out.println("Requesting forecast for coordinates: " + roundedLat + "," + roundedLon);
        JsonNode pointsData = makeNwsRequest(pointsUrl);

        if (pointsData == null) {
            return "Unable to fetch forecast data for this location.";
        }

        // Get the forecast URL from the points response
        String forecastUrl = pointsData.get("properties").get("forecast").asText();
        JsonNode forecastData = makeNwsRequest(forecastUrl);

        if (forecastData == null) {
            return "Unable to fetch detailed forecast.";
        }

        // Format the periods into a readable forecast
        JsonNode periods = forecastData.get("properties").get("periods");
        List<String> forecasts = new ArrayList<>();

        // Only show next 5 periods
        int count = Math.min(periods.size(), 5);
        for (int i = 0; i < count; i++) {
            JsonNode period = periods.get(i);
            String forecast = String.format(
                    "%s:\nTemperature: %d°%s\nWind: %s %s\nForecast: %s\n",
                    period.get("name").asText(),
                    period.get("temperature").asInt(),
                    period.get("temperatureUnit").asText(),
                    period.get("windSpeed").asText(),
                    period.get("windDirection").asText(),
                    period.get("detailedForecast").asText()
            );
            forecasts.add(forecast);
        }

        return String.join("\n---\n", forecasts);
    }

    /**
     * Makes a request to the National Weather Service API.
     *
     * @param url The API endpoint URL
     * @return JsonNode containing the response data, or null if the request failed
     * @throws IOException If there's an error with the API request
     * @throws InterruptedException If the API request is interrupted
     */
    private static JsonNode makeNwsRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MCP Weather Demo (your-email@example.com)")
                .header("Accept", "application/json")
                .GET()
                .build();

        // Configure HttpClient to follow redirects
        HttpClient clientWithRedirects = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpResponse<String> response = clientWithRedirects.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return objectMapper.readTree(response.body());
        } else if (response.statusCode() == 301 || response.statusCode() == 302) {
            // Handle redirect manually if needed
            JsonNode errorNode = objectMapper.readTree(response.body());
            if (errorNode.has("location")) {
                String redirectUrl = errorNode.get("location").asText();
                System.out.println("Following redirect to: " + redirectUrl);
                return makeNwsRequest(redirectUrl);
            }
            System.err.println("Redirect without location header: " + response.body());
            return null;
        } else {
            System.err.println("API request failed with status code: " + response.statusCode());
            System.err.println("Response body: " + response.body());
            return null;
        }
    }

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

        AsyncToolSpecification asyncToolSpecification = AsyncToolSpecification.Sync(
                        new McpSchema.Tool(
                                "weather-forecast",
                                "gives weather forecast for a location using latitude and longitude",
                                "{\n" +
                                        "  \"type\": \"object\",\n" +
                                        "  \"properties\": {\n" +
                                        "    \"latitude\": {\n" +
                                        "      \"type\": \"number\"\n" +
                                        "    },\n" +
                                        "    \"longitude\": {\n" +
                                        "      \"type\": \"number\"\n" +
                                        "    }\n" +
                                        "  },\n" +
                                        "  \"required\": [\"latitude\", \"longitude\"]\n" +
                                        "}"
                        ),
                        (mcpSyncServerExchange, params) -> {
                            try {
                                // Extract latitude and longitude from params
                                double latitude = ((Number) params.get("latitude")).doubleValue();
                                double longitude = ((Number) params.get("longitude")).doubleValue();

                                // Get weather forecast from NWS API
                                String forecast = getWeatherForecast(latitude, longitude);

                                return new McpSchema.CallToolResult(
                                        List.of(new McpSchema.TextContent(forecast)),
                                        false
                                );
                            } catch (Exception e) {
                                return new McpSchema.CallToolResult(
                                        List.of(new McpSchema.TextContent(
                                                "Error fetching weather forecast: " + e.getMessage()
                                        )),
                                        false
                                );
                            }
                        }
                );

        syncServer.addTool(asyncToolSpecification).block();

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
