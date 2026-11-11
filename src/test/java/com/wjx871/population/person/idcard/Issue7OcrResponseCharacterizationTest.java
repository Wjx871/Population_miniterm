package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.wjx871.population.security.SensitiveDataMaskingService;

class Issue7OcrResponseCharacterizationTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "\"name\":{\"nested\":\"synthetic\"}", "\"name\":[1,2]", "\"name\":42",
        "\"address\":{\"nested\":\"synthetic\"}", "\"gender\":[\"F\"]",
        "\"ethnicity\":true", "\"birthDate\":{\"year\":1990}",
        "\"confidence\":\"0.9\"", "\"confidence\":1.1", "\"confidence\":-0.1"
    })
    void rejectsInvalidOptionalFieldTypesAndConfidence(String field) {
        String response = "{\"result\":{\"idCard\":\"110105199001010010\"," + field + "}}";
        assertThatThrownBy(() -> recognize(response)).isInstanceOf(PaddleOcrHttpClient.OcrRequestException.class)
                .hasMessage("OCR_INVALID_RESPONSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"123\"", "\"1101051990010100100\"",
            "\"110105199013010010\"", "\"110105199001010011\"", "{\"id\":1}"})
    void rejectsInvalidIdentityValues(String id) {
        assertThatThrownBy(() -> recognize("{\"result\":{\"idCard\":" + id + "}}"))
                .isInstanceOf(PaddleOcrHttpClient.OcrRequestException.class).hasMessage("OCR_INVALID_RESPONSE");
    }

    @Test
    void acceptsValidXAndChineseText() {
        var result = recognize("{\"result\":{\"idCard\":\"11010519900101007X\",\"name\":\"合成姓名\",\"address\":\"合成地址\",\"confidence\":0.9}}");
        assertThat(result.idCard()).isEqualTo("11010519900101007X");
        assertThat(result.name()).isEqualTo("合成姓名");
        assertThat(result.address()).isEqualTo("合成地址");
    }

    @Test
    void rejectsMissingIdentityAndMalformedDate() {
        assertThatThrownBy(() -> recognize("{\"result\":{}}"))
                .isInstanceOf(PaddleOcrHttpClient.OcrRequestException.class);
        assertThatThrownBy(() -> recognize("{\"result\":{\"idCard\":\"110105199001010010\",\"birthDate\":\"1990-02-30\"}}"))
                .isInstanceOf(PaddleOcrHttpClient.OcrRequestException.class);
    }

    @Test
    void maskingAndRawPayloadRedactionHandleShortAndNullValues() {
        SensitiveDataMaskingService masking = new SensitiveDataMaskingService();
        assertThat(masking.identityAlways(null)).isNull();
        assertThat(masking.identityAlways("")).isEqualTo("****");
        assertThat(masking.identityAlways("1234567")).isEqualTo("****");
        assertThat(masking.addressAlways(null)).isNull();
        assertThat(masking.addressAlways("合成地址")).isEqualTo("详细地址已隐藏");
        OcrLogRedactor redactor = new OcrLogRedactor();
        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("{\"nested\":{\"address\":\"合成地址\"}}"))
                .isEqualTo("{\"redacted\":true}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"result\":[]}"})
    void rejectsMissingOrInvalidResult(String response) {
        assertThatThrownBy(() -> recognize(response)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsStructuredObjectsInTextFields() {
        assertThatThrownBy(() -> recognize("""
                    {"result":{"idCard":"110105199001010010","name":{"nested":"synthetic"}}}
                    """)).isInstanceOf(PaddleOcrHttpClient.OcrRequestException.class);
    }

    private IdCardOcrProperties.OcrResult recognize(String response) {
        IdCardOcrProperties properties = new IdCardOcrProperties();
        properties.setUrl("http://ocr.test");
        PaddleOcrHttpClient client = new PaddleOcrHttpClient(properties);
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer stub = MockRestServiceServer.bindTo(rest).build();
        stub.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        try {
            return client.recognize(new byte[]{1}, "image/jpeg");
        } finally {
            stub.verify();
        }
    }
}
