package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class Phase11QueryValidationTest {
 private final Phase11QueryService service=new Phase11QueryService(mock(Phase11QueryMapper.class),mock(SensitiveDataMaskingService.class),Clock.systemUTC());
 @Test void personPageSizeIsBounded(){assertThatThrownBy(()->service.persons(emptyPerson(),0,101,"personId,DESC")).isInstanceOf(ResponseStatusException.class);}
 @Test void personSortFieldIsWhitelisted(){assertThatThrownBy(()->service.persons(emptyPerson(),0,10,"idCard,DESC")).isInstanceOf(ResponseStatusException.class);}
 @Test void personSortDirectionIsWhitelisted(){assertThatThrownBy(()->service.persons(emptyPerson(),0,10,"name,DROP TABLE")).isInstanceOf(ResponseStatusException.class);}
 @Test void householdMemberRangeIsValidated(){HouseholdQueryCriteria q=new HouseholdQueryCriteria(null,null,null,null,null,null,3,1,null);assertThatThrownBy(()->service.households(q,0,10)).isInstanceOf(ResponseStatusException.class);}
 @Test void migrationDateRangeIsValidated(){MigrationQueryCriteria q=new MigrationQueryCriteria(null,null,null,null,null,null,java.time.LocalDate.of(2026,2,1),java.time.LocalDate.of(2026,1,1),null);assertThatThrownBy(()->service.migrations(q,0,10)).isInstanceOf(ResponseStatusException.class);}
 private PersonQueryCriteria emptyPerson(){return new PersonQueryCriteria(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);}
}
