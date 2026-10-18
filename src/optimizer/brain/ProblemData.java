package optimizer.brain;



import java.util.*;

/** Known inputs, separate from the service-to-server decisions. Units: CPU cores, GB, ms. */
public record ProblemData(
        String name, List<Server> servers, List<Service> services, boolean assessment) {
    /**
     * Hardware description for a single machine in the placement problem.
     */
    public record Server(
            String name,
            double cpu,
            double ram,
            double storage,
            double latencyMs,
            int gpu,
            int maxServices,
            double speed,
            double idlePower,
            double peakPower,
            double busyPrice,
            double bandwidth) {
    }

    /**
     * Workload description for a single service that must be placed on a server.
     */
    public record Service(
            String name,
            double cpu,
            double ram,
            double storage,
            double work,
            int gpu,
            double payload,
            double maxLatency) {
    }

    public ProblemData {
        servers = List.copyOf(servers);
        services = List.copyOf(services);
        if (servers.size() != 4 || services.size() != 12 || !assessment)
            throw new IllegalArgumentException("Use the assessment dataset: 4 servers and 12 services");
        Set<String> names = new HashSet<>();
        for (Server s : servers) {
            if (s.name().isBlank() || !names.add(s.name()))
                throw new IllegalArgumentException("Duplicate/blank server name");
            positive(s.cpu(), s.ram(), s.storage());
            nonnegative(s.latencyMs());
            if (assessment) {
                if (!Double.isFinite(s.speed()) || s.speed() <= 0)
                    throw new IllegalArgumentException(
                            s.name() + ": speed GI/s must be finite and positive");
                if (!Double.isFinite(s.bandwidth()) || s.bandwidth() <= 0)
                    throw new IllegalArgumentException(
                            s.name() + ": bandwidth Mb/s must be finite and positive");
                nonnegative(s.gpu(), s.maxServices(), s.idlePower(), s.peakPower(), s.busyPrice());
                if (s.peakPower() < s.idlePower())
                    throw new IllegalArgumentException(
                            s.name() + ": peak power must be >= idle power");
            }
        }
        names.clear();
        for (Service s : services) {
            if (s.name().isBlank() || !names.add(s.name()))
                throw new IllegalArgumentException("Duplicate/blank service name");
            nonnegative(s.cpu(), s.ram(), s.storage());
            if (assessment) {
                positive(s.work());
                nonnegative(s.gpu(), s.payload(), s.maxLatency());
            }
        }
        long size = 1;
        for (int i = 0; i < services.size(); i++) size = Math.multiplyExact(size, servers.size());
    }

    private static void positive(double... values) {
        for (double v : values)
            if (!Double.isFinite(v) || v <= 0)
                throw new IllegalArgumentException("Capacities must be finite and positive");
    }

    private static void nonnegative(double... values) {
        for (double v : values)
            if (!Double.isFinite(v) || v < 0)
                throw new IllegalArgumentException("Data must be finite and nonnegative");
    }

    public long searchSpace() {
        long total = 1;
        for (int i = 0; i < services.size(); i++) total = Math.multiplyExact(total, servers.size());
        return total;
    }

    public static ProblemData assessmentSupplied() {
        try {
            java.nio.file.Path folder = java.nio.file.Path.of("data", "assessment");
            if (!java.nio.file.Files.exists(folder.resolve("servers.csv"))) {
                java.nio.file.Path code =
                        java.nio.file.Path.of(
                                ProblemData.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI());
                folder = code.getParent().resolve("data/assessment");
            }
            return java.nio.file.Files.exists(folder.resolve("servers.csv"))
                    ? CsvFiles.read(folder)
                    : assessmentDefaults();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid assessment CSV data: " + ex.getMessage(), ex);
        }
    }

    public static ProblemData assessmentDefaults() {
        return new ProblemData(
                "assessment-latency-v1",
                List.of(
                        new Server("A", 16, 32, 160, 18, 0, 4, 12, 70, 150, .003, 500),
                        new Server("B", 24, 48, 250, 30, 0, 5, 20, 95, 210, .005, 800),
                        new Server("C", 32, 64, 400, 60, 2, 5, 34, 140, 290, .010, 1200),
                        new Server("D", 20, 40, 240, 42, 1, 4, 24, 115, 235, .007, 650)),
                List.of(
                        new Service("S01", 4, 8, 30, 90, 0, .5, 55),
                                new Service("S02", 6, 12, 40, 140, 0, 1, 95),
                        new Service("S03", 5, 10, 50, 120, 0, 1.5, 60),
                                new Service("S04", 7, 14, 60, 175, 0, .8, 75),
                        new Service("S05", 3, 6, 25, 65, 0, .6, 100),
                                new Service("S06", 8, 16, 70, 210, 1, 2, 100),
                        new Service("S07", 5, 10, 45, 130, 0, 1.2, 65),
                                new Service("S08", 4, 8, 40, 100, 0, .5, 75),
                        new Service("S09", 6, 12, 55, 165, 1, 1, 80),
                                new Service("S10", 3, 6, 30, 75, 0, .3, 95),
                        new Service("S11", 7, 14, 75, 190, 1, 2.5, 105),
                                new Service("S12", 5, 10, 45, 145, 0, .8, 80)),
                true);
    }

    public static Candidate referenceAssignment() {
        return new Candidate(new int[] {0, 2, 0, 3, 2, 2, 0, 3, 3, 2, 2, 1});
    }

}
