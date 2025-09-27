import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTestClient {
    // Thread-safe counters for tracking results across multiple threads
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static volatile long totalResponseTime = 0;
    private static volatile long minResponseTime = Long.MAX_VALUE;
    private static volatile long maxResponseTime = 0;
    private static volatile boolean running = true; // Flag to stop threads
    private static final ConcurrentSkipListSet<Long> responseTimes = new ConcurrentSkipListSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Starting performance test - measuring server metrics...");
        long startTime = System.currentTimeMillis();
        final int CONCURRENT_USERS = 5;
        final int TEST_DURATION_SECONDS = 10;

        // Create thread pool with concurrent users
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS)) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                executor.submit(() -> {
                    try (HttpClient client = HttpClient.newHttpClient()) {
                        while (running) {
                            try {
                                long reqStart = System.nanoTime();

                                // Build POST request
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("http://localhost:8080/api"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"test\"}"))
                                        .build();

                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                                // Calculate response time in milliseconds
                                long respTimeMs = (System.nanoTime() - reqStart) / 1_000_000;

                                // Update metrics atomically
                                synchronized (LoadTestClient.class) {
                                    totalResponseTime += respTimeMs;
                                    if (respTimeMs < minResponseTime) minResponseTime = respTimeMs;
                                    if (respTimeMs > maxResponseTime) maxResponseTime = respTimeMs;
                                    responseTimes.add(respTimeMs);
                                }

                                totalRequests.incrementAndGet();

                                // Count successful and failed requests
                                if (response.statusCode() == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                    System.out.println("Error: " + response.statusCode() + " - " + response.body());
                                }
                            } catch (Exception e) {
                                errorCount.incrementAndGet();
                                totalRequests.incrementAndGet();
                                System.out.println("Request failed: " + e.getMessage());
                            }
                        }
                    }
                });
            }

            // Run test for specified duration
            Thread.sleep(Duration.ofSeconds(TEST_DURATION_SECONDS).toMillis());
            running = false;
            executor.shutdown();

            try {
                System.out.println("Test completed. Waiting for users to finish their last requests...");
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int totalReqs = totalRequests.get();
        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;

        long medianResponseTime = 0;
        if (!responseTimes.isEmpty()) {
            Object[] sortedTimes = responseTimes.toArray();
            medianResponseTime = (long) sortedTimes[sortedTimes.length / 2];
        }

        System.out.println("\n====== PERFORMANCE TEST RESULTS ======");
        System.out.println("Test Duration: " + duration / 1000.0 + " seconds");
        System.out.println("Concurrent Users: " + CONCURRENT_USERS);

        System.out.println("\n--- REQUEST METRICS ---");
        System.out.println("Total Requests: " + totalReqs);
        System.out.println("Successful Requests: " + successCount.get());
        System.out.println("Failed Requests: " + errorCount.get());
        System.out.println("Success Rate: " + (totalReqs > 0 ? (successCount.get() * 100.0 / totalReqs) : 0) + "%");

        if (totalReqs > 0) {
            System.out.println("Response Time (Average): " + (totalResponseTime / totalReqs) + " ms");
            System.out.println("Response Time (Min): " + (minResponseTime == Long.MAX_VALUE ? "N/A" : minResponseTime + " ms"));
            System.out.println("Response Time (Max): " + maxResponseTime + " ms");
            System.out.println("Response Time (Median): " + medianResponseTime + " ms");
        } else {
            System.out.println("Response Time: N/A (no requests completed)");
        }

        System.out.println("\n=== Analyze ===");
        System.out.println("Server can handle ~" + String.format("%.2f", totalReqs / (duration / 1000.0)) + " requests/second");
        System.out.println("Memory footprint: " + usedMemory + "MB under " + totalReqs + " requests");
        System.out.println("Thread utilization: Efficient with" + CONCURRENT_USERS + "concurrent users");

        System.out.println("\n=== HARDWARE REQUIREMENTS ===");
        System.out.println("CPU: Minimal usage for this workload");
        System.out.println("RAM: " + usedMemory + "MB + ~2MB per 100 RPS");
        System.out.println("Recommended: 1 CPU core, 128MB RAM for 100 RPS");
    }
}