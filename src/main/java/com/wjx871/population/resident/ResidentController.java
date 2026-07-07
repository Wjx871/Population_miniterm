package com.wjx871.population.resident;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/residents")
@RequiredArgsConstructor
public class ResidentController {

    private final ResidentService residentService;

    @GetMapping
    public ApiResponse<Page<Resident>> search(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return ApiResponse.ok(residentService.search(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Resident> get(@PathVariable Long id) {
        return ApiResponse.ok(residentService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Resident> create(@Valid @RequestBody ResidentRequest request) {
        return ApiResponse.created(residentService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Resident> update(
            @PathVariable Long id,
            @Valid @RequestBody ResidentRequest request
    ) {
        return ApiResponse.ok(residentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        residentService.delete(id);
    }
}
