package com.wjx871.population.dataops;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ReplicaReadAspect {
    @Around("@annotation(com.wjx871.population.dataops.ReplicaRead) || @within(com.wjx871.population.dataops.ReplicaRead)")
    public Object route(ProceedingJoinPoint call) throws Throwable {
        // An already-started primary/write transaction must keep its connection.
        boolean nestedTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        boolean replica = !nestedTransaction || (ReadRouteContext.replica()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        Boolean previous = ReadRouteContext.set(replica);
        try { return call.proceed(); }
        finally { ReadRouteContext.restore(previous); }
    }
}
