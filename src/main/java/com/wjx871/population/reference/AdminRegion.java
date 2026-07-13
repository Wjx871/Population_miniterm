package com.wjx871.population.reference;
import java.time.LocalDateTime;import java.util.*;import lombok.Data;
@Data public class AdminRegion {private Long regionId;private String regionCode;private String regionName;private Long parentId;private String parentCode;private Integer regionLevel;private String fullName;private Integer sortNo;private String status;private Integer version;private LocalDateTime createdAt;private LocalDateTime updatedAt;private List<AdminRegion> children=new ArrayList<>();}
