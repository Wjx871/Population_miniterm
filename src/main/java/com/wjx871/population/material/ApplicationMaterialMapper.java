package com.wjx871.population.material;
import java.util.List; import java.util.Optional; import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param;
@Mapper public interface ApplicationMaterialMapper {
    int insert(ApplicationMaterial value); Optional<ApplicationMaterial> selectById(Long id); List<ApplicationMaterial> selectByApplicationId(Long applicationId);
    long countRequired(Long applicationId); long countRequiredNotVerified(Long applicationId); int deleteById(Long id);
    int verify(@Param("id") Long id,@Param("status") MaterialVerifyStatus status,@Param("userId") Long userId,@Param("comment") String comment);
}
