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
            System.out.println("Waiting 10 seconds for Elasticsearch to be fully ready...");
            Thread.sleep(10000);
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
}
