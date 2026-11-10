package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;

class Issue37ExpiryPropertiesTest {
    @Test void boundedDefaultAndBoundaryValues() {
        ResidencePermitProperties p=new ResidencePermitProperties();
        assertThat(p.getExpiryBatchSize()).isEqualTo(256);
        p.setExpiryBatchSize(1); assertThat(p.getExpiryBatchSize()).isOne();
        p.setExpiryBatchSize(1000); assertThat(p.getExpiryBatchSize()).isEqualTo(1000);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1001"})
    void invalidConfigurationFailsBinding(String value) {
        Binder binder=new Binder(new MapConfigurationPropertySource(Map.of("residence-permit.expiry-batch-size",value)));
        assertThatThrownBy(()->binder.bind("residence-permit",Bindable.of(ResidencePermitProperties.class)))
            .isInstanceOf(BindException.class).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
