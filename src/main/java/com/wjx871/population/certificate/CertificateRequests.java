package com.wjx871.population.certificate;import jakarta.validation.constraints.*;import java.time.LocalDate;
record CertificateCreateRequest(@NotNull Long personId,@NotBlank@Size(max=30)String certificateType,@NotBlank@Size(max=50)String certificateNo,@NotNull LocalDate issueDate,LocalDate expireDate){}
record CertificateUpdateRequest(@NotBlank@Size(max=30)String certificateType,@NotBlank@Size(max=50)String certificateNo,@NotNull LocalDate issueDate,LocalDate expireDate,@NotNull Integer version){}
record CertificateCancelRequest(@NotBlank@Size(max=500)String reason,@NotNull Integer version){}
