package com.wjx871.population.migration;
import java.time.*;
public record ArchiveView(Long archiveId,Long personId,Long householdId,Long applicationId,String archiveType,String archiveReason,String personName,String identityNo,String householdNo,String registeredAddress,String regionCode,LocalDate startDate,LocalDate endDate,LocalDateTime archivedAt) {
 public static ArchiveView from(ResidenceArchive a,boolean full){return new ArchiveView(a.getArchiveId(),a.getPersonId(),a.getHouseholdId(),a.getApplicationId(),a.getArchiveType(),a.getArchiveReason(),a.getPersonNameSnapshot(),full?a.getIdentityNoSnapshot():mask(a.getIdentityNoSnapshot()),a.getHouseholdNoSnapshot(),a.getRegisteredAddressSnapshot(),a.getRegionCodeSnapshot(),a.getStartDateSnapshot(),a.getEndDateSnapshot(),a.getArchivedAt());}
 private static String mask(String v){return v==null||v.length()<8?"****":v.substring(0,4)+"**********"+v.substring(v.length()-4);}
}
