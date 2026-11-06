package com.wjx871.population.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.Locale;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect @Component @RequiredArgsConstructor
// Wrap the default transaction advisor: standalone writes commit before invalidation;
// an existing outer transaction still defers invalidation through afterCommit.
@Order(0)
public class ReferenceCacheAspect {
 private final OptionalRedisService cache;private final RedisProperties properties;private final ObjectMapper json;
 @Around("execution(* com.wjx871.population.reference.AdminRegionService.tree()) || execution(* com.wjx871.population.reference.AdminRegionService.children(String)) || execution(* com.wjx871.population.reference.DataDictionaryService.byType(String))")
 public Object cached(ProceedingJoinPoint p) throws Throwable{Method method=((MethodSignature)p.getSignature()).getMethod();String key;Duration ttl;
  if(method.getName().equals("tree")){key=cache.key("region:tree");ttl=properties.getRegionTtl();}
  else if(method.getName().equals("children")){key=cache.key("region:children:"+p.getArgs()[0]);ttl=properties.getRegionTtl();}
  else{
   // Effective visibility must be resolved before every lookup, including warm hits.
   Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
   AuthenticatedUser user=CurrentUserContext.requireUser();
   if(!authentication.isAuthenticated()||!user.isEnabled())throw new IllegalStateException("当前请求没有有效的已认证用户");
   String type=(String)p.getArgs()[0];
   if(type==null||type.isBlank())return p.proceed();
   String visibility=user.permissions().contains("dictionary:manage")?"MANAGE":"ENABLED_ONLY";
   key=cache.key("dictionary:v2:"+visibility+":"+type.trim().toUpperCase(Locale.ROOT));ttl=properties.getDictionaryTtl();
  }
  // Shared caches cannot represent a transaction's uncommitted database view.
  if(TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly())return p.proceed();
  Optional<?> hit=cache.get(key,json.getTypeFactory().constructType(method.getGenericReturnType()));if(hit.isPresent())return hit.get();Object value=p.proceed();cache.put(key,value,ttl);return value;}
 @AfterReturning("execution(* com.wjx871.population.reference.AdminRegionService.create(..)) || execution(* com.wjx871.population.reference.AdminRegionService.update(..)) || execution(* com.wjx871.population.reference.AdminRegionService.enable(..)) || execution(* com.wjx871.population.reference.AdminRegionService.disable(..))")
 public void regionChanged(){CacheInvalidation.afterCommit(()->cache.evictPattern(cache.key("region:*") ));}
 @AfterReturning("execution(* com.wjx871.population.reference.DataDictionaryService.create(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.update(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.enable(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.disable(..))")
 public void dictionaryChanged(){CacheInvalidation.afterCommit(()->cache.evictPattern(cache.key("dictionary:*") ));}
}
