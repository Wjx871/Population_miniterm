# Phase 11 统计口径

时区统一为 Asia/Shanghai。当前户籍人口是存在 `residence.status=ACTIVE` 的正式 `person`；流动人口按 `floating_population.status=ACTIVE AND current_flag=1` 单独计数，可能与户籍人口重叠。家庭户仅统计 ACTIVE。迁入迁出只统计 `business_status=COMPLETED` 的专业记录，日期采用实际业务日期。重点人口仅统计 ACTIVE。证件到期按查询当天至包含截止日动态计算。

每个指标都在 SQL 中应用 `DataScopeCriteria`。统计缓存键包含数据范围、regionCode/departmentId、查询日期及所有筛选参数，避免跨范围污染。
