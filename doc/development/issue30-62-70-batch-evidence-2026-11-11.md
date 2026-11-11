# #30 / #62 / #70 本轮运行证据索引

本索引记录保留在施工 worktree 的 `target/issue30-62-70-evidence/` 中的原始运行文件。它们未提交；下列哈希用于独立核对，且不包含测试凭据或 JWT。为避免覆盖既有构建产物，各轮使用与原 `pom.xml` 仅构建目录不同的独立本地临时 POM，保留在 worktree 根目录。

- #30 旧版表征 `issue30-baseline-4.log`：5 项通过，实际退回无异常，退回后专业状态仍 APPROVED。
- #30 显示缺失 RED `issue30-display-red.log`：人员和家庭户两个详情均缺少能反映 RETURNED 的专业响应字段，2 项预期失败；这是显示契约缺失的证据，不是 `valueOf` 异常 RED。此前实际列表还显示 APPROVED。
- `issue30-62-baseline-1.log`：#62 的分页排序和隔离事务夹具不合理，失败不是漏洞 RED；`related-run3.log` 的草稿初始审批日志预期错误也不是漏洞 RED。
- `issue62-baseline-2.log`：旧生产基线，#62 2 项通过；`final-focused.log`：最终 #30/#62 7 项通过。
- `related-green.log`：88 项，0 失败/错误/跳过；`final-full-maven.log`：979 项，0 失败、0 错误、4 跳过、BUILD SUCCESS。4 项为现有 `Issue34FirstIssueRedTest` 3 项和 `Issue37ExpiryBatchIntegrationTest` 1 项。
- `issue70-baseline.log`：11 项通过；`frontend-final-3.log`：217 项通过；`frontend-final-build.log`：构建通过，有大分包警告。首次前端全量 `frontend-full.log` 因未安装依赖有 4 个文件加载失败，安装后通过。
- `mysql-synthetic.sql` / `mysql-query.log`：私有 MySQL 8.4.8、非 3306、合成最小表的新增 SQL 投影和已有专业状态筛选验证；不是 MyBatis 全链路或真实业务 MySQL 验证。私有实例已关闭，数据保留。

## 文件哈希

- `final-focused.log`: SHA-256 `ba0ae937cdc21e1bfa306ac02696f9d13440fbedc7edac6f2b52efddcc8f9c29`
- `final-full-maven.log`: SHA-256 `aac9fc8d399d636c4e79c1eb9f278b85d64d5c9284a372a5bc535318b958250d`
- `frontend-build.log`: SHA-256 `5e4569732f3f564d5238cf2bd05ebea34c504ad4e5a46d506ee749d09abbc81c`
- `frontend-final-2.log`: SHA-256 `f4edfd1090daab7af7920f30cef11d6a9bdf9ddbb37c335997f2a2d74b7f0797`
- `frontend-final-3.log`: SHA-256 `36fdc9239a4b87ad53bcb9f84e2ce64fc67c4a8193758904cd78ed40eabdb923`
- `frontend-final-build.log`: SHA-256 `f6fe94807f1959d7013bdaca78142cecae6374e8b338f1090d1066772213ce12`
- `frontend-final.log`: SHA-256 `f94951d0d032767bb3cef13b26f4d0897dd02829bdb8f1eefcb7a32a8f6115a7`
- `frontend-full.log`: SHA-256 `16e266a38ad8bf6a219b5f3b6efbedb16420dc55374dab45d53aa919868d2580`
- `frontend-green.log`: SHA-256 `36016f620e74f7d0d6c4b1ecc7de60e519679c0907e2c036e385665828eba41d`
- `frontend-install.log`: SHA-256 `427ce97dd11096e3ee558937992155b57fd0552891eb66c78d65a229a8b1e6af`
- `full-maven.log`: SHA-256 `b5d1102284262e40c0d998c8d1894a1c43a1d24372b797185b63fad80bfa4bfb`
- `issue30-62-baseline-1.log`: SHA-256 `cb97748919ae512f08ec220216b4c7f3d8e2092868737a3d4999e04faa144b11`
- `issue30-baseline-4.log`: SHA-256 `d92528d8c3e6f8cc948cb08a71c87bbec472ffa30d3c241084ab319c209c81b8`
- `issue30-display-red.log`: SHA-256 `83794fdb8ef1ecc1a32b33138e234bd69a5c82f2b850a918a268ffb81739b465`
- `issue62-baseline-2.log`: SHA-256 `d6c9da21db584957a2dcd5cbe9ad66fd8a1cf6667958576716d446e5c9c8ef11`
- `issue70-baseline.log`: SHA-256 `5e29288a04f12fee2e834e90be4f87c33d12426ae12f5961cc881e78771ea9b9`
- `mysql-ascii-init.log`: SHA-256 `6caefbb7cc47d85fac2f04738e1e139b091e34c45721e39c88005f5404e2b2b7`
- `mysql-init.log`: SHA-256 `2180b97d24cd747d9735ed9c379a427302579374233ec48392f9a9aacb3e2f8b`
- `mysql-private.ini`: SHA-256 `ae1f242a164fc82be9cb1ad3c51b1740049156d3b8b58b31e7804511397605d7`
- `mysql-query.log`: SHA-256 `1ad566541caf46ff959974b9941005a387f4df1998b7829db4350b2f4dcd89c1`
- `mysql-synthetic.sql`: SHA-256 `d8ee2fae00a9bb27fe5494b35c72b8193c2072c65594214f01db68a618854d67`
- `related-green.log`: SHA-256 `ace048a6a33fb3941030ceb598b02309c61c97c876605f150182e15c81b4ffaf`
- `related-run3.log`: SHA-256 `501805f765ee658e4c3b5fdb04e96d2a438141cdfc2a13d01b61ef87c63cb2d5`
