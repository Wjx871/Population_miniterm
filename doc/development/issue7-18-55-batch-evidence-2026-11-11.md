# 审计 #7 / #18 / #55 脱敏证据索引（2026-11-11）

本表只包含本机日志文件名、哈希和 JWT 模式扫描结果，不包含原始请求或令牌。原始文件保留在本机 `C:/Users/jxgm/.codex/worktrees/issue7-18-55-batch/`，不纳入 Git。扫描仅检查常见 JWT 三段格式，不能替代人工隐私审核。

| 本机原始日志 | SHA256 | 扫描发现 JWT 格式 |
|---|---|---|
| frontend-build-attempt1.log | `8EB963DBF8A3D5AB8015E4B4D282DC1C8A1892955A4F080300A367B9BEDDC297` | 未发现 |
| frontend-unit-attempt1.log | `CC1892F2960B7FC415178472B0320E2D6F64DCAA3C4F1534C96E4969C4072CCE` | 未发现 |
| frontend-unit-attempt2.log | `6E8A7D409580C53BCB23E121C4EAF020969B74F008F15FBE3E5B3609ACD764CA` | 未发现 |
| issue18-characterization-guarded.log | `E8B65CB0D12BDE25CA79C9C824C7D89A08E85DBF7BF31203E8BD6DFACBAC3543` | 未发现 |
| issue18-red-attempt1.log | `A317EF8E7B989587FB61DC1C6F3C841E5A14E2A25481DA931E79DAA099725011` | 未发现 |
| issue19-regression-attempt1.log | `B438A5B4C512FAFC4E4190DD0CE15B86D0E72430B1BFBB4610F83128691B678D` | 未发现 |
| issue34-single-green.log | `A981D79D52634CC3FD81F345029DC4C081CBB50F323620477907AEDB97F2945D` | 未发现 |
| issue55-cancellation-regression-green.log | `D333DDDD22892748641F503EF894BC3122FE9A8E917E938CAF31BE94C7371E11` | 未发现 |
| issue55-red.log | `CF629DCADED872D296B255AF5D2D6A288E9B066A4FC3283FE1DD2C9D9AD41CFF` | 是：仅本地保留 |
| issue7-55-green-attempt1.log | `8031A3E9C1C48750BC66BD26A07B1478105B043D0D24581E5CB7E087165807F7` | 是：仅本地保留 |
| issue7-55-green-attempt2.log | `DCAB7752065B68C51E22CA0139CE5D578C10639BB239B8BED457E20639DB2CD6` | 未发现 |
| issue7-ocr-regression-green.log | `A4650762D104E2CFC7BA8C73B20769B0FC23D1A7E2372A9E4C2555176D292F79` | 未发现 |
| issue7-red.log | `18CC6A14CB3E817D24DFE4B4A19C0418604D0D91D2FEFAF22AAF781993805C55` | 未发现 |
| issue7-short-mask-red.log | `D09ADEADC78C16C0B0EF2B957CADEB1D0FABED9F5BFD6903D5D7519BB61851F2` | 未发现 |
| maven-full-guarded-attempt1.log | `F4E90676861C3F3D1C2BFEA7532524EF41BE43A6A30F819459505C311DAF4BA9` | 未发现 |
| maven-full-guarded-attempt2.log | `4307E21587881EAFC18E70635C50C8511618404B85BB5EF8916F24C23E63BE5F` | 未发现 |
| maven-full-guarded-final.log | `788596286F881813DCCD9904B5D303059F91D390C3D0810A5A2F5BF00774C362` | 未发现 |
| targeted-guarded-attempt1.log | `61FEF635EC29185A2110EF09A639B174CAEBD3D2C9A89B45D5DD617A1EEAF914` | 未发现 |
| targeted-guarded-attempt2.log | `0D15282B4C63AF45F1B2665BF4CA96E0A5635C655852B2C107E9BA3E19FEC762` | 未发现 |
| targeted-guarded-attempt3.log | `F3AAA33422221CD06F110740639A112FD102AAF06D778213DE7BBFEC4369133C` | 未发现 |
| targeted-guarded-attempt4.log | `FCC490C850A4723C8D06AF4C8F5573187677F44555097B47B220395323B662DE` | 未发现 |
| targeted-guarded-attempt5.log | `96E29A102584140767AA9941BB8E925FCD7C2A6CD841487BD8205D9551959198` | 未发现 |

## 与结论的对应关系

- `issue7-red.log`：对象类型文本字段被错误接受；`issue7-ocr-regression-green.log`：66 项 OCR 及事务回归通过。
- `issue7-short-mask-red.log`：8 字符掩码边界 RED；该风险未修复，#7 保持 OPEN。复现探针源码保留在本机 `short-mask-probe-01/ShortMaskProbe.java`。
- `issue18-red-attempt1.log` 和 `issue18-characterization-guarded.log`：历史单执行冲突、退回与常规 PUT 缺口；#18 保持 FIXED_NOT_VERIFIED。
- `issue55-red.log`：无版本的旧请求成功；`issue34-single-green.log` 和 `issue55-cancellation-regression-green.log`：单类 35 项及取消回归 65 项通过。该 RED 日志包含测试 JWT，只在本地保留。
- `maven-full-guarded-attempt2.log`：旧 Issue34 调用未带版本导致唯一失败；`maven-full-guarded-final.log`：1006 项、0 失败、0 错误、4 跳过。
- `frontend-unit-attempt2.log` 与 `frontend-build-attempt1.log`：217 项前端单测及构建通过。
- 其他原始日志记录启动、防删除保护器兼容性和早期验证尝试；均保留，未将其误报为最终通过结果。

本轮详细实现与未验证范围见[施工复核](issue7-18-55-batch-review-2026-11-11.md)。
