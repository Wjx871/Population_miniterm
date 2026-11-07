package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Run only on the unmodified baseline: -Dissue34.red=true -Dtest=Issue34FirstIssueRedTest. */
@EnabledIfSystemProperty(named="issue34.red", matches="true")
class Issue34FirstIssueRedTest extends Issue34HttpSupport {
    @ParameterizedTest @ValueSource(strings={"DRAFT", "UNDER_REVIEW", "APPROVED", "RETURNED"})
    void duplicateCreatesTwoRealHttpDrafts(String state) throws Exception {
        long f = floating(9340001);
        long app = applicationId(first(f, "population"));
        if (!state.equals("DRAFT")) { materials(app, true); submit(app); }
        if (state.equals("APPROVED") || state.equals("RETURNED")) approve(app);
        if (state.equals("RETURNED")) returned(app);
        assertThat(state(app)).isEqualTo(state);
        List<Integer> before = snapshot();
        check(first(f, "population"), 201);
        assertThat(count("business_application")).isEqualTo(before.get(0)+1);
        assertThat(count("residence_permit_application")).isEqualTo(before.get(1)+1);
        evidence("RED state="+state+" HTTP=201,201");
    }
    @Test void anotherApplicantAlsoCreatesDuplicate() throws Exception {
        long f=floating(9340001);
        applicationId(first(f,"population"));
        check(first(f,"admin"),201);
        assertThat(count("residence_permit_application")).isEqualTo(2);
        evidence("RED different-applicant HTTP=201,201");
    }
    @Test void simultaneousHttpRequestsBothCreateDrafts() throws Exception {
        long f=floating(9340001);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> a=pool.submit(()->{start.await();return first(f,"population").getStatusCode().value();});
            Future<Integer> b=pool.submit(()->{start.await();return first(f,"admin").getStatusCode().value();});
            start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactly(201,201);
            assertThat(count("residence_permit_application")).isEqualTo(2);
            evidence("RED concurrent HTTP=201,201");
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
}
