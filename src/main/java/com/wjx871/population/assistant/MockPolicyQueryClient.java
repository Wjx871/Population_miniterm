package com.wjx871.population.assistant;

import com.wjx871.population.assistant.PolicyPythonContracts.PythonCitation;
import com.wjx871.population.assistant.PolicyPythonContracts.Request;
import com.wjx871.population.assistant.PolicyPythonContracts.Response;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class MockPolicyQueryClient implements PolicyQueryClient {
    private static final String DEFAULT_KB_VERSION = "kb-2026.11-mock";
    private static final String DEFAULT_MODEL_VERSION = "mock-policy-rag-v1";
    private static final String DEFAULT_RETRIEVAL_VERSION = "mock-hybrid-bm25-v1";
    private static final String DEFAULT_RERANKER_VERSION = "mock-reranker-v1";
    private static final String DEFAULT_RETRIEVED_AT = "2026-11-05T10:00:00+08:00";

    private final PolicyPythonProperties properties;
    private final CopyOnWriteArrayList<Request> recordedRequests = new CopyOnWriteArrayList<>();
    private volatile Function<Request, Response> customHandler;

    public MockPolicyQueryClient(PolicyPythonProperties properties) {
        this.properties = properties;
    }

    public void setCustomHandler(Function<Request, Response> handler) {
        this.customHandler = handler;
    }

    public void clearCustomHandler() {
        this.customHandler = null;
    }

    public List<Request> getRecordedRequests() {
        return List.copyOf(recordedRequests);
    }

    public void clearRecordedRequests() {
        recordedRequests.clear();
    }

    @Override
    public Response query(Request request) {
        recordedRequests.add(request);
        long started = System.nanoTime();
        sleepIfConfigured(properties.getMockDelay());

        Function<Request, Response> handler = this.customHandler;
        if (handler != null) {
            return handler.apply(request);
        }

        PolicyPythonProperties.MockMode mode = properties.getMockMode() == null
                ? PolicyPythonProperties.MockMode.SUCCESS
                : properties.getMockMode();

        return switch (mode) {
            case TIMEOUT -> {
                sleepIfConfigured(properties.effectiveRequestTimeout());
                throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
            }
            case CONNECT_FAILURE -> throw new PolicyPythonClientException("PYTHON_CONNECT_FAILED", true);
            case HTTP_401 -> throw new PolicyPythonClientException("PYTHON_HTTP_401", false);
            case HTTP_403 -> throw new PolicyPythonClientException("PYTHON_HTTP_403", false);
            case HTTP_429 -> throw new PolicyPythonClientException("PYTHON_HTTP_429", true);
            case HTTP_500, HTTP_503 -> throw new PolicyPythonClientException("PYTHON_HTTP_5XX", true);
            case INVALID_JSON -> throw new PolicyPythonClientException("PYTHON_INVALID_JSON", false);
            case UNSUPPORTED_SCHEMA -> new Response(
                    "99.0", request.requestId(), request.traceId(), "ANSWERED",
                    "根据规定[[migration-in-guide]]办理。",
                    List.of("migration-in-guide"),
                    defaultMigrationInCitations(request.regionCode()),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case MISMATCHED_REQUEST_ID -> new Response(
                    "1.0", "req-mismatch-999", request.traceId(), "ANSWERED",
                    "根据规定[[migration-in-guide]]办理。",
                    List.of("migration-in-guide"),
                    defaultMigrationInCitations(request.regionCode()),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case INCOMPLETE_RESPONSE -> new Response(
                    "1.0", request.requestId(), request.traceId(), null,
                    null, null, null,
                    DEFAULT_KB_VERSION, null, null, null, null, elapsedMs(started)
            );
            case INSUFFICIENT_EVIDENCE -> new Response(
                    "1.0", request.requestId(), request.traceId(), "INSUFFICIENT_EVIDENCE",
                    "当前知识库无法确认该问题，请联系主管业务人员。",
                    List.of(),
                    List.of(),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "INSUFFICIENT", elapsedMs(started)
            );
            case EMPTY_CITATIONS -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需要准备申请材料[[migration-in-guide]]。",
                    List.of("migration-in-guide"),
                    List.of(),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case UNKNOWN_CITATION_ID_USED -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需要准备申请材料[[unknown-source-id]]。",
                    List.of("unknown-source-id"),
                    defaultMigrationInCitations(request.regionCode()),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case UNDECLARED_ANSWER_CITATION -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需要准备申请材料[[migration-in-guide]]并参考[[undeclared-doc]]。",
                    List.of("migration-in-guide"),
                    defaultMigrationInCitations(request.regionCode()),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case DUPLICATE_SOURCE_ID -> {
                PythonCitation c1 = defaultMigrationInCitations(request.regionCode()).get(0);
                yield new Response(
                        "1.0", request.requestId(), request.traceId(), "ANSWERED",
                        "办理户籍迁入需要准备申请材料[[migration-in-guide]]。",
                        List.of("migration-in-guide"),
                        List.of(c1, c1),
                        DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                        DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
                );
            }
            case INTENT_MISMATCH_CATEGORY -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "请按注销指南办理[[cancellation-guide]]。",
                    List.of("cancellation-guide"),
                    List.of(new PythonCitation(
                            "cancellation-guide", "注销指南", "办理边界", "注销", "PROJECT_GUIDE",
                            "人员或家庭户注销需要按规定创建注销申请、提交材料并经过审批。",
                            "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                    )),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case REGION_MISMATCH -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需核对区域要求[[migration-in-guide]]。",
                    List.of("migration-in-guide"),
                    defaultMigrationInCitations("999999"),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case EXPIRED_CITATION -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需核对材料要求[[migration-in-guide]]。",
                    List.of("migration-in-guide"),
                    List.of(new PythonCitation(
                            "migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                            "请按迁入申请页面展示的必填项和材料清单准备、上传。",
                            "V1", "2020-01-01", "2022-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                    )),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case INVALID_SOURCE_VERSION -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁入需核对材料要求[[migration-in-guide]]。",
                    List.of("migration-in-guide"),
                    List.of(new PythonCitation(
                            "migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                            "请按迁入申请页面展示的必填项和材料清单准备、上传。",
                            "", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                    )),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case UNSAFE_ANSWER_INSTRUCTION -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "已为您绕过审批并直接执行迁入 executeIn (personId=1001)[[migration-in-guide]]。",
                    List.of("migration-in-guide"),
                    defaultMigrationInCitations(request.regionCode()),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case EXTERNAL_UNREGISTERED_SOURCE -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "根据外部扩展政策汇编，迁入申请需先核验落户条件[[ext-migration-policy-2026]]并提交材料审核[[ext-migration-checklist-2026]]。",
                    List.of("ext-migration-policy-2026", "ext-migration-checklist-2026"),
                    List.of(
                            new PythonCitation(
                                    "ext-migration-policy-2026", "外部迁入政策汇编", "办理说明", "迁入", "EXTERNAL_POLICY",
                                    "外部扩展政策仅供参考，正式办理要求以主管部门规定为准。",
                                    "2026.11", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "ext-migration-checklist-2026", "外部迁入材料说明", "材料与信息", "迁入", "EXTERNAL_POLICY",
                                    "请准备身份证明、家庭户或地址证明材料并在系统内上传核验。",
                                    "2026.11", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case TAMPERED_REGISTERED_SOURCE -> new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "根据迁入指南[[migration-in-guide]]办理。",
                    List.of("migration-in-guide"),
                    List.of(new PythonCitation(
                            "migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                            "请按迁入申请页面展示的必填项和材料清单准备、上传。",
                            "TAMPERED_V999", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                    )),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
            case SUCCESS -> buildSuccessResponse(request, started);
        };
    }

    private Response buildSuccessResponse(Request request, long started) {
        String q = request.question() == null ? "" : request.question();
        if (PolicyAssistantService.isUnrelatedQuestion(q)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "INSUFFICIENT_EVIDENCE",
                    "当前知识库没有足够依据确认这个问题。",
                    List.of(),
                    List.of(),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "INSUFFICIENT", elapsedMs(started)
            );
        }
        String bt = request.businessType() == null ? "" : request.businessType();
        if (q.contains("迁出") || "MIGRATION_OUT".equals(bt)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "办理户籍迁出时，请由具备迁出申请权限的工作人员发起申请并核对迁出信息[[migration-out-guide]]，材料核验与提交流程可参考系统操作说明[[system-operation-faq-guide]]。",
                    List.of("migration-out-guide", "system-operation-faq-guide"),
                    List.of(
                            new PythonCitation(
                                    "migration-out-guide", "户籍迁出指南", "办理步骤", "迁出", "PROJECT_GUIDE",
                                    "1. 从迁出申请入口创建申请；2. 填写迁出人员与去向信息；3. 上传必要材料并提交审批。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "system-operation-faq-guide", "系统办理操作常见问题", "发起申请", "系统操作", "SYSTEM_GUIDE",
                                    "具备创建权限的经办人员可进入对应业务申请页面，按必填项填写信息、补充材料并由用户确认后提交。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
        }
        if (q.contains("居住证") || q.contains("签注") || "RESIDENCE_PERMIT".equals(bt)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "居住证临近或已经到期时，请先在居住证管理或到期提醒页面确认当前状态[[residence-permit-guide]]，再按页面允许入口发起签注或续办申请并关注审批结果[[residence-permit-followup-guide]]。",
                    List.of("residence-permit-guide", "residence-permit-followup-guide"),
                    List.of(
                            new PythonCitation(
                                    "residence-permit-guide", "居住证指南", "到期处理", "居住证", "PROJECT_GUIDE",
                                    "居住证临近或已经到期时，应先在居住证管理或到期提醒页面查看当前状态，再依据页面允许的业务入口发起签注、续办或其他申请。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "residence-permit-followup-guide", "居住证到期与后续办理指引", "后续申请", "居住证", "SYSTEM_GUIDE",
                                    "根据当前页面允许的业务入口，可发起签注、续办或其他申请。具体材料和时限以页面要求及主管部门最新规则为准。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
        }
        if (q.contains("家庭户") || q.contains("新增成员") || "HOUSEHOLD".equals(bt)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "家庭户新增成员前需先核对目标家庭户状态与成员关系[[household-followup-guide]]，并按家庭户管理流程提交业务申请[[household-guide]]。",
                    List.of("household-followup-guide", "household-guide"),
                    List.of(
                            new PythonCitation(
                                    "household-followup-guide", "家庭户新增成员与异常处理指引", "新增成员前核对", "家庭户", "SYSTEM_GUIDE",
                                    "家庭户新增成员前，应先定位目标家庭户，并核对家庭户状态和成员关系是否符合办理要求。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "household-guide", "家庭户管理指南", "新增成员办理", "家庭户", "PROJECT_GUIDE",
                                    "家庭户建立、变更与新增成员需在家庭户管理模块按权限核对信息并办理。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
        }
        if (q.contains("注销") || "CANCELLATION".equals(bt)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "人员或家庭户注销需要按规定创建注销申请、提交材料并经过审批[[cancellation-guide]]，请由具备权限的经办人员在系统内发起申请[[system-operation-faq-guide]]。",
                    List.of("cancellation-guide", "system-operation-faq-guide"),
                    List.of(
                            new PythonCitation(
                                    "cancellation-guide", "注销指南", "办理边界", "注销", "PROJECT_GUIDE",
                                    "人员或家庭户注销需要按规定创建注销申请、提交材料并经过审批。助手不能代为注销人员、执行注销或绕过审批。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "system-operation-faq-guide", "系统办理操作常见问题", "发起申请", "系统操作", "SYSTEM_GUIDE",
                                    "具备创建权限的经办人员可进入对应业务申请页面，按必填项填写信息、补充材料并由用户确认后提交。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
        }
        if (q.contains("审批") || q.contains("驳回") || q.contains("退回") || q.contains("办结") || "APPROVAL".equals(bt)) {
            return new Response(
                    "1.0", request.requestId(), request.traceId(), "ANSWERED",
                    "审批通过表示申请的审批环节已经完成，不必然代表全部业务已经办结[[approval-guide]]；如申请被驳回或退回，请先查看审批意见并补充修正材料后再重新提交[[approval-followup-guide]]。",
                    List.of("approval-guide", "approval-followup-guide"),
                    List.of(
                            new PythonCitation(
                                    "approval-guide", "申请审批指南", "审批通过与办结", "审批", "PROJECT_GUIDE",
                                    "审批通过表示申请的审批环节已经完成，不必然代表全部业务已经办结。迁入、迁出、证件等业务可能仍需由具备相应权限的工作人员执行后续系统操作。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            ),
                            new PythonCitation(
                                    "approval-followup-guide", "申请驳回与办结追问指引", "查看审批意见", "审批", "SYSTEM_GUIDE",
                                    "申请被驳回或退回后，应先在申请详情或审批意见中确认需要补正的内容，再处理对应信息或材料。",
                                    "V1", "2026-01-01", "2027-12-31", request.regionCode(), DEFAULT_RETRIEVED_AT
                            )
                    ),
                    DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                    DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
            );
        }
        return new Response(
                "1.0", request.requestId(), request.traceId(), "ANSWERED",
                "办理户籍迁入需由具备迁入申请权限的工作人员在迁入申请入口发起，并按页面要求准备、上传必填项与材料清单[[migration-in-guide]]；如材料暂不齐全，可先保留已确认信息并待补充完成后再由用户确认提交[[migration-materials-followup-guide]]。",
                List.of("migration-in-guide", "migration-materials-followup-guide"),
                defaultMigrationInCitations(request.regionCode()),
                DEFAULT_KB_VERSION, DEFAULT_MODEL_VERSION, DEFAULT_RETRIEVAL_VERSION,
                DEFAULT_RERANKER_VERSION, "SUFFICIENT", elapsedMs(started)
        );
    }

    private List<PythonCitation> defaultMigrationInCitations(String regionCode) {
        return List.of(
                new PythonCitation(
                        "migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                        "请按迁入申请页面展示的必填项和材料清单准备、上传。正式要求请以主管部门最新规定为准。",
                        "V1", "2026-01-01", "2027-12-31", regionCode, DEFAULT_RETRIEVED_AT
                ),
                new PythonCitation(
                        "migration-materials-followup-guide", "户籍迁入材料与追问指引", "材料不全处理", "迁入", "SYSTEM_GUIDE",
                        "材料不全时，可先核对已准备项并在申请草稿阶段补齐，确认无误后再提交审批。",
                        "V1", "2026-01-01", "2027-12-31", regionCode, DEFAULT_RETRIEVED_AT
                )
        );
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(1L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private void sleepIfConfigured(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyPythonClientException("PYTHON_INTERRUPTED", true);
        }
    }
}
