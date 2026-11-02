package com.wjx871.population.migration;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidenceArchiveService {
    private final MigrationMapper mapper;

    @Transactional(readOnly = true)
    public Page<ArchiveView> search(String person, String identity, String household, String type, String region,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        DataScopeCriteria scope = archiveScope(user);
        String requestedRegion = trim(region);
        if (requestedRegion != null && user.dataScope() == DataScope.REGION
                && !user.regionCodes().contains(requestedRegion)) {
            throw forbidden("超出数据范围");
        }
        boolean full = canReadFull(user);
        return new PageImpl<>(mapper.searchArchives(trim(person), trim(identity), trim(household), trim(type),
                requestedRegion, from, to, scope, pageable.getPageSize(), pageable.getOffset()).stream()
                .map(archive -> ArchiveView.from(archive, full)).toList(), pageable,
                mapper.countArchives(trim(person), trim(identity), trim(household), trim(type), requestedRegion,
                        from, to, scope));
    }

    @Transactional(readOnly = true)
    public ArchiveView get(Long id) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        DataScopeCriteria scope = archiveScope(user);
        ResidenceArchive archive = mapper.findArchive(id, scope)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "归档不存在或超出数据范围"));
        return ArchiveView.from(archive, canReadFull(user));
    }

    private DataScopeCriteria archiveScope(AuthenticatedUser user) {
        if (user.dataScope() == DataScope.SELF) {
            throw forbidden("SELF 数据范围不能查询迁移归档");
        }
        if (user.dataScope() == DataScope.REGION && user.regionCodes().isEmpty()) {
            throw forbidden("用户区划数据范围无法验证");
        }
        return new DataScopeCriteria(user.dataScope(), user.userId(), user.departmentId(), user.regionCode(),
                user.regionCodes());
    }

    private boolean canReadFull(AuthenticatedUser user) {
        return user.permissions().contains("migration:execute")
                || user.permissions().contains("system:user:manage");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
