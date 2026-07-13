package com.wjx871.population.audit;

import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogQueryMapper {
    long count(@Param("loginOnly") boolean loginOnly, @Param("username") String username,
            @Param("operationType") String operationType, @Param("module") String module,
            @Param("result") String result, @Param("ip") String ip, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("scope") DataScopeCriteria scope);
    List<LogQueryView> search(@Param("loginOnly") boolean loginOnly, @Param("username") String username,
            @Param("operationType") String operationType, @Param("module") String module,
            @Param("result") String result, @Param("ip") String ip, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("scope") DataScopeCriteria scope,
            @Param("limit") int limit, @Param("offset") long offset);
    Optional<LogQueryView> find(@Param("id") Long id, @Param("scope") DataScopeCriteria scope);
}
