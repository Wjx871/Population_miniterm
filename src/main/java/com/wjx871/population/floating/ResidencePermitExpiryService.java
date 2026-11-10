package com.wjx871.population.floating;

import com.wjx871.population.audit.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ResidencePermitExpiryService {
    private final FloatingResidenceMapper mapper;
    private final ResidencePermitDatePolicy dates;
    private final ResidencePermitExpiryWorker worker;
    private final ResidencePermitProperties properties;

    @Scheduled(cron="${residence-permit.expiry-cron:0 0 2 * * *}")
    public void scheduled() { runNow(); }

    /** Returns candidates visited, including records that became ineligible before locking.
     * A worker failure still propagates; earlier REQUIRES_NEW commits remain durable.
     */
    public int runNow() {
        LocalDate today=dates.today();
        int batchSize=properties.getExpiryBatchSize();
        int permits=scan(after -> mapper.expiredPermitIds(today,after,batchSize),
            id -> worker.expirePermit(id,today),batchSize);
        int registrations=scan(after -> mapper.expiredFloatingIds(today,after,batchSize),
            id -> worker.expireFloating(id,today),batchSize);
        return permits+registrations;
    }

    // No enclosing transaction: each read releases its connection before a worker starts.
    // Advance by the last candidate, even when the worker finds it is no longer eligible.
    private int scan(Function<Long,List<Long>> read,Consumer<Long> process,int batchSize) {
        long after=0;
        int visited=0;
        while(true) {
            List<Long> ids=read.apply(after);
            if(ids.isEmpty()) return visited;
            for(Long id:ids) process.accept(id);
            visited+=ids.size();
            after=ids.get(ids.size()-1);
            if(ids.size()<batchSize) return visited;
        }
    }
}

@Service
@RequiredArgsConstructor
class ResidencePermitExpiryWorker {
    private final FloatingResidenceMapper mapper;
    private final FloatingResidenceTransactionHook hook;
    private final OperationLogMapper audit;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void expirePermit(Long id,LocalDate today) {
        // Updating current_flag can acquire an implicit FK parent lock in MySQL.
        // Follow the same floating/person -> permit order as close/endorse (#32).
        Long floatingId=mapper.findPermitFloatingId(id).orElse(null);
        if(floatingId==null || mapper.lockFloating(floatingId).isEmpty()) return;
        ResidencePermit p=mapper.lockPermit(id).orElse(null);
        if(p==null || !"ACTIVE".equals(p.getStatus()) || !Integer.valueOf(1).equals(p.getCurrentFlag())
            || !Objects.equals(p.getFloatingId(),floatingId)
            || p.getValidUntil()==null || !p.getValidUntil().isBefore(today)) return;
        if(mapper.expirePermit(id,p.getVersion())==0) return;
        hook.afterPermitExpire();
        log(p,"EXPIRE","ACTIVE","EXPIRED","到期自动失效");
        system("RESIDENCE_PERMIT_EXPIRE");
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void expireFloating(Long id,LocalDate today) {
        FloatingPopulation f=mapper.lockFloating(id).orElse(null);
        if(f==null || !"ACTIVE".equals(f.getStatus()) || !Integer.valueOf(1).equals(f.getCurrentFlag())
            || f.getPlannedLeaveDate()==null || !f.getPlannedLeaveDate().isBefore(today)) return;
        if(mapper.closeFloating(id,"EXPIRED","PLANNED_LEAVE_EXPIRED",f.getVersion())==0) return;
        // Preserve registration expiry policy and floating/person -> permit lock order.
        mapper.findActivePermitByFloating(id).ifPresent(p -> {
            if(mapper.expirePermit(p.getPermitId(),p.getVersion())>0)
                log(p,"REGISTRATION_CLOSED","ACTIVE","EXPIRED","流动登记到期");
        });
        system("FLOATING_REGISTRATION_EXPIRE");
    }

    private void log(ResidencePermit p,String action,String from,String to,String reason) {
        ResidencePermitLog l=new ResidencePermitLog();
        l.setPermitId(p.getPermitId()); l.setAction(action); l.setFromStatus(from); l.setToStatus(to);
        l.setOldValidUntil(p.getValidUntil()); l.setNewValidUntil(p.getValidUntil()); l.setReason(reason);
        mapper.insertPermitLog(l);
    }
    private void system(String type) {
        audit.insert(OperationLog.builder().operationType(type).moduleName("FLOATING_RESIDENCE")
            .operationResult("SUCCESS").detail(type).build());
    }
}
