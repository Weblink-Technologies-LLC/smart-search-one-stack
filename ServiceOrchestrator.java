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
        
        System.out.println("Testing Elasticsearch connectivity at: " + elasticUrl);
        
        int maxAttempts = 20;
        int attemptDelay = 3000; // 3 seconds between attempts
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                java.net.URL url = new java.net.URL(elasticUrl + "/_cluster/health");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                
                String auth = elasticUsername + ":" + elasticPassword;
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    System.out.println("✅ Elasticsearch is ready and responding to health checks (HTTP " + responseCode + ")");
                    
                    System.out.println("Waiting additional 15 seconds for Elasticsearch internal readiness...");
                    Thread.sleep(15000);
                    return;
                } else {
                    System.out.println("⏳ Elasticsearch not ready yet (attempt " + attempt + "/" + maxAttempts + ") - HTTP " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                System.out.println("⏳ Elasticsearch connectivity test failed (attempt " + attempt + "/" + maxAttempts + "): " + e.getMessage());
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(attemptDelay);
            }
        }
        
        System.err.println("⚠️  WARNING: Elasticsearch did not become ready after " + maxAttempts + " attempts");
        System.err.println("   Proceeding with search-admin startup, but it may fail during Elasticsearch registration");
        System.err.println("   Total wait time: " + (maxAttempts * attemptDelay / 1000) + " seconds");
        
        System.out.println("Adding 30-second safety buffer for Elasticsearch readiness...");
        Thread.sleep(30000);
    }
}
