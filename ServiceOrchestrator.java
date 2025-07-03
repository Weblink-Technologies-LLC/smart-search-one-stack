import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ServiceOrchestrator {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Smart Search One Stack Services...");
        
        new File("/tmp").mkdirs();
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Process> processes = new ArrayList<>();
        
        try {
            Process adminProcess = startService("SEARCH-ADMIN", "/search-admin", "search-admin-3.0.7-SNAPSHOT.jar", "/tmp/ss-admin.log");
            processes.add(adminProcess);
            
            Process apiProcess = startService("SEARCH-API", "/search-api", "smart-search-api-3.0.7-SNAPSHOT.jar", "/tmp/ss-api.log");
            processes.add(apiProcess);
            
            Process utilProcess = startService("UTIL-SERVICES", "/smart-search-util", "smart-search-util-3.0.7-SNAPSHOT.jar", "/tmp/ss-utils.log");
            processes.add(utilProcess);
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down services...");
                for (Process p : processes) {
                    if (p.isAlive()) {
                        p.destroyForcibly();
                    }
                }
            }));
            
            System.out.println("All services started. Waiting for processes...");
            
            boolean allRunning = true;
            while (allRunning) {
                Thread.sleep(1000); // Check every second
                for (int i = 0; i < processes.size(); i++) {
                    Process process = processes.get(i);
                    if (!process.isAlive()) {
                        String serviceName = (i == 0) ? "SEARCH-ADMIN" : (i == 1) ? "SEARCH-API" : "UTIL-SERVICES";
                        System.err.println("ERROR: " + serviceName + " process exited with code: " + process.exitValue());
                        allRunning = false;
                        break;
                    }
                }
            }
            
            for (Process process : processes) {
                process.waitFor();
            }
            
        } catch (Exception e) {
            System.err.println("Error in service orchestration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            executor.shutdown();
        }
    }
    
    private static Process startService(String serviceName, String workDir, String jarFile, String logFile) throws Exception {
        System.out.println("Starting " + serviceName + "...");
        
        if (serviceName.equals("SEARCH-ADMIN")) {
            System.out.println("Waiting for Elasticsearch to be fully ready before starting search-admin...");
            waitForElasticsearch();
        }
        
        Map<String, String> env = new HashMap<>(System.getenv());
        File envFile = new File(workDir + "/.env");
        if (envFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            env.put(key, value);
                            System.out.println("Set env var: " + key + " = " + (key.contains("SECRET") || key.contains("PASSWORD") ? "[HIDDEN]" : value));
                        }
                    }
                }
            }
        }
        
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith("LICENSE_") || entry.getKey().startsWith("APPLICATION_")) {
                env.put(entry.getKey(), entry.getValue());
                System.out.println("Added system env: " + entry.getKey() + " = " + (entry.getKey().contains("SECRET") ? "[HIDDEN]" : entry.getValue()));
            }
        }
        
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", workDir + "/" + jarFile);
        pb.environment().putAll(env);
        
        pb.redirectOutput(new File(logFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(logFile)));
        
        Process process = pb.start();
        System.out.println(serviceName + " started with PID: " + process.pid());
        
        Thread.sleep(2000);
        if (!process.isAlive()) {
            System.err.println("WARNING: " + serviceName + " exited immediately with code: " + process.exitValue());
            System.err.println("Check log file: " + logFile);
        } else {
            System.out.println(serviceName + " is running successfully");
        }
        
        return process;
    }
    
    private static void waitForElasticsearch() throws Exception {
        String elasticUrl = System.getenv("INTERNAL_SEARCH_ENGINE_URL");
        String elasticUsername = System.getenv("INTERNAL_SEARCH_ENGINE_USERNAME");
        String elasticPassword = System.getenv("INTERNAL_SEARCH_ENGINE_PASSWORD");
        
        if (elasticUrl == null) {
            elasticUrl = "http://elasticsearch-secure:9200";
        }
        if (elasticUsername == null) {
            elasticUsername = "elastic";
        }
        if (elasticPassword == null) {
            elasticPassword = "Cu5BAieKx8cpD4q";
        }
        
        System.out.println("🔍 Testing Elasticsearch connectivity at: " + elasticUrl);
        System.out.println("   Using credentials: " + elasticUsername + " / " + (elasticPassword != null ? elasticPassword.substring(0, Math.min(4, elasticPassword.length())) + "..." : "null"));
        
        int maxAttempts = 30;
        int attemptDelay = 3000; // 3 seconds between attempts
        boolean elasticsearchReady = false;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                java.net.URL url = new java.net.URL(elasticUrl + "/_cluster/health");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                
                String auth = elasticUsername + ":" + elasticPassword;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String responseBody = response.toString();
                    System.out.println("✅ Elasticsearch connectivity successful (attempt " + attempt + "/" + maxAttempts + ") - HTTP " + responseCode);
                    System.out.println("   Cluster health response: " + responseBody.substring(0, Math.min(100, responseBody.length())) + "...");
                    
                    if (responseBody.contains("\"status\":\"green\"") || responseBody.contains("\"status\":\"yellow\"")) {
                        System.out.println("✅ Elasticsearch cluster is healthy and ready for search-admin registration");
                        elasticsearchReady = true;
                        break;
                    } else {
                        System.out.println("⏳ Elasticsearch cluster not yet healthy, continuing to wait...");
                    }
                } else {
                    System.out.println("⏳ Elasticsearch not ready yet (attempt " + attempt + "/" + maxAttempts + ") - HTTP " + responseCode);
                }
                
                connection.disconnect();
            } catch (java.net.ConnectException e) {
                System.out.println("⏳ Elasticsearch connection refused (attempt " + attempt + "/" + maxAttempts + ") - service not yet available");
            } catch (java.net.UnknownHostException e) {
                System.out.println("⏳ Elasticsearch hostname not resolved (attempt " + attempt + "/" + maxAttempts + ") - DNS not ready: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("⏳ Elasticsearch connectivity test failed (attempt " + attempt + "/" + maxAttempts + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(attemptDelay);
            }
        }
        
        if (elasticsearchReady) {
            System.out.println("🔍 Performing additional Elasticsearch readiness verification...");
            
            for (int j = 1; j <= 5; j++) {
                try {
                    java.net.URL url = new java.net.URL(elasticUrl + "/_cat/indices");
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    
                    String auth = elasticUsername + ":" + elasticPassword;
                    String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                    connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                    
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        System.out.println("✅ Elasticsearch index operations ready (verification " + j + "/5)");
                        break;
                    } else {
                        System.out.println("⏳ Waiting for index operations readiness... (verification " + j + "/5) - HTTP " + responseCode);
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    System.out.println("⏳ Index operations test failed (verification " + j + "/5): " + e.getMessage());
                }
                
                if (j < 5) {
                    Thread.sleep(2000);
                }
            }
            
            System.out.println("⏳ Adding 25-second buffer for complete Elasticsearch internal readiness...");
            Thread.sleep(25000);
            System.out.println("✅ Elasticsearch is fully ready for search-admin service startup");
        } else {
            System.err.println("⚠️  WARNING: Elasticsearch did not become ready after " + maxAttempts + " attempts");
            System.err.println("   Total wait time: " + (maxAttempts * attemptDelay / 1000) + " seconds");
            System.err.println("   Proceeding with search-admin startup, but it may fail during Elasticsearch registration");
            
            System.out.println("⏳ Adding 45-second safety buffer for potential Elasticsearch readiness...");
            Thread.sleep(45000);
            System.out.println("⚠️  Search-admin startup proceeding despite Elasticsearch connectivity issues");
        }
    }
}
