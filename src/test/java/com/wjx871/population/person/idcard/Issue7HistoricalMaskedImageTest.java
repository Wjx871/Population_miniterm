package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import com.wjx871.population.security.SensitiveDataMaskingService;
import org.junit.jupiter.api.Test;

class Issue7HistoricalMaskedImageTest {
    @Test void preexistingMaskedColumnCannotExposeHistoricalShortIdentity() {
        PersonIdCardImage row = new PersonIdCardImage();
        row.setOcrStatus(OcrStatus.SUCCESS);
        row.setOcrIdcardFull("abcdefgh");
        row.setOcrIdcardMasked("abcd**********efgh");
        assertThat(PersonIdCardImageView.fromUpload(row, new SensitiveDataMaskingService()).ocrIdcardMasked()).isEqualTo("****");
        for (boolean privileged : new boolean[] {false, true}) {
            var view = PersonIdCardImageView.fromDetail(row, privileged, new SensitiveDataMaskingService());
            assertThat(view.ocrIdcardMasked()).isEqualTo("****");
        }
    }

    @Test void absentFullValueDoesNotTrustStoredMask() {
        PersonIdCardImage row = new PersonIdCardImage();
        var masking = new SensitiveDataMaskingService();
        assertThat(PersonIdCardImageView.fromDetail(row, false, masking).ocrIdcardMasked()).isNull();
        row.setOcrIdcardMasked("abcd**********efgh");
        assertThat(PersonIdCardImageView.fromUpload(row, masking).ocrIdcardMasked()).isEqualTo("****");
        assertThat(PersonIdCardImageView.fromDetail(row, false, masking).ocrIdcardMasked()).isEqualTo("****");
    }
}
