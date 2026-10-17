package optimizer;

/**
 * Cooperative control of real evaluations; waiting/presentation time is excluded from search time.
 */
public final class SearchControl {
    private volatile boolean paused, stopped;
    private int permits;
    private volatile long waitingNanos;
    private final int presentationDelay;

    public SearchControl(int presentationDelay) {
        this.presentationDelay = presentationDelay;
    }

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        permits = 0;
        notifyAll();
    }

    public synchronized void step() {
        paused = true;
        permits++;
        notifyAll();
    }

    public synchronized void stop() {
        stopped = true;
        notifyAll();
    }

    public boolean stopped() {
        return stopped;
    }

    public boolean paused() {
        return paused;
    }

    public long waitingNanos() {
        return waitingNanos;
    }

    public void beforeEvaluation() {
        if (!paused || stopped) return;
        long start = System.nanoTime();
        synchronized (this) {
            while (paused && permits == 0 && !stopped)
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    stopped = true;
                }
            if (permits > 0) permits--;
        }
        waitingNanos += System.nanoTime() - start;
    }

    public void pace() {
        if (presentationDelay == 0 || paused || stopped) return;
        long start = System.nanoTime();
        synchronized (this) {
            try {
                wait(presentationDelay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                stopped = true;
            }
        }
        waitingNanos += System.nanoTime() - start;
    }
}
