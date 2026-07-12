package com.wjx871.population.system;

import com.wjx871.population.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> health() {
        return ApiResponse.ok("Backend is running");
    }
}
