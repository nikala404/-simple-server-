import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RequestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();

            if ("POST".equals(method)) {
                handlePostRequest(exchange);
            } else if ("GET".equals(method)) {
                handleHealthCheck(exchange);
            } else {
                //Method Not Allowed For Other Type Of Requests
                String response = "{\"error\":\"Method not allowed\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(405, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        } catch (Exception e) {
            //Unexpected errors
            String errorResponse = "{\"error\":\"Internal server error\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorResponse.getBytes());
            os.close();
            e.printStackTrace();
        }
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        // Parse Request
        InputStream inputStream = exchange.getRequestBody();
        String requestBody = new String(inputStream.readAllBytes());
        inputStream.close();

        if (requestBody.contains("\"message\"") && !requestBody.trim().isEmpty()) {
            // Return Success Message
            String response = String.format(
                    "{\"status\":\"success\",\"received\":\"%s\",\"timestamp\":%d}",
                    requestBody.replace("\"", "\\\""), // Escape quotes
                    System.currentTimeMillis()
            );

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } else {
            // Return Error Response
            String errorResponse = "{\"status\":\"error\",\"message\":\"Request must contain 'message' field\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, errorResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorResponse.getBytes());
            os.close();
        }
    }

    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
