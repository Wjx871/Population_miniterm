-- ====================================================================
-- Migration: 2026-07-12 P0 Security Fix — Approval request structured columns
-- ====================================================================
-- 问题：旧的 apply_reason 字段被当作 "字符串拼接容器" 装下 businessType /
--       businessId / applicationId / applyReason / payloadJson。
--       用户提交的内容含 "[REASON=" / "[BT=" 可破坏 parseApplyReason，
--       导致 PERSON_UPDATE 草稿被错派给 PERSON_CREATE 落地。
--
-- 修复：将 businessType / businessId / applicationId 拆为独立列，
--       payloadJson 用 JSON 类型（结构化、可索引、避免注入），apply_reason
--       仅保留用户提交的自由文本。
--
-- 兼容：旧 apply_reason 中的 [BT=...][PID=...][APPID=...][REASON=...]xxx 格式
--       仍能通过 application 层回填逻辑迁移过来（见 sys_approval_request 数据修正）。
-- ====================================================================

ALTER TABLE sys_approval_request
    ADD COLUMN business_type VARCHAR(50) NULL COMMENT '业务类型（PERSON_CREATE/...）' AFTER status,
    ADD COLUMN business_id    BIGINT      NULL COMMENT '业务主键ID'                AFTER business_type,
    ADD COLUMN payload_json   JSON        NULL COMMENT '业务载荷 JSON'              AFTER business_id,
    ADD KEY  idx_approval_business (business_type, business_id);

-- 把已有数据从旧的字符串格式回填到新列（幂等执行）
UPDATE sys_approval_request
SET
    business_type = SUBSTRING(
        SUBSTRING(apply_reason, LOCATE('[BT=', apply_reason) + 4),
        1,
        LOCATE(']', SUBSTRING(apply_reason, LOCATE('[BT=', apply_reason) + 4)) - 1
    ),
    business_id = CASE
        WHEN LOCATE('[PID=', apply_reason) > 0
        THEN CAST(
            SUBSTRING(
                SUBSTRING(apply_reason, LOCATE('[PID=', apply_reason) + 5),
                1,
                LOCATE(']', SUBSTRING(apply_reason, LOCATE('[PID=', apply_reason) + 5)) - 1
            ) AS UNSIGNED
        )
        ELSE NULL
    END,
    apply_reason = CASE
        WHEN LOCATE('[REASON=', apply_reason) > 0
        THEN TRIM(SUBSTRING(apply_reason, LOCATE('[REASON=', apply_reason) + 8))
        ELSE apply_reason
    END
WHERE apply_reason LIKE '[BT=%'
  AND business_type IS NULL;