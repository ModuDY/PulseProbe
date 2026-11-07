package com.monitordata.fusion.metrics;

import java.util.Map;

/**
 * 指标目录：统一 ID、单位与类型。口径在服务端固定，前端只按 ID 取值。
 */
public final class MetricCatalog {

    public static final String HOST_CPU_USAGE = "host.cpu.usage";
    public static final String HOST_MEM_AVAILABLE = "host.mem.available";
    public static final String HOST_SWAP_USED = "host.swap.used";
    public static final String HOST_DISK_READ_LATENCY = "host.disk.read.latency";
    public static final String DB_CONNECTIONS = "db.connections";
    public static final String DB_THREADS_RUNNING = "db.threads.running";
    public static final String DB_BUFFER_POOL_HIT = "db.innodb.buffer_pool.hit";
    public static final String DB_LOCK_WAITS = "db.innodb.lock_waits";
    public static final String CONTAINER_CPU_CORES = "container.cpu.cores";
    public static final String CONTAINER_MEM_USED = "container.mem.used";
    public static final String SERVICE_REQUEST_RATE = "service.request.rate";
    public static final String SERVICE_ERROR_RATE = "service.error.rate";
    public static final String SERVICE_P95 = "service.latency.p95";
    public static final String PROBE_HTTP_DURATION = "probe.http.duration";

    public record Definition(String metricId, String unit, MetricKind kind) {
    }

    private static final Map<String, Definition> DEFINITIONS = Map.ofEntries(
            Map.entry(HOST_CPU_USAGE, new Definition(HOST_CPU_USAGE, "percent", MetricKind.GAUGE)),
            Map.entry(HOST_MEM_AVAILABLE, new Definition(HOST_MEM_AVAILABLE, "bytes", MetricKind.GAUGE)),
            Map.entry(HOST_SWAP_USED, new Definition(HOST_SWAP_USED, "bytes", MetricKind.GAUGE)),
            Map.entry(HOST_DISK_READ_LATENCY, new Definition(HOST_DISK_READ_LATENCY, "milliseconds", MetricKind.DERIVED)),
            Map.entry(DB_CONNECTIONS, new Definition(DB_CONNECTIONS, "count", MetricKind.GAUGE)),
            Map.entry(DB_THREADS_RUNNING, new Definition(DB_THREADS_RUNNING, "count", MetricKind.GAUGE)),
            Map.entry(DB_BUFFER_POOL_HIT, new Definition(DB_BUFFER_POOL_HIT, "percent", MetricKind.DERIVED)),
            Map.entry(DB_LOCK_WAITS, new Definition(DB_LOCK_WAITS, "count", MetricKind.DERIVED)),
            Map.entry(CONTAINER_CPU_CORES, new Definition(CONTAINER_CPU_CORES, "cores", MetricKind.GAUGE)),
            Map.entry(CONTAINER_MEM_USED, new Definition(CONTAINER_MEM_USED, "bytes", MetricKind.GAUGE)),
            Map.entry(SERVICE_REQUEST_RATE, new Definition(SERVICE_REQUEST_RATE, "requests_per_second", MetricKind.DERIVED)),
            Map.entry(SERVICE_ERROR_RATE, new Definition(SERVICE_ERROR_RATE, "ratio", MetricKind.DERIVED)),
            Map.entry(SERVICE_P95, new Definition(SERVICE_P95, "milliseconds", MetricKind.DERIVED)),
            Map.entry(PROBE_HTTP_DURATION, new Definition(PROBE_HTTP_DURATION, "milliseconds", MetricKind.DERIVED)));

    private MetricCatalog() {
    }

    public static Definition require(String metricId) {
        Definition definition = DEFINITIONS.get(metricId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown metric id: " + metricId);
        }
        return definition;
    }

    public static boolean exists(String metricId) {
        return DEFINITIONS.containsKey(metricId);
    }
}
