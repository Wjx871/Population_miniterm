package com.wjx871.population.approval;
import com.wjx871.population.security.DataScopeCriteria; import java.util.*; import org.apache.ibatis.annotations.*;
@Mapper public interface ApprovalRequestMapper {
    int insert(ApprovalRequest value);
    Optional<ApprovalRequest> selectById(Long id);
    Optional<ApprovalRequest> selectByApplicationId(Long id);
    int decide(@Param("id")Long id,@Param("from")ApprovalStatus from,@Param("to")ApprovalStatus to,@Param("version")int version,@Param("userId")Long userId,@Param("comment")String comment);
    /**
     * 申请人被退回后重新提交：把审批单的状态从 APPROVED/REJECTED 重置为 PENDING，
     * 并把版本号归零（避免与旧决策乐观锁冲突）；新提交人写入新一批提交时间。
     * 由于 sys_approval_request 在 application_id 上有 UNIQUE，这里必须复用旧行，
     * 否则会触发重复键错误。
     */
    int resetForResubmit(@Param("applicationId") Long applicationId,
                          @Param("userId") Long userId,
                          @Param("submittedAt") java.time.LocalDateTime submittedAt);
    List<ApprovalSummary> selectByStatus(@Param("pending")boolean pending,@Param("scope")DataScopeCriteria scope);
}
