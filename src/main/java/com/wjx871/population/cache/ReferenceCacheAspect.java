package com.wjx871.population.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect @Component @RequiredArgsConstructor
public class ReferenceCacheAspect {
 private final OptionalRedisService cache;private final RedisProperties properties;private final ObjectMapper json;
 @Around("execution(* com.wjx871.population.reference.AdminRegionService.tree()) || execution(* com.wjx871.population.reference.AdminRegionService.children(String)) || execution(* com.wjx871.population.reference.DataDictionaryService.byType(String))")
 public Object cached(ProceedingJoinPoint p) throws Throwable{Method method=((MethodSignature)p.getSignature()).getMethod();String key;Duration ttl;
  if(method.getName().equals("tree")){key=cache.key("region:tree");ttl=properties.getRegionTtl();}
  else if(method.getName().equals("children")){key=cache.key("region:children:"+p.getArgs()[0]);ttl=properties.getRegionTtl();}
  else{key=cache.key("dictionary:"+String.valueOf(p.getArgs()[0]).toUpperCase());ttl=properties.getDictionaryTtl();}
  Optional<?> hit=cache.get(key,json.getTypeFactory().constructType(method.getGenericReturnType()));if(hit.isPresent())return hit.get();Object value=p.proceed();cache.put(key,value,ttl);return value;}
 @AfterReturning("execution(* com.wjx871.population.reference.AdminRegionService.create(..)) || execution(* com.wjx871.population.reference.AdminRegionService.update(..)) || execution(* com.wjx871.population.reference.AdminRegionService.enable(..)) || execution(* com.wjx871.population.reference.AdminRegionService.disable(..))")
 public void regionChanged(){CacheInvalidation.afterCommit(()->cache.evictPattern(cache.key("region:*") ));}
 @AfterReturning("execution(* com.wjx871.population.reference.DataDictionaryService.create(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.update(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.enable(..)) || execution(* com.wjx871.population.reference.DataDictionaryService.disable(..))")
 public void dictionaryChanged(){CacheInvalidation.afterCommit(()->cache.evictPattern(cache.key("dictionary:*") ));}
}
