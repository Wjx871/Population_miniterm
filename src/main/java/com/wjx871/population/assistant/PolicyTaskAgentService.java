package com.wjx871.population.assistant;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bounded, read-only task agent for policy guidance.  It intentionally has no
 * repository, mapper, SQL, mutation, or arbitrary URL capability: every step
 * below is a server-owned allowlisted tool.
 */
@Service
public class PolicyTaskAgentService {
    private static final int MAX_HISTORY_TURNS = 6;
    private final PolicyAssistantService policyService;

    public PolicyTaskAgentService(PolicyAssistantService policyService) {
        this.policyService = policyService;
    }

    public AgentResponse execute(String question, List<ConversationTurn> history) {
        String raw = question == null ? "" : question.trim();
        Intent intent = classifyIntent(raw, history);
        String standaloneQuestion = rewriteQuestion(raw, history, intent);
        List<String> queries = expandQueries(standaloneQuestion, intent);
        List<PolicyAssistantService.QueryResponse> responses = queries.stream()
                .map(policyService::query)
                .toList();
        PolicyAssistantService.QueryResponse primary = responses.get(0);
        List<PolicyAssistantService.Citation> citations = fuseCitations(responses, intent);
        Evidence evidence = checkEvidence(primary, citations);
        List<String> conditions = sectionValues(citations, "\u529e\u7406\u8bf4\u660e", "\u4f7f\u7528\u8303\u56f4", "\u529e\u7406\u8fb9\u754c");
        List<String> materials = sectionValues(citations, "\u6750\u6599", "\u4fe1\u606f");
        List<String> steps = sectionValues(citations, "\u6b65\u9aa4", "\u540e\u7eed\u5904\u7406", "\u9a73\u56de", "\u5230\u671f");
        List<String> warnings = sectionValues(citations, "\u6ce8\u610f\u4e8b\u9879", "\u529e\u7406\u8fb9\u754c", "\u65e0\u5173\u95ee\u9898");
        if (asksStatus(raw)) {
            steps = merge(steps, List.of(explainBusinessStatus(raw)));
        }
        List<AllowedAction> nextActions = getAllowedActions(intent, CurrentUserContext.requireUser());
        String answerSummary = buildSummary(intent, evidence.status, conditions, materials, steps, warnings);
        List<String> trace = new ArrayList<>(List.of(
                "\u5df2\u8bc6\u522b\u529e\u7406\u4e8b\u9879\uff1a" + intent.label,
                "\u5df2\u6539\u5199\u4e3a\u72ec\u7acb\u95ee\u9898",
                "\u5df2\u68c0\u7d22\u76f8\u5173\u4e1a\u52a1\u6307\u5357\uff1a" + citations.size() + " \u6761",
                "\u5df2\u6838\u9a8c\u56de\u7b54\u4f9d\u636e\uff1a" + evidence.status
        ));
        if (asksStatus(raw)) trace.add("\u5df2\u89e3\u91ca\u4e1a\u52a1\u72b6\u6001\u6d41\u8f6c");
        trace.add("\u5df2\u8fc7\u6ee4\u6743\u9650\u5185\u4e0b\u4e00\u6b65\u64cd\u4f5c");
        return new AgentResponse(intent.code, intent.label, standaloneQuestion, answerSummary,
                conditions, materials, steps, warnings, citations, nextActions, evidence.status,
                !evidence.status.equals("SUFFICIENT"), List.copyOf(trace), primary.mode(),
                primary.confidence(), primary.traceId());
    }

    private Intent classifyIntent(String question, List<ConversationTurn> history) {
        Intent direct = classify(normalize(question));
        if (direct != Intent.APPLICATION) return direct;
        return classify(normalize(recentHistory(history)));
    }

    private Intent classify(String text) {
        if (text.contains("\u8fc1\u5165")) return Intent.MIGRATION_IN;
        if (text.contains("\u8fc1\u51fa")) return Intent.MIGRATION_OUT;
        if (text.contains("\u5c45\u4f4f\u8bc1") || text.contains("\u7b7e\u6ce8")) return Intent.RESIDENCE_PERMIT;
        if (text.contains("\u5bb6\u5ead\u6237") || text.contains("\u65b0\u589e\u6210\u5458")) return Intent.HOUSEHOLD;
        if (text.contains("\u6ce8\u9500")) return Intent.CANCELLATION;
        if (text.contains("\u5ba1\u6279") || text.contains("\u9a73\u56de") || text.contains("\u9000\u56de")) return Intent.APPROVAL;
        if (text.contains("\u4eba\u53e3") || text.contains("\u6237\u7c4d")) return Intent.POPULATION;
        return Intent.APPLICATION;
    }

