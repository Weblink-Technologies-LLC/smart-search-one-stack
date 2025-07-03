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
            
            while (true) {
                Thread.sleep(5000); // Check every 5 seconds
                for (int i = 0; i < processes.size(); i++) {
                    Process process = processes.get(i);
                    if (!process.isAlive()) {
                        String serviceName = (i == 0) ? "SEARCH-ADMIN" : (i == 1) ? "SEARCH-API" : "UTIL-SERVICES";
                        String workDir = (i == 0) ? "/search-admin" : (i == 1) ? "/search-api" : "/smart-search-util";
                        String jarFile = (i == 0) ? "search-admin-3.0.7-SNAPSHOT.jar" : (i == 1) ? "smart-search-api-3.0.7-SNAPSHOT.jar" : "smart-search-util-3.0.7-SNAPSHOT.jar";
                        String logFile = (i == 0) ? "/tmp/ss-admin.log" : (i == 1) ? "/tmp/ss-api.log" : "/tmp/ss-utils.log";
                        
                        System.err.println("ERROR: " + serviceName + " process exited with code: " + process.exitValue());
                        
                        if (serviceName.equals("SEARCH-ADMIN")) {
                            System.out.println("Restarting SEARCH-ADMIN service...");
                            try {
                                Process newProcess = startService(serviceName, workDir, jarFile, logFile);
                                processes.set(i, newProcess);
                                System.out.println("SEARCH-ADMIN service restarted successfully");
                            } catch (Exception e) {
                                System.err.println("Failed to restart SEARCH-ADMIN: " + e.getMessage());
                                Thread.sleep(10000); // Wait 10 seconds before trying again
                            }
                        } else {
                            System.err.println("Critical service " + serviceName + " failed. Exiting...");
                            System.exit(1);
                        }
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
            System.out.println("Waiting for all dependencies to be fully ready before starting search-admin...");
            waitForMongoDB();
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
    
    private static void waitForMongoDB() throws Exception {
        String mongoUri = System.getenv("MONGODB_URI");
        if (mongoUri == null) {
            mongoUri = "mongodb://root:SecureMongoPass2024@mongodb-secure:27017/ss-test-onestack?authSource=admin&retryWrites=true&w=majority";
        }
        
        System.out.println("🔍 Enhanced MongoDB readiness verification for search-admin compatibility");
        System.out.println("   Testing MongoDB connection and authentication...");
        
        String mongoHost = "mongodb-secure";
        int mongoPort = 27017;
        String username = "root";
        String password = "SecureMongoPass2024";
        String authDatabase = "admin";
        
        int maxAttempts = 30;
        int attemptDelay = 2000; // 2 seconds between attempts
        boolean mongoReady = false;
        
        System.out.println("🔍 Stage 1: Testing MongoDB connectivity and authentication...");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(mongoHost, mongoPort), 5000);
                socket.close();
                
                System.out.println("✅ MongoDB connectivity successful (attempt " + attempt + "/" + maxAttempts + ")");
                System.out.println("   MongoDB server is accepting connections on " + mongoHost + ":" + mongoPort);
                mongoReady = true;
                break;
                
            } catch (java.net.ConnectException e) {
                System.out.println("⏳ MongoDB connection refused (attempt " + attempt + "/" + maxAttempts + ") - service not yet available");
            } catch (java.net.UnknownHostException e) {
                System.out.println("⏳ MongoDB hostname not resolved (attempt " + attempt + "/" + maxAttempts + ") - DNS not ready: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("⏳ MongoDB connectivity test failed (attempt " + attempt + "/" + maxAttempts + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(attemptDelay);
            }
        }
        
        if (mongoReady) {
            System.out.println("🔍 Stage 2: Adding buffer for MongoDB user initialization and authentication readiness...");
            System.out.println("   This ensures MongoDB root user is fully initialized before search-admin connects");
            Thread.sleep(15000); // 15 seconds buffer for user initialization
            System.out.println("✅ MongoDB is fully ready for search-admin service startup");
        } else {
            System.err.println("⚠️  WARNING: MongoDB did not become ready after " + maxAttempts + " attempts");
            System.err.println("   Total wait time: " + (maxAttempts * attemptDelay / 1000) + " seconds");
            System.err.println("   Proceeding with search-admin startup, but it may fail during MongoDB connection");
            
            System.out.println("⏳ Adding safety buffer for potential MongoDB readiness...");
            Thread.sleep(30000); // 30 seconds safety buffer
            System.out.println("⚠️  Search-admin startup proceeding despite MongoDB connectivity issues");
        }
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
        
        System.out.println("🔍 Enhanced Elasticsearch readiness verification for search-admin compatibility");
        System.out.println("   Testing connectivity at: " + elasticUrl);
        System.out.println("   Using credentials: " + elasticUsername + " / " + (elasticPassword != null ? elasticPassword.substring(0, Math.min(4, elasticPassword.length())) + "..." : "null"));
        
        int maxAttempts = 60;
        int attemptDelay = 5000; // 5 seconds between attempts
        boolean elasticsearchReady = false;
        
        System.out.println("🔍 Stage 1: Testing basic Elasticsearch connectivity and cluster health...");
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
                    
                    if (responseBody.contains("\"status\":\"green\"") || responseBody.contains("\"status\":\"yellow\"")) {
                        System.out.println("✅ Elasticsearch cluster health is acceptable: " + (responseBody.contains("green") ? "GREEN" : "YELLOW"));
                        elasticsearchReady = true;
                        break;
                    } else {
                        System.out.println("⏳ Elasticsearch cluster not yet healthy, continuing to wait... (status: " + responseBody + ")");
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
            System.out.println("🔍 Stage 2: Testing Elasticsearch index operations for search-admin compatibility...");
            boolean indexOpsReady = false;
            
            for (int j = 1; j <= 10; j++) {
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
                        System.out.println("✅ Elasticsearch index operations ready (verification " + j + "/10)");
                        indexOpsReady = true;
                        break;
                    } else {
                        System.out.println("⏳ Waiting for index operations readiness... (verification " + j + "/10) - HTTP " + responseCode);
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    System.out.println("⏳ Index operations test failed (verification " + j + "/10): " + e.getMessage());
                }
                
                if (j < 10) {
                    Thread.sleep(3000);
                }
            }
            
            if (indexOpsReady) {
                System.out.println("🔍 Stage 3: Testing search engine registration endpoints...");
                boolean registrationReady = false;
                
                for (int k = 1; k <= 5; k++) {
                    try {
                        java.net.URL url = new java.net.URL(elasticUrl + "/_nodes");
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                        
                        String auth = elasticUsername + ":" + elasticPassword;
                        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                        
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        
                        int responseCode = connection.getResponseCode();
                        if (responseCode == 200) {
                            System.out.println("✅ Elasticsearch registration endpoints ready (test " + k + "/5)");
                            registrationReady = true;
                            break;
                        } else {
                            System.out.println("⏳ Registration endpoints not ready... (test " + k + "/5) - HTTP " + responseCode);
                        }
                        connection.disconnect();
                    } catch (Exception e) {
                        System.out.println("⏳ Registration endpoint test failed (test " + k + "/5): " + e.getMessage());
                    }
                    
                    if (k < 5) {
                        Thread.sleep(2000);
                    }
                }
                
                if (registrationReady) {
                    System.out.println("🔍 Stage 4: Adding extended buffer for complete Elasticsearch internal readiness...");
                    System.out.println("   This ensures all internal Elasticsearch services are fully initialized");
                    Thread.sleep(60000); // 60 seconds buffer
                    System.out.println("✅ Elasticsearch is fully ready for search-admin service startup");
                } else {
                    System.out.println("⚠️  Registration endpoints not fully ready, adding safety buffer...");
                    Thread.sleep(45000);
                    System.out.println("⚠️  Proceeding with search-admin startup despite registration endpoint issues");
                }
            } else {
                System.out.println("⚠️  Index operations not fully ready, adding safety buffer...");
                Thread.sleep(45000);
                System.out.println("⚠️  Proceeding with search-admin startup despite index operation issues");
            }
        } else {
            System.err.println("⚠️  WARNING: Elasticsearch did not become ready after " + maxAttempts + " attempts");
            System.err.println("   Total wait time: " + (maxAttempts * attemptDelay / 1000) + " seconds");
            System.err.println("   Proceeding with search-admin startup, but it may fail during Elasticsearch registration");
            
            System.out.println("⏳ Adding extended safety buffer for potential Elasticsearch readiness...");
            Thread.sleep(120000); // 2 minutes safety buffer
            System.out.println("⚠️  Search-admin startup proceeding despite Elasticsearch connectivity issues");
        }
    }
}
