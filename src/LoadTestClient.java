import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LoadTestClient {
    // Thread-safe counters for tracking results across multiple threads
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static volatile double totalResponseTime = 0;
    private static volatile double minResponseTime = Long.MAX_VALUE;
    private static volatile double maxResponseTime = 0;
    private static volatile boolean running = true; // Flag to stop threads
    private static final List<Double> responseTimes = Collections.synchronizedList(new ArrayList<>());
    static final int CONCURRENT_USERS = 5;
    static final int TEST_DURATION_SECONDS = 10;
    static double cpuUsage;


    public static void main(String[] args) throws Exception {

        if (checkServerRunning()) {
            System.out.println("✓ Server is already running on port 8080");
        } else {

            System.out.println("Server is not running. Starting embedded server...");

            new Thread(() -> {
                try {
                    Server.main(null);
                } catch (Exception e) {
                    System.err.println("Failed to start server: " + e.getMessage());
                }
            }, "Embedded-Server-Thread").start();

            int attempts = 0;
            while (attempts < 3) {
                Thread.sleep(500); // Wait 500ms between checks
                if (checkServerRunning()) {
                    Server.setRunning(true);
                    System.out.println("✓ Embedded server started successfully");
                    break;
                }
                attempts++;
            }
        }
        long startTime = System.currentTimeMillis();
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

                                // Calculate response time in Nanos
                                double respTimeNanos = (double) (System.nanoTime() - reqStart);

                                // Update metrics atomically
                                synchronized (LoadTestClient.class) {
                                    totalResponseTime += respTimeNanos;
                                    if (respTimeNanos < minResponseTime) minResponseTime = respTimeNanos;
                                    if (respTimeNanos > maxResponseTime) maxResponseTime = respTimeNanos;
                                    responseTimes.add(respTimeNanos);
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
            OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuUsage = osBean.getProcessCpuLoad() * 100.0;
            running = false;
            executor.shutdown();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        printResults(startTime);
    }

    private static double nanoToMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static boolean checkServerRunning() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void printResults(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        int totalReqs = totalRequests.get();
        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        double medianResponseTimeMs = responseTimes.isEmpty() ? 0 :
                nanoToMs(
                        responseTimes.stream()
                                .sorted()
                                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    int mid = list.size() / 2;
                                    return list.size() % 2 == 0
                                            ? (list.get(mid - 1) + list.get(mid)) / 2
                                            : list.get(mid);
                                }))
                );

        StringBuilder results = new StringBuilder();
        results.append("\n====== PERFORMANCE TEST RESULTS ======\n");
        results.append(String.format("Test Duration: %.1f seconds\n", duration / 1000.0));
        results.append(String.format("Concurrent Users: %d\n", CONCURRENT_USERS));

        results.append("\n--- REQUEST METRICS ---\n");
        results.append(String.format("Total Requests: %d\n", totalReqs));
        results.append(String.format("Successful Requests: %d\n", successCount.get()));
        results.append(String.format("Failed Requests: %d\n", errorCount.get()));
        results.append(String.format("Success Rate: %.2f%%\n", totalReqs > 0 ? (successCount.get() * 100.0 / totalReqs) : 0));

        if (totalReqs > 0) {
            results.append(String.format("Response Time (Average): %.3f ms\n", nanoToMs(totalResponseTime) / totalReqs));
            results.append(String.format("Response Time (Min): %.3f ms\n", minResponseTime == Double.MAX_VALUE ? 0 : nanoToMs(minResponseTime)));
            results.append(String.format("Response Time (Max): %.3f ms\n", nanoToMs(maxResponseTime)));
            results.append(String.format("Response Time (Median): %.3f ms\n", medianResponseTimeMs));
        } else {
            results.append("Response Time: N/A (no requests completed)\n");
        }

        double rps = totalReqs / (duration / 1000.0);
        results.append("\n=== SERVER PERFORMANCE ANALYSIS ===\n");
        results.append(String.format("Server throughput: %.2f requests/second\n", rps));
        results.append(String.format("Memory footprint: %dMB\n", usedMemory));
        results.append(String.format("CPU usage: %.2f%%\n", cpuUsage));

        System.out.print(results);
    }

}