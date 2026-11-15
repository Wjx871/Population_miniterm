-- Measured on MySQL 8.4 / 20,000 synthetic floating rows: avoid a full row scan
-- for DashboardMapper.countActiveFloating / StatisticsMapper.countFloatingPopulation.
-- Apply to the primary; replication distributes the index. No business data changes.
DROP PROCEDURE IF EXISTS phase15_floating_statistics_index;
DELIMITER $$
CREATE PROCEDURE phase15_floating_statistics_index()
BEGIN
  DECLARE index_columns TEXT;
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') INTO index_columns
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='floating_population'
      AND index_name='idx_floating_status_current';
  IF index_columns IS NULL THEN
    ALTER TABLE floating_population ADD INDEX idx_floating_status_current(status,current_flag),
      ALGORITHM=INPLACE, LOCK=NONE;
  ELSEIF index_columns <> 'status,current_flag' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='idx_floating_status_current has an unexpected definition';
  END IF;
END$$
DELIMITER ;
CALL phase15_floating_statistics_index();
DROP PROCEDURE IF EXISTS phase15_floating_statistics_index;
