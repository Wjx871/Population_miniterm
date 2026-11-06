package com.wjx871.population.assistant;

import com.wjx871.population.s2.trace.S2TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;

/**
 * Read-only checklist tool for the policy task agent. It turns retrieved
 * evidence into a user-confirmed preparation list; it never invokes OCR or
 * writes to an application.
 */
@Service
public class PolicyWorkflowService {
    private final PolicyAssistantService policyService;

    public PolicyWorkflowService(PolicyAssistantService policyService) {
        this.policyService = policyService;
    }

    public WorkflowResponse generateChecklist(String question, boolean ignoredIdCardRecognized) {
        String traceId = S2TraceContext.resolveTraceId(ThreadContext.get("traceId"));
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            Intent intent = Intent.from(question);
            PolicyAssistantService.QueryResponse policy = policyService.query(question);
            if (PolicyAssistantService.MODE_SAFE_REFUSAL.equals(policy.mode())) {
                return new WorkflowResponse(intent.label,
                        List.of("classify_intent", "sensitive_guard"),
                        policy, List.of(), "INSUFFICIENT", PolicyAssistantService.REFUSAL, null);
            }
            if (PolicyAssistantService.MODE_SERVICE_ERROR.equals(policy.mode())) {
                return new WorkflowResponse(intent.label,
                        List.of("classify_intent", "search_policy"),
                        policy, List.of(), "INSUFFICIENT", PolicyAssistantService.SERVICE_ERROR_MESSAGE, null);
            }
            if (policy.citations().isEmpty()) {
                return new WorkflowResponse(intent.label,
                        List.of("classify_intent", "search_policy", "check_evidence"),
                        policy, List.of(), "INSUFFICIENT", "未找到足够的内置指南依据，无法生成核验建议。", null);
            }
            String evidenceStatus = policy.evidenceLevel() != null
                    ? policy.evidenceLevel()
                    : (policy.confidence() >= .28 ? "SUFFICIENT" : "PARTIAL");
            List<ChecklistItem> items = new ArrayList<>();
            items.add(new ChecklistItem("办理事项", "已识别",
                    "当前识别为“" + intent.label + "”。请在当前权限范围内继续办理。", false));
            items.add(new ChecklistItem("政策依据", "已检索 " + policy.citations().size() + " 条",
                    "以下材料和步骤仅依据此次检索到的业务指南生成。", false));
            policy.citations().stream()
                    .filter(c -> c.section().contains("材料") || c.section().contains("信息"))
                    .findFirst()
                    .ifPresent(c -> items.add(new ChecklistItem("材料核对", "待用户确认", c.summary(), true)));
            policy.citations().stream()
                    .filter(c -> c.section().contains("步骤") || c.section().contains("办理说明") || c.section().contains("后续"))
                    .findFirst()
                    .ifPresent(c -> items.add(new ChecklistItem("办理流程", "待按步骤办理", c.summary(), true)));
            items.add(new ChecklistItem("提交前确认", "待人工确认",
                    "请核对申请页面的必填项与材料清单。助手不会自动提交、审批或修改业务数据。", true));
            String next = "请核对以上任务项，再进入当前账号已授权的业务功能完成申请。";
            return new WorkflowResponse(
                    intent.label,
                    List.of("classify_intent", "search_policy", "get_process_guide", "check_evidence", "generate_checklist"),
                    policy, List.copyOf(items), evidenceStatus, next, intent.path
            );
        }
    }

    private enum Intent {
        MIGRATION_IN("户籍迁入", "/migrations/in/apply"),
        MIGRATION_OUT("户籍迁出", "/migrations/out/apply"),
        RESIDENCE_PERMIT("居住证办理", "/residence-permits"),
        HOUSEHOLD("家庭户办理", "/households"),
        CANCELLATION("注销业务", "/cancellations"),
        APPROVAL("申请审批", "/approvals"),
        OTHER("业务办理咨询", "/applications");

        final String label;
        final String path;

        Intent(String label, String path) {
            this.label = label;
            this.path = path;
        }

        static Intent from(String q) {
            String value = q == null ? "" : q;
            if (value.contains("迁入")) return MIGRATION_IN;
            if (value.contains("迁出")) return MIGRATION_OUT;
            if (value.contains("居住证")) return RESIDENCE_PERMIT;
            if (value.contains("家庭户") || value.contains("新增成员")) return HOUSEHOLD;
            if (value.contains("注销")) return CANCELLATION;
            if (value.contains("审批") || value.contains("驳回") || value.contains("退回")) return APPROVAL;
            return OTHER;
        }
    }

    public record ChecklistItem(String name, String status, String guidance, boolean needsConfirmation) {
    }

    public record WorkflowResponse(String businessType, List<String> usedTools,
                                   PolicyAssistantService.QueryResponse policy,
                                   List<ChecklistItem> checklist, String evidenceStatus,
                                   String nextStep, String actionPath) {
    }
}
