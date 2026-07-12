package com.wjx871.population.person;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 人口基础信息接口控制器。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final OperationLogService audit;

    @GetMapping
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<Page<Person>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String idCard,
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return ApiResponse.ok(personService.search(name, idCard, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<Person> get(@PathVariable Long id) {
        return ApiResponse.ok(personService.get(id));
    }

    @GetMapping("/id-card/{idCard}")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<Person> getByIdCard(@PathVariable String idCard) {
        return ApiResponse.ok(personService.getByIdCard(idCard));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('population:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Person> create(@Valid @RequestBody PersonRequest request,HttpServletRequest http) {
        Person result=personService.create(request);audit.record(CurrentUserContext.requireUser().userId(),"PERSON_CREATE","SUCCESS",null,http);return ApiResponse.created(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('population:edit')")
    public ApiResponse<Person> update(
            @PathVariable Long id,
            @Valid @RequestBody PersonRequest request,HttpServletRequest http
    ) {
        Person result=personService.update(id, request);audit.record(CurrentUserContext.requireUser().userId(),"PERSON_UPDATE","SUCCESS",null,http);return ApiResponse.ok(result);
    }

}
