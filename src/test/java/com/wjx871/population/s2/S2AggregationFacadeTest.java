package com.wjx871.population.s2;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsCalculator;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.stats.StatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S2AggregationFacadeTest {

    @Test
    void mapsAuthorizedSummaryCountsIntoAggregateOnlyRemoteRequest() throws Exception {
        StatisticsService statistics = mock(StatisticsService.class);
        PopulationMetricsClient remote = mock(PopulationMetricsClient.class);
        when(statistics.getSummary()).thenReturn(Map.of(
                "residentCount", 100L, "floatingCount", 15L, "keyPopulationCount", 9L,
                "householdCount", 35L, "isMock", false));
        when(remote.aggregate(any())).thenAnswer(invocation ->
                new PopulationMetricsCalculator().calculate(invocation.getArgument(0)));

        PopulationMetricsResult result = new S2AggregationFacade(statistics, remote)
                .aggregateAuthorizedSummary("trace-s2-1");

        ArgumentCaptor<PopulationMetricsRequest> requestCaptor = ArgumentCaptor.forClass(PopulationMetricsRequest.class);
        verify(remote).aggregate(requestCaptor.capture());
        PopulationMetricsRequest request = requestCaptor.getValue();
        assertThat(request.getTraceId()).isEqualTo("trace-s2-1");
        assertThat(request.getSchemaVersion()).isEqualTo("1.0");
        assertThat(request.getResidentCount()).isEqualTo(100L);
        assertThat(request.getFloatingRegistrationCount()).isEqualTo(15L);
        assertThat(request.getActiveKeyPopulationCount()).isEqualTo(9L);
        assertThat(request.getActiveHouseholdCount()).isEqualTo(35L);
        assertThat(result.getFloatingRegistrationToResidentRatio()).isEqualByComparingTo(new BigDecimal("0.1500"));
    }

    @Test
    void doesNotSendStatisticsMarkedAsMockToTheRemoteService() throws Exception {
        StatisticsService statistics = mock(StatisticsService.class);
        PopulationMetricsClient remote = mock(PopulationMetricsClient.class);
        when(statistics.getSummary()).thenReturn(Map.of(
                "residentCount", 100L, "floatingCount", 15L, "keyPopulationCount", 9L,
                "householdCount", 35L, "isMock", true));

        assertThatThrownBy(() -> new S2AggregationFacade(statistics, remote)
                .aggregateAuthorizedSummary("trace-s2-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("will not label mock statistics");
        verify(remote, never()).aggregate(any());
    }

    @Test
    void rejectsCountValuesOutsideTheSignedLongProtocolRange() throws Exception {
        StatisticsService statistics = mock(StatisticsService.class);
        PopulationMetricsClient remote = mock(PopulationMetricsClient.class);
        when(statistics.getSummary()).thenReturn(Map.of(
                "residentCount", new java.math.BigInteger("9223372036854775808"),
                "floatingCount", 0L, "keyPopulationCount", 0L, "householdCount", 0L, "isMock", false));

        assertThatThrownBy(() -> new S2AggregationFacade(statistics, remote)
                .aggregateAuthorizedSummary("trace-s2-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out-of-range residentCount");
        verify(remote, never()).aggregate(any());
    }
}
