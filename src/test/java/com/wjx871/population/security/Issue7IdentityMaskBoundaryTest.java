package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class Issue7IdentityMaskBoundaryTest {
    private final SensitiveDataMaskingService service = new SensitiveDataMaskingService();

    @Test void nullAndEmptyRemainCompatible() {
        assertThat(service.identityAlways(null)).isNull();
        assertThat(service.identityAlways("")).isEqualTo("****");
    }

    @Test void nonstandardValuesAreConservativelyMasked() {
        for (int n = 1; n <= 17; n++) {
            assertThat(service.identityAlways("abcdefghijklmnopq".substring(0, n)))
                .as("nonstandard length %s", n).isEqualTo("****");
        }
        for (String value : new String[]{"abcdefghijklmnopqr", "abcdefghijklmnopqrs", "中文测试甲乙丙丁", "🙂abcdefg"}) {
            assertThat(service.identityAlways(value)).as("nonstandard characters or length").isEqualTo("****");
        }
    }

    @Test void standardFormatIsPreservedIncludingX() {
        assertThat(service.identityAlways("1101" + "0519800101" + "0016")).isEqualTo("1101**********0016");
        assertThat(service.identityAlways("1101" + "0519800101" + "001X")).isEqualTo("1101**********001X");
    }
}
