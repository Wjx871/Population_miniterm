package com.wjx871.population.household;
import jakarta.validation.constraints.*; import java.time.LocalDate;
record HouseholdCreateRequest(@NotBlank @Size(max=30) String householdNo,@NotBlank @Size(max=255) String address,@NotBlank @Size(max=20) String regionCode,@NotBlank @Size(max=30) String householdType,@NotNull LocalDate establishDate,Long headPersonId){}
record HouseholdUpdateRequest(@NotBlank @Size(max=255) String address,@NotBlank @Size(max=20) String regionCode,@NotBlank @Size(max=30) String householdType,@NotNull LocalDate establishDate,@NotBlank String status,@NotNull Integer version){}
record MemberAddRequest(@NotNull Long personId,@NotBlank @Size(max=30) String relationship,@NotNull LocalDate joinDate){}
record MemberUpdateRequest(@NotBlank @Size(max=30) String relationship,@NotNull Integer version){}
record MemberLeaveRequest(@NotNull LocalDate leaveDate,@NotNull Integer version){}
record ChangeHeadRequest(@NotNull Long newHeadPersonId,@NotBlank @Size(max=500) String reason,@NotNull Integer version){}
