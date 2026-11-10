package com.wjx871.population.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class ExportFileServiceFormulaInjectionTest {
    @TempDir
    Path exportDirectory;

    @Test
    void allPrefixExpressionCombinationsRemainStringInOriginalOoxml() throws Exception {
        ExportProperties properties = new ExportProperties();
        properties.setDir(exportDirectory.toString());
        var cases = ExportFormulaPayloads.cases();
        List<Map<String, Object>> rows = cases.stream()
                .map(p -> Map.<String, Object>of("name", p.input())).toList();
        var generated = new ExportFileService(properties).generate("PERSON", List.of("name"), rows, "matrix.xlsx");
        var inspection = ExportXlsxInspection.inspect(generated.path());
        assertThat(inspection.sha256()).isEqualTo(generated.sha256());
        assertThat(inspection.poiFormulaCells()).isZero();
        assertThat(inspection.xmlFormulaElements()).isZero();
        assertThat(inspection.externalLinkParts()).isZero();
        assertThat(inspection.externalRelationships()).isZero();
        for (int i = 0; i < cases.size(); i++) {
            var cell = inspection.cells().get("A" + (i + 2));
            assertThat(cell.type()).isEqualTo("STRING");
            assertThat(cell.text()).isEqualTo(cases.get(i).expected());
            assertThat(cell.xml()).contains("t=\"s\"").doesNotContain("<f");
        }
    }

    @Test
    void inspectionDetectsHarmlessRealFormulaPositiveControl() throws Exception {
        Path file = exportDirectory.resolve("positive-control.xlsx");
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            workbook.createSheet("CONTROL").createRow(0).createCell(0).setCellFormula("1+1");
            try (var output = Files.newOutputStream(file)) { workbook.write(output); }
        }
        var inspection = ExportXlsxInspection.inspect(file);
        assertThat(inspection.poiFormulaCells()).isOne();
        assertThat(inspection.xmlFormulaElements()).isOne();
        assertThat(inspection.cells().get("A1").type()).isEqualTo("FORMULA");
    }

    @Test
    void generatedAndReopenedWorkbooksKeepPayloadsAsTextWithoutFormulaCells() throws Exception {
        List<PayloadCase> cases = List.of(
                new PayloadCase("equals", "=1+1", "'=1+1"),
                new PayloadCase("plus", "+1+1", "'+1+1"),
                new PayloadCase("minus", "-1+1", "'-1+1"),
                new PayloadCase("at", "@SUM(1,1)", "'@SUM(1,1)"),
                new PayloadCase("leadingSpace", " =1+1", " =1+1"),
                new PayloadCase("leadingTab", "\t=1+1", "\t=1+1"),
                new PayloadCase("leadingCr", "\r=1+1", "\r=1+1"),
                new PayloadCase("leadingLf", "\n=1+1", "\n=1+1"),
                new PayloadCase("leadingCrLf", "\r\n=1+1", "\r\n=1+1"),
                new PayloadCase("unicodeNbsp", unicode(0x00A0) + "=1+1", unicode(0x00A0) + "=1+1"),
                new PayloadCase("unicodeEmSpace", unicode(0x2003) + "=1+1", unicode(0x2003) + "=1+1"),
                new PayloadCase("unicodeIdeographicSpace", unicode(0x3000) + "=1+1", unicode(0x3000) + "=1+1"),
                new PayloadCase("unicodeNelControl", unicode(0x0085) + "=1+1", unicode(0x0085) + "=1+1"),
                new PayloadCase("unicodeLrmFormat", unicode(0x200E) + "=1+1", unicode(0x200E) + "=1+1"),
                new PayloadCase("embeddedCr", "文本\r=1+1", "文本\r=1+1"),
                new PayloadCase("embeddedLf", "文本\n=1+1", "文本\n=1+1"),
                new PayloadCase("embeddedCrLf", "文本\r\n=1+1", "文本\r\n=1+1"),
                new PayloadCase("chinese", "中文姓名：王小明", "中文姓名：王小明"),
                new PayloadCase("integer", 123, "123"),
                new PayloadCase("decimal", new BigDecimal("12.50"), "12.50"),
                new PayloadCase("legalNegative", new BigDecimal("-12.50"), "'-12.50"),
                new PayloadCase("alreadyEscaped", "'=1+1", "'=1+1"));

        List<String> fields = cases.stream().map(PayloadCase::field).toList();
        Map<String, Object> row = new LinkedHashMap<>();
        cases.forEach(payload -> row.put(payload.field(), payload.value()));
        ExportProperties properties = new ExportProperties();
        properties.setDir(exportDirectory.toString());
        ExportFileService service = new ExportFileService(properties);

        for (String filename : List.of("formula-check-first.xlsx", "formula-check-repeat.xlsx")) {
            ExportFileService.Generated generated = service.generate("PERSON", fields, List.of(row), filename);
            assertThat(generated.path()).exists();
            assertReopenedWorkbook(generated.path(), cases);
        }
    }

    private static void assertReopenedWorkbook(Path path, List<PayloadCase> cases) throws Exception {
        try (InputStream input = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(input)) {
            long formulaCells = 0;
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.FORMULA) formulaCells++;
                    }
                }
            }
            assertThat(formulaCells).as("formula cell count in %s", path.getFileName()).isZero();

            Sheet sheet = workbook.getSheet("PERSON");
            assertThat(sheet).isNotNull();
            Row data = sheet.getRow(1);
            assertThat(data).isNotNull();
            for (int i = 0; i < cases.size(); i++) {
                PayloadCase payload = cases.get(i);
                Cell cell = data.getCell(i);
                assertThat(cell).as("cell for %s", payload.field()).isNotNull();
                assertThat(cell.getCellType()).as("CellType for %s", payload.field()).isEqualTo(CellType.STRING);
                assertThat(cell.getStringCellValue()).as("read-back value for %s", payload.field())
                        .isEqualTo(payload.expected());
            }
        }
    }

    private static String unicode(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private record PayloadCase(String field, Object value, String expected) {}
}
