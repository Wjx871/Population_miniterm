package com.wjx871.population.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only checklist tool for the policy task agent. It turns retrieved
 * evidence into a user-confirmed preparation list; it never invokes OCR or
 * writes to an application.
 */
@Service
public class PolicyWorkflowService {
    private final PolicyAssistantService policyService;

    public PolicyWorkflowService(PolicyAssistantService policyService) { this.policyService = policyService; }

    public WorkflowResponse generateChecklist(String question, boolean ignoredIdCardRecognized) {
        Intent intent = Intent.from(question);
        PolicyAssistantService.QueryResponse policy = policyService.query(question);
        if (policy.citations().isEmpty()) {
            return new WorkflowResponse(intent.label, List.of("classify_intent", "search_policy", "check_evidence"), policy,
                    List.of(), "INSUFFICIENT", "\u672a\u627e\u5230\u8db3\u591f\u7684\u5185\u7f6e\u6307\u5357\u4f9d\u636e\uff0c\u65e0\u6cd5\u751f\u6210\u6838\u9a8c\u5efa\u8bae\u3002", null);
        }
        String evidenceStatus = policy.confidence() >= .28 ? "SUFFICIENT" : "PARTIAL";
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem("\u529e\u7406\u4e8b\u9879", "\u5df2\u8bc6\u522b", "\u5f53\u524d\u8bc6\u522b\u4e3a\u201c" + intent.label + "\u201d\u3002\u8bf7\u5728\u5f53\u524d\u6743\u9650\u8303\u56f4\u5185\u7ee7\u7eed\u529e\u7406\u3002", false));
        items.add(new ChecklistItem("\u653f\u7b56\u4f9d\u636e", "\u5df2\u68c0\u7d22 " + policy.citations().size() + " \u6761", "\u4ee5\u4e0b\u6750\u6599\u548c\u6b65\u9aa4\u4ec5\u4f9d\u636e\u6b64\u6b21\u68c0\u7d22\u5230\u7684\u4e1a\u52a1\u6307\u5357\u751f\u6210\u3002", false));
        policy.citations().stream().filter(c -> c.section().contains("\u6750\u6599") || c.section().contains("\u4fe1\u606f"))
                .findFirst().ifPresent(c -> items.add(new ChecklistItem("\u6750\u6599\u6838\u5bf9", "\u5f85\u7528\u6237\u786e\u8ba4", c.summary(), true)));
        policy.citations().stream().filter(c -> c.section().contains("\u6b65\u9aa4") || c.section().contains("\u529e\u7406\u8bf4\u660e") || c.section().contains("\u540e\u7eed"))
                .findFirst().ifPresent(c -> items.add(new ChecklistItem("\u529e\u7406\u6d41\u7a0b", "\u5f85\u6309\u6b65\u9aa4\u529e\u7406", c.summary(), true)));
        items.add(new ChecklistItem("\u63d0\u4ea4\u524d\u786e\u8ba4", "\u5f85\u4eba\u5de5\u786e\u8ba4", "\u8bf7\u6838\u5bf9\u7533\u8bf7\u9875\u9762\u7684\u5fc5\u586b\u9879\u4e0e\u6750\u6599\u6e05\u5355\u3002\u52a9\u624b\u4e0d\u4f1a\u81ea\u52a8\u63d0\u4ea4\u3001\u5ba1\u6279\u6216\u4fee\u6539\u4e1a\u52a1\u6570\u636e\u3002", true));
        String next = "\u8bf7\u6838\u5bf9\u4ee5\u4e0a\u4efb\u52a1\u9879\uff0c\u518d\u8fdb\u5165\u5f53\u524d\u8d26\u53f7\u5df2\u6388\u6743\u7684\u4e1a\u52a1\u529f\u80fd\u5b8c\u6210\u7533\u8bf7\u3002";
        return new WorkflowResponse(intent.label, List.of("classify_intent", "search_policy", "get_process_guide", "check_evidence", "generate_checklist"),
                policy, List.copyOf(items), evidenceStatus, next, intent.path);
    }

    private enum Intent {
        MIGRATION_IN("\u6237\u7c4d\u8fc1\u5165", "/migrations/in/apply"), MIGRATION_OUT("\u6237\u7c4d\u8fc1\u51fa", "/migrations/out/apply"),
        RESIDENCE_PERMIT("\u5c45\u4f4f\u8bc1\u529e\u7406", "/residence-permits"), HOUSEHOLD("\u5bb6\u5ead\u6237\u529e\u7406", "/households"),
        CANCELLATION("\u6ce8\u9500\u4e1a\u52a1", "/cancellations"), APPROVAL("\u7533\u8bf7\u5ba1\u6279", "/approvals"), OTHER("\u4e1a\u52a1\u529e\u7406\u54a8\u8be2", "/applications");
        final String label, path; Intent(String label, String path) { this.label = label; this.path = path; }
        static Intent from(String q) { String value = q == null ? "" : q; if (value.contains("\u8fc1\u5165")) return MIGRATION_IN; if (value.contains("\u8fc1\u51fa")) return MIGRATION_OUT; if (value.contains("\u5c45\u4f4f\u8bc1")) return RESIDENCE_PERMIT; if (value.contains("\u5bb6\u5ead\u6237") || value.contains("\u65b0\u589e\u6210\u5458")) return HOUSEHOLD; if (value.contains("\u6ce8\u9500")) return CANCELLATION; if (value.contains("\u5ba1\u6279") || value.contains("\u9a73\u56de") || value.contains("\u9000\u56de")) return APPROVAL; return OTHER; }
    }
    public record ChecklistItem(String name, String status, String guidance, boolean needsConfirmation) { }
    public record WorkflowResponse(String businessType, List<String> usedTools, PolicyAssistantService.QueryResponse policy,
                                   List<ChecklistItem> checklist, String evidenceStatus, String nextStep, String actionPath) { }
}
