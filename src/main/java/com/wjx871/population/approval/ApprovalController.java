package com.wjx871.population.approval;
import com.wjx871.population.common.ApiResponse; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import java.util.List; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/approvals") @RequiredArgsConstructor public class ApprovalController {private final ApprovalService service;
 @GetMapping("/pending") @PreAuthorize("hasAuthority('approval:view')") public ApiResponse<List<ApprovalSummary>> pending(){return ApiResponse.ok(service.pending());}
 @GetMapping("/processed") @PreAuthorize("hasAuthority('approval:view')") public ApiResponse<List<ApprovalSummary>> processed(){return ApiResponse.ok(service.processed());}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('approval:view')") public ApiResponse<ApprovalDetailView> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('approval:handle')") public ApiResponse<Void> approve(@PathVariable Long id,@Valid @RequestBody ApprovalDecisionRequest body,HttpServletRequest req){service.approve(id,body,req);return ApiResponse.ok(null);}
 @PostMapping("/{id}/approve-and-create-person") @PreAuthorize("hasAuthority('approval:handle')") public ApiResponse<Void> approveAndCreatePerson(@PathVariable Long id,@Valid @RequestBody ApprovalCreatePersonRequest body,HttpServletRequest req){service.approveAndCreatePerson(id,body,req);return ApiResponse.ok(null);}
 @PostMapping("/{id}/reject") @PreAuthorize("hasAuthority('approval:handle')") public ApiResponse<Void> reject(@PathVariable Long id,@Valid @RequestBody ApprovalDecisionRequest body,HttpServletRequest req){service.reject(id,body,req);return ApiResponse.ok(null);}
}
