package com.wjx871.population.dataops;

final class ReadRouteContext {
    private static final ThreadLocal<Boolean> REPLICA = new ThreadLocal<>();
    private ReadRouteContext() {}
    static boolean replica() { return Boolean.TRUE.equals(REPLICA.get()); }
    static Boolean set(boolean replica) { Boolean previous = REPLICA.get(); REPLICA.set(replica); return previous; }
    static void restore(Boolean previous) { if (previous == null) REPLICA.remove(); else REPLICA.set(previous); }
}
