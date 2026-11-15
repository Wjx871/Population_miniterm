package com.wjx871.population.material;

import com.wjx871.population.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class MigrationMaterialOcrController {
    private final MigrationMaterialOcrService service;
    private final MigrationMaterialCheckService materialCheck;

    @PostMapping(value = "/api/applications/{id}/materials/household-ocr",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('material:upload')")
    public ApiResponse<MigrationMaterialOcrService.Result> previewHousehold(@PathVariable Long id,
            @RequestParam String pageType, @RequestPart("file") MultipartFile file, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(service.recognizeHousehold(id, pageType, file));
    }

    @PostMapping(value = "/api/applications/{id}/materials/migration-form-ocr",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('material:upload')")
    public ApiResponse<MigrationMaterialOcrService.Result> previewForm(@PathVariable Long id,
            @RequestPart("file") MultipartFile file, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(service.recognizeForm(id, file));
    }

    @PostMapping("/api/applications/{id}/materials/consistency-check")
    @PreAuthorize("hasAuthority('material:upload')")
    public ApiResponse<MigrationMaterialCheckService.Result> check(@PathVariable Long id,
            @RequestBody MigrationMaterialCheckService.Selection selection, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.ok(materialCheck.check(id, selection));
    }
}
