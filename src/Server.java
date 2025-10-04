import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server {
    private static boolean isRunning = false;

    public static void setRunning(boolean running) {
        isRunning = running;
    }
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api", new RequestHandler());
        server.createContext("/health", new RequestHandler());
        server.setExecutor(Executors.newFixedThreadPool(5));
        server.start();
        isRunning = true;
        System.out.println("Server started on port 8080");
    }
}
