package com.wjx871.population.person.idcard;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Phase 14 / V4_013 人口-身份证影印本接口。 */
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonIdCardImageController {

    private final PersonIdCardImageService service;

    @PostMapping(value = "/idcard-image", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('person:create-with-idcard')")
    public ApiResponse<PersonIdCardImageView> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "skipOcr", defaultValue = "false") boolean skipOcr,
            HttpServletRequest http) {
        Long userId = CurrentUserContext.requireUser().userId();
        return ApiResponse.ok(service.uploadAndRecognize(file, skipOcr, userId, http));
    }

    @GetMapping("/idcard-image/{id}")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<PersonIdCardImageView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }
}
