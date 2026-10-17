package optimizer;

/**
 * One complete proposal: server index for every service. Defensive copies protect the incumbent.
 */
public final class Candidate {
    private final int[] assignment;

    public Candidate(int[] assignment) {
        this.assignment = assignment.clone();
    }

    public int server(int service) {
        return assignment[service];
    }

    public int size() {
        return assignment.length;
    }

    public int[] copy() {
        return assignment.clone();
    }

    public long code(int serverCount) {
        long code = 0;
        for (int s : assignment) code = code * serverCount + s;
        return code;
    }
}
