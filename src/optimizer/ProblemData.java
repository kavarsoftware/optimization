package optimizer;

import java.util.*;

/** Known inputs, separate from the service-to-server decisions. Units: CPU cores, GB, ms. */
public record ProblemData(String name, List<Server> servers, List<Service> services) {
    public record Server(String name, double cpu, double ram, double storage,
                         double latencyMs, double costPerCpu) {}
    public record Service(String name, double cpu, double ram, double storage,
                          double latencyValue) {}

    public ProblemData {
        servers = List.copyOf(servers);
        services = List.copyOf(services);
        if (servers.isEmpty() || services.isEmpty()) throw new IllegalArgumentException("Empty problem");
        Set<String> names = new HashSet<>();
        for (Server s : servers) {
            if (s.name().isBlank() || !names.add(s.name())) throw new IllegalArgumentException("Duplicate/blank server name");
            positive(s.cpu(), s.ram(), s.storage()); nonnegative(s.latencyMs(), s.costPerCpu());
        }
        names.clear();
        for (Service s : services) {
            if (s.name().isBlank() || !names.add(s.name())) throw new IllegalArgumentException("Duplicate/blank service name");
            nonnegative(s.cpu(), s.ram(), s.storage(), s.latencyValue());
        }
        long size = 1;
        for (int i = 0; i < services.size(); i++) size = Math.multiplyExact(size, servers.size());
    }
    private static void positive(double... values) {
        for (double v : values) if (!Double.isFinite(v) || v <= 0) throw new IllegalArgumentException("Capacities must be finite and positive");
    }
    private static void nonnegative(double... values) {
        for (double v : values) if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Data must be finite and nonnegative");
    }
    public long searchSpace() {
        long total = 1;
        for (int i = 0; i < services.size(); i++) total = Math.multiplyExact(total, servers.size());
        return total;
    }
    public static ProblemData demo() {
        return new ProblemData("godar-4x12-v1", List.of(
            new Server("S1",48,96,1000,20,.08), new Server("S2",24,192,1200,35,.05),
            new Server("S3",16,64,4000,50,.025), new Server("S4",20,48,500,5,.06)), List.of(
            new Service("Auth",4,8,20,9), new Service("Database",8,48,600,5),
            new Service("Cache",4,32,10,8), new Service("Web",4,8,30,10),
            new Service("Video",16,16,200,2), new Service("Analytics",10,24,400,2),
            new Service("Search",8,28,150,6), new Service("Email",2,4,50,3),
            new Service("Files",2,8,1200,3), new Service("Gateway",6,8,20,10),
            new Service("Backup",2,4,900,1), new Service("ML",12,24,100,4)));
    }
}
