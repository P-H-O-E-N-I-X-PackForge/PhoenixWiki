package net.phoenixvine.wiki.client.rich.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeLoadingTimers {

    private FakeLoadingTimers() {}

    public static final long DELAY_MS = 550L;

    private static final Map<String, Long> startedAtMs = new ConcurrentHashMap<>();

    public static void start(String expandKey) {
        startedAtMs.putIfAbsent(expandKey, System.currentTimeMillis());
    }

    public static boolean isLoading(String expandKey) {
        Long startedAt = startedAtMs.get(expandKey);
        if (startedAt == null) return false;
        if (System.currentTimeMillis() - startedAt >= DELAY_MS) {
            startedAtMs.remove(expandKey);
            return false;
        }
        return true;
    }
}
