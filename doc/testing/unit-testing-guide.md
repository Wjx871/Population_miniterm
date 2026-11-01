# JUnit 5、Mockito 与 JaCoCo 单元测试

项目使用 Java 17、Maven 和 Spring Boot 3.5.3。`spring-boot-starter-test` 已提供 JUnit Jupiter、Mockito（包括 `MockitoExtension`）和 AssertJ，不需要重复添加这些依赖或自行指定版本。JaCoCo Maven 插件版本在 `pom.xml` 中统一设置。

## 在 IntelliJ IDEA 中运行

1. 打开根目录 `pom.xml`，在 Maven 工具窗口执行 Reload All Maven Projects（重新加载所有 Maven 项目）。项目 SDK 和 Maven Runner JRE 选择 JDK 17。
2. 打开 `src/test/java/com/wjx871/population/cancellation/CancellationMaterialRequirementServiceTest.java`，点击类名或测试方法左侧的绿色运行按钮。普通 Run 用于检查测试结果，Debug 可以设置断点。
3. 要生成本项目配置的 JaCoCo HTML 报告，在 IDEA Terminal 的项目根目录运行：

   ```powershell
   .\mvnw.cmd -Punit-tests clean test
   ```

   也可以在 Maven 运行配置的命令行填写 `-Punit-tests clean test`，工作目录选择项目根目录。

4. 成功后用浏览器打开 `target/site/jacoco/index.html`。逐级进入包、类和方法，可查看行覆盖率、分支覆盖率以及源码着色。逐项测试结果位于 `target/surefire-reports/`。

直接点击 IDEA 的普通 Run 不会执行 Maven 的 JaCoCo 插件，所以不会生成上述 HTML 报告。IDEA 的 Run with Coverage 是另一个覆盖率运行入口；需要本项目统一报告时使用 Maven 命令。

## 测试范围

`unit-tests` profile 通过 JUnit `@Tag("unit")` 筛选纯单元测试。目前标记了身份证校验、注销材料校验、综合查询服务、查询参数校验、统计服务和令牌撤销这六个测试类。它们不启动 Spring 容器，不需要运行 MySQL、Redis、OCR 服务或前端。

未指定 profile 的 ` .\mvnw.cmd clean test` 保留原有全量测试行为，会包含 Spring 集成测试。不要用测试类名是否以 `IntegrationTest` 结尾来判断是否访问数据库：部分 `MapperTest` 和 `ServiceTest` 同样使用 `@SpringBootTest`。

只运行注销材料测试并生成覆盖率报告：

```powershell
.\mvnw.cmd -Punit-tests "-Dtest=CancellationMaterialRequirementServiceTest" clean test
```

每次使用 `clean` 可避免旧的覆盖率执行数据混入本次报告。报告统计整个后端生产代码；仅运行单元测试时，未执行的控制器、持久层等代码仍计入分母。此时全项目覆盖率较低不代表测试失败。当前未设置覆盖率最低门槛，新增测试后应重点检查对应业务类的行和分支覆盖情况。

## 如何继续编写测试

参考 `CancellationMaterialRequirementServiceTest`：

- `@Tag("unit")`：将测试纳入纯单元测试 profile。
- `@ExtendWith(MockitoExtension.class)`：启用 Mockito，并检查无用的 mock 配置。
- `@Mock`：替代 Mapper 等外部依赖，不连接真实数据库。
- `@InjectMocks`：创建被测试的 Service，并注入 mock 依赖。
- `when(...).thenReturn(...)`：安排依赖的返回数据。
- `@Test` / `@ParameterizedTest`：使用 JUnit 执行单个场景或多组输入。
- AssertJ 断言：检查返回值、异常类型和业务提示；`verify(...)` 检查必要的依赖调用。

每个测试按照“准备输入与依赖 → 调用业务方法 → 验证结果”编写，覆盖成功、缺失数据、非法输入及业务分支。新写的纯单元测试应添加 `@Tag("unit")`；需要 Spring 容器或数据库的测试不要添加该标签。

JaCoCo 使用 `prepare-agent` 为 Maven 测试 JVM 收集执行数据，并在 `test` 阶段结束时用 `report` 生成报告。配置依据：[JaCoCo Maven 插件官方文档](https://www.jacoco.org/jacoco/trunk/doc/maven.html)。
