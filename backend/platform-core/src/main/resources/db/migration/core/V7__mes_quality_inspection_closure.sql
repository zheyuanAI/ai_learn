-- Failed 质检必须有可审计的正式处置关闭状态；关闭只记录质量决定，不执行库存移动。
ALTER TABLE mes_quality_inspection
    DROP CONSTRAINT IF EXISTS mes_quality_inspection_status_check;

ALTER TABLE mes_quality_inspection
    ADD CONSTRAINT mes_quality_inspection_status_check
    CHECK (status IN ('Draft', 'Submitted', 'Passed', 'Failed', 'Closed'));
