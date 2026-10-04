package actor.starintel.zcode.remote;

/** UI-thread-owned state; page readiness is deliberately NOT agent connectivity. */
public final class SessionState {
    public enum Phase { IDLE, LOADING, PAGE_READY, FAILED, CLOSED }
    private long generation;
    private Phase phase = Phase.IDLE;
    public long begin() {
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("Session generation exhausted");
        generation++;
        phase = Phase.LOADING;
        return generation;
    }
    public void ready(long eventGeneration) {
        if (eventGeneration == generation && phase == Phase.LOADING) phase = Phase.PAGE_READY;
    }
    public void fail(long eventGeneration) {
        if (eventGeneration == generation && (phase == Phase.LOADING || phase == Phase.PAGE_READY)) {
            phase = Phase.FAILED;
        }
    }
    public void close() { phase = Phase.CLOSED; }
    public Phase phase() { return phase; }
    public boolean accepts(long eventGeneration) {
        return generation == eventGeneration && phase != Phase.CLOSED;
    }
}
