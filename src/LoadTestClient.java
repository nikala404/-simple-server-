import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTestClient {
    // Thread-safe counters for tracking results across multiple threads
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static long totalResponseTime = 0;
    private static volatile boolean running = true; // Flag to stop all worker threads

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        // Create thread pool with 5 concurrent users
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try (HttpClient client = HttpClient.newHttpClient()) {
                        while (running) {
                            try {
                                long reqStart = System.nanoTime();

                                // Build POST request
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("http://localhost:8080/api"))
                                        .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"test\"}"))
                                        .build();
                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                                System.out.println("Thread: " + Thread.currentThread().getName());
                                System.out.println("Response: " + response.body());

                                // Calculate response time in milliseconds
                                long reqTime = (System.nanoTime() - reqStart) / 1_000_000;
                                totalResponseTime += reqTime;

                                // Count successful vs failed requests
                                if (response.statusCode() == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                errorCount.incrementAndGet();
                            }
                        }
                    }
                });
            }
            // Performance Duration
            Thread.sleep(Duration.ofSeconds(10).toMillis());
            running = false;
            executor.shutdown();

            try {
                System.out.println("Waiting for users to finish their last requests...");
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                } else {
                    System.out.println("All users finished gracefully");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int totalRequests = successCount.get() + errorCount.get();

        System.out.println("=== RESULTS ===");
        System.out.println("Duration: " + duration / 1000 + " seconds");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());

        if (totalRequests > 0) {
            System.out.println("Avg Response Time: " + (totalResponseTime / totalRequests) + " ms");
        } else {
            System.out.println("Avg Response Time: N/A (no requests completed)");
        }
    }
}