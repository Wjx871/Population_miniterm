package com.wjx871.population.system;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;

@Tag("unit")
class RequestMetricsTest {
    @Test void percentilesAndServerErrorRateUseTheSameRequestWindow() {
        var metrics = new RequestMetrics();
        metrics.record(1_000_000, 200);
        metrics.record(2_000_000, 401);
        metrics.record(3_000_000, 500);
        metrics.record(4_000_000, 200);
        var snapshot = metrics.snapshot();
        assertThat(snapshot.p50Ms()).isEqualTo(2);
        assertThat(snapshot.p95Ms()).isEqualTo(4);
        assertThat(snapshot.serverErrorRate()).isEqualTo(.25);
        assertThat(snapshot.totalServerErrors()).isEqualTo(1);
    }
    @Test void retentionIsBoundedAndSignalsTruncation() {
        var metrics = new RequestMetrics();
        for (int index = 0; index < 5000; index++) metrics.record(1, 200);
        assertThat(metrics.snapshot().samples()).isEqualTo(4096);
        assertThat(metrics.snapshot().totalRequests()).isEqualTo(5000);
        assertThat(metrics.snapshot().capacityLimited()).isTrue();
    }
}
