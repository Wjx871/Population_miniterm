package com.wjx871.population.migration;
import java.time.*; import lombok.Data;
@Data public class Residence { private Long residenceId; private Long personId; private Long householdId; private String registeredAddress; private String regionCode; private String registerTypeCode; private LocalDate registerDate; private LocalDate startDate; private String status; private Long createdBy; private Integer version; }