    private String rewriteQuestion(String raw, List<ConversationTurn> history, Intent intent) {
        String context = recentHistory(history);
        boolean followUp = raw.startsWith("\u90a3") || raw.startsWith("\u5982\u679c") || raw.contains("\u6750\u6599\u4e0d\u5168")
                || raw.contains("\u54ea\u91cc\u63d0\u4ea4") || raw.contains("\u600e\u4e48\u529e");
        if (!followUp || context.isBlank()) return raw;
        return "\u529e\u7406" + intent.label + "\u65f6\uff0c" + raw;
    }

    private List<String> expandQueries(String standalone, Intent intent) {
        List<String> queries = new ArrayList<>();
        queries.add(standalone);
        if (standalone.contains("\u548c") || standalone.contains("\u3001") || standalone.contains("\u540c\u65f6")) {
            queries.add("\u529e\u7406" + intent.label + "\u9700\u8981\u7684\u6761\u4ef6\u548c\u6750\u6599");
            queries.add("\u529e\u7406" + intent.label + "\u7684\u6b65\u9aa4\u548c\u6ce8\u610f\u4e8b\u9879");
        }
        return queries.stream().filter(q -> !q.isBlank()).distinct().limit(3).toList();
    }

    private List<PolicyAssistantService.Citation> fuseCitations(List<PolicyAssistantService.QueryResponse> responses, Intent intent) {
        Map<String, WeightedCitation> selected = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < responses.size(); queryIndex++) {
            double queryWeight = queryIndex == 0 ? 1.0 : 0.8;
            for (PolicyAssistantService.Citation citation : responses.get(queryIndex).citations()) {
                if (!matchesIntent(citation, intent)) continue;
                String key = citation.logicalPath() + "#" + citation.section();
                double score = queryWeight * (5 - citation.index());
                WeightedCitation old = selected.get(key);
                selected.put(key, old == null || score > old.score ? new WeightedCitation(citation, score) : old);
            }
        }
        if (selected.isEmpty()) {
            responses.stream().flatMap(response -> response.citations().stream()).limit(4)
                    .forEach(citation -> selected.put(citation.logicalPath() + "#" + citation.section(), new WeightedCitation(citation, 0)));
        }
        return selected.values().stream().sorted(Comparator.comparingDouble(WeightedCitation::score).reversed()).limit(4)
                .map(WeightedCitation::citation).map(c -> new PolicyAssistantService.Citation(0, c.title(), c.section(), c.category(), c.sourceType(), c.version(), c.logicalPath(), c.summary()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    for (int i = 0; i < list.size(); i++) { PolicyAssistantService.Citation c = list.get(i); list.set(i, new PolicyAssistantService.Citation(i + 1, c.title(), c.section(), c.category(), c.sourceType(), c.version(), c.logicalPath(), c.summary())); }
                    return List.copyOf(list);
                }));
    }

    private boolean matchesIntent(PolicyAssistantService.Citation citation, Intent intent) {
        String category = citation.category();
        return switch (intent) {
            case MIGRATION_IN -> category.contains("\u8fc1\u5165") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case MIGRATION_OUT -> category.contains("\u8fc1\u51fa") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case RESIDENCE_PERMIT -> category.contains("\u5c45\u4f4f\u8bc1") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case HOUSEHOLD -> category.contains("\u5bb6\u5ead\u6237") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case CANCELLATION -> category.contains("\u6ce8\u9500") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case APPROVAL -> category.contains("\u5ba1\u6279") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            case POPULATION -> category.contains("\u4eba\u53e3") || category.contains("\u5e38\u89c1\u95ee\u9898") || category.contains("\u7cfb\u7edf\u64cd\u4f5c");
            default -> true;
        };
    }

    private String buildSummary(Intent intent, String evidenceStatus, List<String> conditions, List<String> materials, List<String> steps, List<String> warnings) {
        if ("INSUFFICIENT".equals(evidenceStatus)) return "\u5f53\u524d\u77e5\u8bc6\u5e93\u6ca1\u6709\u8db3\u591f\u4f9d\u636e\u786e\u8ba4\u8fd9\u4e2a\u95ee\u9898\uff0c\u8bf7\u8054\u7cfb\u4e1a\u52a1\u4eba\u5458\u6216\u67e5\u9605\u6700\u65b0\u89c4\u5b9a\u3002";
        String detail = java.util.stream.Stream.of(materials, conditions, steps, warnings).flatMap(Collection::stream).findFirst().orElse("\u8bf7\u53c2\u8003\u4e0b\u65b9\u653f\u7b56\u4f9d\u636e\u3002");
        int sentenceEnd = detail.indexOf('\u3002');
        if (sentenceEnd >= 0) detail = detail.substring(0, sentenceEnd + 1);
        if (detail.length() > 120) detail = detail.substring(0, 120) + "\u2026";
        String prefix = "\u5df2\u8bc6\u522b\u4e3a\u201c" + intent.label + "\u201d\u4efb\u52a1\u3002";
        return "PARTIAL".equals(evidenceStatus) ? prefix + detail + "\u5176\u4f59\u5185\u5bb9\u8bf7\u4ee5\u4e3b\u7ba1\u90e8\u95e8\u6700\u65b0\u8981\u6c42\u4e3a\u51c6\u3002" : prefix + detail;
    }

    private Evidence checkEvidence(PolicyAssistantService.QueryResponse primary, List<PolicyAssistantService.Citation> citations) {
        if (citations.isEmpty() || primary.confidence() < .15) return new Evidence("INSUFFICIENT");
        Set<String> sections = citations.stream().map(PolicyAssistantService.Citation::section).collect(Collectors.toSet());
        return new Evidence(primary.confidence() >= .28 && sections.size() >= 2 ? "SUFFICIENT" : "PARTIAL");
    }

    private List<String> sectionValues(List<PolicyAssistantService.Citation> citations, String... sectionNames) {
        return citations.stream().filter(c -> Arrays.stream(sectionNames).anyMatch(s -> c.section().contains(s)))
                .map(c -> c.summary()).distinct().limit(3).toList();
    }

    private List<AllowedAction> getAllowedActions(Intent intent, AuthenticatedUser user) {
        return actionsFor(intent).stream().filter(action -> user.permissions().contains(action.permission))
                .map(action -> new AllowedAction(action.label, action.path)).toList();
    }

    private List<ActionDefinition> actionsFor(Intent intent) {
        return switch (intent) {
            case MIGRATION_IN -> List.of(new ActionDefinition("\u53d1\u8d77\u8fc1\u5165\u7533\u8bf7", "/migrations/in/apply", "migration:in:create"), new ActionDefinition("\u67e5\u770b\u8fc1\u5165\u4e1a\u52a1", "/migrations/in", "migration:view"));
            case MIGRATION_OUT -> List.of(new ActionDefinition("\u53d1\u8d77\u8fc1\u51fa\u7533\u8bf7", "/migrations/out/apply", "migration:out:create"), new ActionDefinition("\u67e5\u770b\u8fc1\u51fa\u4e1a\u52a1", "/migrations/out", "migration:view"));
            case RESIDENCE_PERMIT -> List.of(new ActionDefinition("\u67e5\u770b\u5c45\u4f4f\u8bc1", "/residence-permits", "residence-permit:view"), new ActionDefinition("\u67e5\u770b\u5230\u671f\u63d0\u9192", "/residence-permits/expiring", "residence-permit:expiry:view"));
            case HOUSEHOLD -> List.of(new ActionDefinition("\u8fdb\u5165\u5bb6\u5ead\u6237\u7ba1\u7406", "/households", "household:view"));
            case CANCELLATION -> List.of(new ActionDefinition("\u8fdb\u5165\u6ce8\u9500\u7ba1\u7406", "/cancellations", "cancellation:view"));
            case APPROVAL -> List.of(new ActionDefinition("\u67e5\u770b\u6211\u7684\u7533\u8bf7", "/applications", "application:view"), new ActionDefinition("\u8fdb\u5165\u5f85\u5ba1\u6279\u5217\u8868", "/approvals", "approval:view"));
            case POPULATION -> List.of(new ActionDefinition("\u4eba\u53e3\u7efc\u5408\u67e5\u8be2", "/queries/comprehensive", "population:view"));
            default -> List.of(new ActionDefinition("\u67e5\u770b\u6211\u7684\u7533\u8bf7", "/applications", "application:view"));
        };
    }

    private boolean asksStatus(String question) { return question.contains("\u5ba1\u6279") || question.contains("\u9a73\u56de") || question.contains("\u529e\u7ed3") || question.contains("\u72b6\u6001"); }
    private String explainBusinessStatus(String question) {
        if (question.contains("\u9a73\u56de") || question.contains("\u9000\u56de")) return "\u7533\u8bf7\u88ab\u9a73\u56de\u540e\uff1a\u5148\u67e5\u770b\u5ba1\u6279\u610f\u89c1\uff0c\u8865\u5145\u6216\u4fee\u6b63\u6750\u6599\u540e\u518d\u6309\u6d41\u7a0b\u63d0\u4ea4\u3002";
        return "\u4e1a\u52a1\u72b6\u6001\u4e3a\uff1a\u7533\u8bf7\u5df2\u63d0\u4ea4 \u2192 \u7b49\u5f85\u5ba1\u6279 \u2192 \u5ba1\u6279\u901a\u8fc7 \u2192 \u7b49\u5f85\u4e13\u4e1a\u4e1a\u52a1\u6267\u884c \u2192 \u4e1a\u52a1\u529e\u7ed3\u3002\u5ba1\u6279\u901a\u8fc7\u4e0d\u7b49\u4e8e\u4e1a\u52a1\u5df2\u529e\u7ed3\u3002";
    }
    private static List<String> merge(List<String> left, List<String> right) { return java.util.stream.Stream.concat(left.stream(), right.stream()).distinct().toList(); }
    private static String recentHistory(List<ConversationTurn> history) { if (history == null) return ""; return history.stream().filter(Objects::nonNull).filter(t -> "user".equals(t.role)).map(ConversationTurn::content).filter(Objects::nonNull).skip(Math.max(0, history.size() - MAX_HISTORY_TURNS)).collect(Collectors.joining(" ")); }
    private static String normalize(String value) { return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }

    private enum Intent {
        MIGRATION_IN("MIGRATION_IN", "\u6237\u7c4d\u8fc1\u5165"), MIGRATION_OUT("MIGRATION_OUT", "\u6237\u7c4d\u8fc1\u51fa"),
        RESIDENCE_PERMIT("RESIDENCE_PERMIT", "\u5c45\u4f4f\u8bc1\u529e\u7406"), HOUSEHOLD("HOUSEHOLD", "\u5bb6\u5ead\u6237\u529e\u7406"),
        CANCELLATION("CANCELLATION", "\u6ce8\u9500\u4e1a\u52a1"), APPROVAL("APPROVAL", "\u7533\u8bf7\u5ba1\u6279"),
        POPULATION("POPULATION", "\u4eba\u53e3\u6237\u7c4d"), APPLICATION("APPLICATION", "\u4e1a\u52a1\u529e\u7406\u54a8\u8be2");
        final String code; final String label;
        Intent(String code, String label) { this.code = code; this.label = label; }
    }
    private record WeightedCitation(PolicyAssistantService.Citation citation, double score) { }
    private record Evidence(String status) { }
    private record ActionDefinition(String label, String path, String permission) { }
    public record ConversationTurn(String role, String content) { }
    public record AllowedAction(String label, String path) { }
    public record AgentResponse(String intent, String intentLabel, String standaloneQuestion, String answerSummary,
                                List<String> conditions, List<String> requiredMaterials, List<String> steps, List<String> warnings,
                                List<PolicyAssistantService.Citation> citations, List<AllowedAction> nextActions,
                                String evidenceStatus, boolean needHumanHelp, List<String> executionTrace, String mode,
                                double confidence, String traceId) { public String answer() { return answerSummary; } }
}
