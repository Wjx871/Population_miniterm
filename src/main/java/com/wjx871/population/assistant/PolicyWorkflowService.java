package com.wjx871.population.assistant;

import org.springframework.stereotype.Service;

import java.util.List;

/** Bounded workflow coordinator: it only selects policy retrieval and checklist tools; it cannot mutate business data. */
@Service
public class PolicyWorkflowService {
    private final PolicyAssistantService policyService;
    public PolicyWorkflowService(PolicyAssistantService policyService) { this.policyService = policyService; }

    public WorkflowResponse generateChecklist(String question, boolean idCardRecognized) {
        Intent intent = Intent.from(question);
        PolicyAssistantService.QueryResponse policy = policyService.query(question);
        if (policy.citations().isEmpty()) return new WorkflowResponse(intent.label, List.of("search_policy"), policy, List.of(), "未找到足够的内置指南依据，无法生成材料核验建议。", null);
        List<ChecklistItem> items = List.of(
                new ChecklistItem("业务申请信息", "待填写", "请在对应业务申请页面按必填项完成填写。", false),
                new ChecklistItem("身份证明材料", idCardRecognized ? "已识别，待用户确认" : "待提供", idCardRecognized ? "已完成辅助识别，识别结果仅作核验使用。" : "可上传身份证正面图片进行辅助核验。", true),
                new ChecklistItem("其他业务材料", "待人工确认", "内置指南未预设统一材料名称；请以申请页面要求和主管部门最新规定为准。", true)
        );
        String next = idCardRecognized ? "请确认识别结果，并进入相关业务入口补充申请信息和其他材料。" : "可先上传身份证正面图片进行辅助核验，再进入相关业务入口补充申请信息。";
        return new WorkflowResponse(intent.label, List.of("search_policy", "generate_checklist", idCardRecognized ? "extract_document" : "" ).stream().filter(s -> !s.isBlank()).toList(), policy, items, next, intent.path);
    }

    private enum Intent {
        MIGRATION_IN("户籍迁入", "/migrations"), MIGRATION_OUT("户籍迁出", "/migrations"),
        RESIDENCE_PERMIT("居住证办理", "/residence-permits"), HOUSEHOLD("家庭户办理", "/households"),
        CANCELLATION("注销业务", "/applications"), APPROVAL("申请审批", "/approvals"), OTHER("业务办理咨询", "/applications");
        final String label, path; Intent(String label, String path) { this.label=label; this.path=path; }
        static Intent from(String q) { if(q.contains("迁入"))return MIGRATION_IN; if(q.contains("迁出"))return MIGRATION_OUT; if(q.contains("居住证"))return RESIDENCE_PERMIT; if(q.contains("家庭户")||q.contains("新增成员"))return HOUSEHOLD; if(q.contains("注销"))return CANCELLATION; if(q.contains("审批")||q.contains("驳回")||q.contains("退回"))return APPROVAL; return OTHER; }
    }
    public record ChecklistItem(String name, String status, String guidance, boolean needsConfirmation) { }
    public record WorkflowResponse(String businessType, List<String> usedTools, PolicyAssistantService.QueryResponse policy, List<ChecklistItem> checklist, String nextStep, String actionPath) { }
}
