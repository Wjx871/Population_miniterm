package com.wjx871.population.reference;import java.time.LocalDateTime;import lombok.Data;
@Data public class DataDictionary {private Long dictId;private String dictType;private String dictCode;private String dictName;private Integer sortNo;private String status;private Integer version;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
