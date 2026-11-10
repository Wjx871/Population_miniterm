package com.wjx871.population.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Real security/controller/service/mapper/file/download chain; H2 fixtures roll back. */
@SpringBootTest(properties = {"export.normal-max-rows=1000", "export.sensitive-max-rows=1000"})
@AutoConfigureMockMvc
@Transactional
class ExportFormulaInjectionIntegrationTest {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Path ROOT = Path.of("target/issue78", UUID.randomUUID().toString());
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("export.dir", () -> ROOT.resolve("files").toAbsolutePath().toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final List<ExportFormulaPayloads.Payload> cases = ExportFormulaPayloads.cases();
    private String applicant;

    @BeforeEach
    void fixtures() throws Exception {
        // Seed at the persisted-data boundary, bypassing input validation to test worst-case export values.
        for (int i = 0; i < cases.size(); i++) {
            jdbc.update("INSERT INTO person(person_id,name,gender,id_card,phone,current_address,status,current_status_code,"
                            + "initial_scope_user_id,initial_scope_department_id,initial_scope_region_code) "
                            + "VALUES(?,?,'1',?,'13800138000',?,'正常','REGISTERED',2,1,'110000')",
                    780000 + i, cases.get(i).input(), identity(i), cases.get(i).input());
        }
        applicant = token("population");
    }

    @Test
    void normalHttpExportAndDownloadKeepFormulaShapedNamesAsText() throws Exception {
        exercise(false);
    }

    @Test
    void approvedSensitiveHttpExportAndDownloadKeepNamesAndAddressesAsText() throws Exception {
        exercise(true);
    }

    private void exercise(boolean sensitive) throws Exception {
        List<String> fields = sensitive ? List.of("name", "identityNo", "registeredAddress", "phone")
                : List.of("name", "maskedIdentityNo", "maskedPhone");
        for (String attempt : List.of("first", "regenerated")) {
            long logId;
            if (sensitive) {
                long application = request("/api/exports/sensitive/applications", applicant, Map.of(
                        "module", "PERSON", "filters", Map.of(), "fields", fields,
                        "reason", "公式形状文本独立复核", "expectedRowLimit", 1000, "title", "XLSX复核"), 201).asLong();
                // Same verified metadata fixture as the existing export approval tests; no real material upload.
                jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,"
                                + "stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,"
                                + "verify_user_id,uploaded_by) VALUES(?,'EXPORT_JUSTIFICATION','说明','x.pdf',?,"
                                + "'test','application/pdf',1,?,1,'VERIFIED',7,2)",
                        application, UUID.randomUUID() + ".pdf", "0".repeat(64));
                mvc.perform(post("/api/applications/" + application + "/submit")
                        .header("Authorization", applicant)).andExpect(status().isOk());
                long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",
                        Long.class, application);
                request("/api/approvals/" + approval + "/approve", token("approver"),
                        Map.of("comment", "同意", "version", 0), 200);
                assertThat(jdbc.queryForObject("SELECT requested_format FROM data_export_request WHERE application_id=?",
                        String.class, application)).isEqualTo("XLSX");
                logId = request("/api/exports/sensitive/applications/" + application + "/execute", token("household"),
                        Map.of("version", 2), 200).path("exportLogId").asLong();
            } else {
                logId = request("/api/exports/normal", applicant,
                        Map.of("module", "PERSON", "filters", Map.of(), "fields", fields), 201)
                        .path("exportLogId").asLong();
            }
            var response = mvc.perform(get("/api/exports/" + logId + "/download")
                    .header("Authorization", applicant)).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(response.getContentType()).isEqualTo(MIME);
            assertThat(response.getHeader("Content-Disposition")).contains(".xlsx");
            Path evidence = ROOT.resolve((sensitive ? "sensitive-" : "normal-") + attempt);
            Files.createDirectories(evidence);
            Path file = evidence.resolve("download.xlsx");
            Files.write(file, response.getContentAsByteArray(), StandardOpenOption.CREATE_NEW);
            var inspection = ExportXlsxInspection.inspect(file);
            List<Map<String, Object>> values = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                String key = sensitive ? identity(i) : "1101**********" + (1000 + i);
                String identityCell = inspection.cells().entrySet().stream()
                        .filter(e -> e.getKey().startsWith("B") && e.getValue().text().equals(key))
                        .map(Map.Entry::getKey).findFirst().orElseThrow();
                String row = identityCell.substring(1);
                for (String column : sensitive ? List.of("A", "C") : List.of("A")) {
                    var cell = inspection.cells().get(column + row);
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("case", cases.get(i).label());
                    value.put("cell", column + row);
                    value.put("input", cases.get(i).input());
                    value.put("expected", cases.get(i).expected());
                    value.put("actual", cell);
                    values.add(value);
                }
            }
            // Preserve raw evidence before safety assertions, including on an unexpected RED.
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("mode", sensitive ? "SENSITIVE_APPROVED" : "NORMAL_MASKED");
            manifest.put("fixtureBoundary", "persisted H2 person rows; production HTTP export and download");
            manifest.put("sha256", inspection.sha256());
            manifest.put("poiFormulaCells", inspection.poiFormulaCells());
            manifest.put("xmlFormulaElements", inspection.xmlFormulaElements());
            manifest.put("externalLinkParts", inspection.externalLinkParts());
            manifest.put("externalRelationships", inspection.externalRelationships());
            manifest.put("values", values);
            manifest.put("allCells", inspection.cells());
            Files.writeString(evidence.resolve("manifest.json"), json.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest), StandardOpenOption.CREATE_NEW);
            for (var part : inspection.xmlParts().entrySet()) {
                Path destination = evidence.resolve("ooxml").resolve(part.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, part.getValue(), StandardOpenOption.CREATE_NEW);
            }
            assertThat(inspection.sha256()).isEqualTo(jdbc.queryForObject(
                    "SELECT file_sha256 FROM data_export_log WHERE export_log_id=?", String.class, logId));
            assertThat(inspection.poiFormulaCells()).isZero();
            assertThat(inspection.xmlFormulaElements()).isZero();
            assertThat(inspection.externalLinkParts()).isZero();
            assertThat(inspection.externalRelationships()).isZero();
            for (var cell : inspection.cells().values()) {
                assertThat(cell.type()).isEqualTo("STRING");
                assertThat(cell.xml()).contains("t=\"s\"").doesNotContain("<f");
            }
            for (var value : values) {
                var cell = (ExportXlsxInspection.CellEvidence) value.get("actual");
                assertThat(cell.text()).as("%s %s", value.get("case"), value.get("cell")).isEqualTo(value.get("expected"));
            }
            System.out.println("ISSUE78_EVIDENCE " + evidence.toAbsolutePath() + " sha256=" + inspection.sha256());
        }
    }

    private JsonNode request(String url, String token, Object body, int expected) throws Exception {
        var response = mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data");
    }

    private String token(String username) throws Exception {
        return "Bearer " + request("/api/auth/login", "", Map.of("username", username, "password", "123456"), 200)
                .path("token").asText();
    }

    private String identity(int i) { return "11010119900101" + (1000 + i); }
}
