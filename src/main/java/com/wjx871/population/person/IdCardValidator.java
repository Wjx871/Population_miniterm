package com.wjx871.population.person;

import com.wjx871.population.common.BusinessException;
import java.time.DateTimeException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** GB 11643 18位公民身份号码校验与解析。 */
@Component
public class IdCardValidator {
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    public Identity parse(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (!value.matches("\\d{17}[0-9X]")) bad("身份证号必须为18位，前17位为数字，末位为数字或X");
        if ("000000".equals(value.substring(0, 6))) bad("身份证地址码无效");
        LocalDate birthDate;
        try {
            birthDate = LocalDate.of(Integer.parseInt(value.substring(6, 10)),
                    Integer.parseInt(value.substring(10, 12)), Integer.parseInt(value.substring(12, 14)));
        } catch (DateTimeException | NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "身份证出生日期无效");
        }
        if (birthDate.isAfter(LocalDate.now())) bad("身份证出生日期不得晚于当前日期");
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (value.charAt(i) - '0') * WEIGHTS[i];
        if (CHECK_CODES[sum % 11] != value.charAt(17)) bad("身份证校验位错误");
        return new Identity(value, birthDate, ((value.charAt(16) - '0') & 1) == 1 ? "M" : "F");
    }

    public Identity validate(String raw, LocalDate suppliedBirthDate, String suppliedGender) {
        Identity identity = parse(raw);
        if (suppliedBirthDate != null && !identity.birthDate().equals(suppliedBirthDate)) bad("出生日期与身份证不一致");
        String gender = normalizeGender(suppliedGender);
        if (!identity.gender().equals(gender)) bad("性别与身份证顺序码不一致");
        return identity;
    }

    private static void bad(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST, message); }
    private static String normalizeGender(String raw) {String value=raw==null?"":raw.trim().toUpperCase();return switch(value){case "M","男","1"->"M";case "F","女","2"->"F";default->{bad("性别必须为 M 或 F");yield null;}};}
    public record Identity(String normalized, LocalDate birthDate, String gender) {}
}
