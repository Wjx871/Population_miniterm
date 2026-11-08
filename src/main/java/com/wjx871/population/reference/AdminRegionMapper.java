package com.wjx871.population.reference;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminRegionMapper {
    Optional<AdminRegion> findByCode(String code);
    Optional<AdminRegion> findById(Long id);
    Optional<AdminRegion> shareById(Long id);
    Optional<AdminRegion> lockById(Long id);
    List<AdminRegion> shareChildrenNowait(Long parentId);
    List<AdminRegion> search(@Param("parentCode") String parentCode, @Param("level") Integer level,
                            @Param("status") String status, @Param("keyword") String keyword);
    void insert(AdminRegion value);
    int update(@Param("value") AdminRegion value, @Param("version") Integer version);
    int updateStatus(@Param("code") String code, @Param("from") String from,
                     @Param("to") String to, @Param("version") Integer version);
}
