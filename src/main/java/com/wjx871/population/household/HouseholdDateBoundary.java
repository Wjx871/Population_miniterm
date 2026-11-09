package com.wjx871.population.household;

import com.wjx871.population.common.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Date checks run after the caller has locked the household, in that same transaction. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class HouseholdDateBoundary {
    private final HouseholdMapper mapper;

    public void validateJoinDate(LocalDate establishDate, LocalDate joinDate) {
        if (joinDate.isBefore(establishDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "入户日期不能早于家庭户立户日期");
        }
    }

    public void validateEstablishDate(Long householdId, LocalDate establishDate) {
        // Locking/current read: include historical rows and avoid an older RR snapshot.
        mapper.lockEarliestJoinDate(householdId).ifPresent(earliest -> {
            if (establishDate.isAfter(earliest)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "立户日期不能晚于现有或历史成员的入户日期");
            }
        });
    }

    public void validateRestoration(Long householdId, Long personId, LocalDate establishDate) {
        for (HouseholdMember historical : mapper.lockRestorableMembers(householdId, personId)) {
            if (historical.getJoinDate().isBefore(establishDate)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "历史成员入户日期早于立户日期，请核查历史记录后再办理迁入");
            }
        }
    }
}
