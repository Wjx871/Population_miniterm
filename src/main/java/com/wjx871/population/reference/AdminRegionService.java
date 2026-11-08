package com.wjx871.population.reference;

import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRegionService {
    private final AdminRegionMapper mapper;
    private final OperationLogService audit;

    public List<AdminRegion> search(String parent, Integer level, String status, String keyword) {
        return mapper.search(trim(parent), level, trim(status), trim(keyword));
    }

    public AdminRegion get(String code) {
        return mapper.findByCode(code).orElseThrow(() -> notFound("行政区划不存在"));
    }

    public List<AdminRegion> children(String code) {
        get(code);
        return mapper.search(code, null, null, null);
    }

    public List<AdminRegion> tree() {
        List<AdminRegion> all = mapper.search(null, null, "ENABLED", null);
        Map<Long, AdminRegion> byId = new LinkedHashMap<>();
        List<AdminRegion> roots = new ArrayList<>();
        all.forEach(x -> { x.setChildren(new ArrayList<>()); byId.put(x.getRegionId(), x); });
        for (AdminRegion x : all) {
            if (x.getParentId() == null) roots.add(x);
            else {
                AdminRegion p = byId.get(x.getParentId());
                if (p != null) p.getChildren().add(x);
            }
        }
        return roots;
    }

    @Transactional
    public AdminRegion create(AdminRegionCreateRequest q, HttpServletRequest http) {
        if (mapper.findByCode(q.regionCode()).isPresent()) throw conflict("行政区划编码已存在");
        AdminRegion proposed = requestedParent(q.parentCode(), q.regionLevel(), q.regionCode());
        Map<Long, AdminRegion> plan = new TreeMap<>();
        observeChain(proposed, plan, null);
        Map<Long, AdminRegion> locked = lockPlan(plan, null);
        AdminRegion parent = checkedParent(proposed, q.regionLevel(), locked);
        AdminRegion v = value(q.regionCode(), q.regionName(), parent, q.regionLevel(), q.fullName(), q.sortNo());
        v.setStatus("ENABLED");
        v.setVersion(0);
        mapper.insert(v);
        log("REGION_CREATE", q.regionCode(), http);
        return get(q.regionCode());
    }

    @Transactional
    public AdminRegion update(String code, AdminRegionUpdateRequest q, HttpServletRequest http) {
        AdminRegion old = get(code);
        AdminRegion proposed = requestedParent(q.parentCode(), q.regionLevel(), code);
        Map<Long, AdminRegion> plan = new TreeMap<>();
        observeChain(old, plan, null);
        observeChain(proposed, plan, old.getRegionId());
        Map<Long, AdminRegion> locked = lockPlan(plan, old.getRegionId());
        AdminRegion parent = checkedParent(proposed, q.regionLevel(), locked);
        AdminRegion v = value(code, q.regionName(), parent, q.regionLevel(), q.fullName(), q.sortNo());
        if (mapper.update(v, q.version()) == 0) throw conflict("行政区划已被其他操作修改");
        log("REGION_UPDATE", code, http);
        return get(code);
    }

    @Transactional
    public void disable(String code, VersionRequest q, HttpServletRequest http) {
        AdminRegion v = lockNodeAndAncestors(code);
        // Every hierarchy writer holds shared locks on its ancestors. This exclusive node lock
        // therefore protects the entire subtree, including inserts and moves into that subtree.
        rejectEnabledDescendants(v.getRegionId());
        change(code, "ENABLED", "DISABLED", q.version(), "REGION_DISABLE", http);
    }

    @Transactional
    public void enable(String code, VersionRequest q, HttpServletRequest http) {
        AdminRegion observed = get(code);
        Map<Long, AdminRegion> plan = new TreeMap<>();
        observeChain(observed, plan, null);
        Map<Long, AdminRegion> locked = lockPlan(plan, observed.getRegionId());
        AdminRegion v = locked.get(observed.getRegionId());
        requireEnabledAncestors(v.getParentId(), locked);
        change(code, "DISABLED", "ENABLED", q.version(), "REGION_ENABLE", http);
    }

    private AdminRegion lockNodeAndAncestors(String code) {
        AdminRegion observed = get(code);
        Map<Long, AdminRegion> plan = new TreeMap<>();
        observeChain(observed, plan, null);
        return lockPlan(plan, observed.getRegionId()).get(observed.getRegionId());
    }

    private AdminRegion requestedParent(String code, Integer level, String self) {
        if (code == null || code.isBlank()) {
            if (level != 1) throw badRequest("非根区划必须指定父区划");
            return null;
        }
        if (code.equals(self)) throw badRequest("区划不能以自身为父节点");
        return get(code);
    }

    /** Discovery may use an old RR snapshot; it is only a lock plan, never an authorization decision. */
    private void observeChain(AdminRegion start, Map<Long, AdminRegion> plan, Long forbiddenId) {
        Set<Long> visited = new HashSet<>();
        for (AdminRegion x = start; x != null;) {
            if (Objects.equals(x.getRegionId(), forbiddenId)) throw badRequest("区划父子关系不能形成循环");
            if (!visited.add(x.getRegionId())) throw conflict("行政区划存在历史循环，需先核查层级");
            plan.putIfAbsent(x.getRegionId(), x);
            x = x.getParentId() == null ? null : mapper.findById(x.getParentId())
                .orElseThrow(() -> conflict("行政区划祖先缺失，需先核查层级"));
        }
    }

    /** Single-table primary-key current reads, ascending IDs; no S-to-X lock upgrades. */
    private Map<Long, AdminRegion> lockPlan(Map<Long, AdminRegion> plan, Long exclusiveId) {
        Map<Long, AdminRegion> locked = new HashMap<>();
        for (AdminRegion observed : plan.values()) {
            Long id = observed.getRegionId();
            AdminRegion current = (Objects.equals(id, exclusiveId) ? mapper.lockById(id) : mapper.shareById(id))
                .orElseThrow(() -> conflict("行政区划层级已变化，请刷新后重试"));
            if (!Objects.equals(current.getParentId(), observed.getParentId()))
                throw conflict("行政区划层级已变化，请刷新后重试");
            if (!"ENABLED".equals(current.getStatus()) && !"DISABLED".equals(current.getStatus()))
                throw conflict("行政区划存在未知状态，需先核查数据");
            locked.put(id, current);
        }
        return locked;
    }

    private AdminRegion checkedParent(AdminRegion proposed, Integer level, Map<Long, AdminRegion> locked) {
        if (proposed == null) return null;
        AdminRegion parent = locked.get(proposed.getRegionId());
        if (parent.getRegionLevel() >= level) throw badRequest("子区划级别必须低于父区划");
        requireEnabledAncestors(parent.getRegionId(), locked);
        return parent;
    }

    private void requireEnabledAncestors(Long id, Map<Long, AdminRegion> locked) {
        while (id != null) {
            AdminRegion x = locked.get(id);
            if (x == null) throw conflict("行政区划层级已变化，请刷新后重试");
            if (!"ENABLED".equals(x.getStatus())) throw conflict("父区划或祖先区划未启用");
            id = x.getParentId();
        }
    }

    private void rejectEnabledDescendants(Long rootId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        visited.add(rootId);
        pending.add(rootId);
        while (!pending.isEmpty()) {
            for (AdminRegion child : currentChildren(pending.removeFirst())) {
                if (!visited.add(child.getRegionId())) throw conflict("行政区划存在历史循环，需先核查层级");
                if ("ENABLED".equals(child.getStatus())) throw conflict("存在启用后代区划，不能停用");
                if (!"DISABLED".equals(child.getStatus())) throw conflict("行政区划存在未知状态，需先核查数据");
                pending.addLast(child.getRegionId());
            }
        }
    }

    private List<AdminRegion> currentChildren(Long parentId) {
        try {
            // NOWAIT avoids a secondary traversal lock waiting on an older child ID while
            // that child's writer waits for the already-held ancestor lock.
            return mapper.shareChildrenNowait(parentId);
        } catch (DataAccessException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && (sql.getErrorCode() == 3572 || sql.getErrorCode() == 50200))
                    throw conflict("后代区划正在被并发修改，请刷新后重试");
            }
            throw e;
        }
    }

    private void change(String code, String from, String to, Integer version, String op, HttpServletRequest http) {
        if (mapper.updateStatus(code, from, to, version) == 0) throw conflict("状态或版本已变化");
        log(op, code, http);
    }

    private AdminRegion value(String code, String name, AdminRegion parent, Integer level, String full, Integer sort) {
        AdminRegion v = new AdminRegion();
        v.setRegionCode(code); v.setRegionName(name); v.setParentId(parent == null ? null : parent.getRegionId());
        v.setRegionLevel(level); v.setFullName(full); v.setSortNo(sort);
        return v;
    }

    private void log(String op, String code, HttpServletRequest http) {
        audit.recordTransactional(CurrentUserContext.requireUser().userId(), op, "REGION", code, http);
    }
    private String trim(String x) { return x == null || x.isBlank() ? null : x.trim(); }
    private BusinessException badRequest(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
    private BusinessException notFound(String message) { return new BusinessException(HttpStatus.NOT_FOUND, message); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
}
