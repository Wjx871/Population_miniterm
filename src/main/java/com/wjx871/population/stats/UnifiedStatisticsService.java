package com.wjx871.population.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wjx871.population.cache.*;
import com.wjx871.population.dashboard.*;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class UnifiedStatisticsService {
 private final DashboardService dashboard;private final StatisticsMapper mapper;private final OptionalRedisService cache;private final RedisProperties properties;
 public DashboardOverviewView overview(int period,int expiry){String key=key("overview",period,expiry);return cached(key,new TypeReference<>(){},properties.getOverviewTtl(),()->dashboard.overview(period,expiry));}
 public DashboardChartsView charts(int days,int limit){String key=key("charts",days,limit);return cached(key,new TypeReference<>(){},properties.getTrendTtl(),()->dashboard.charts(days,limit));}
 public List<MigrationTrendPoint> migrationTrend(int days){return charts(days,8).getMigrationTrend();}
 public List<RegionCountView> regionDistribution(int limit){return charts(30,limit).getRegisteredPopulationByRegion();}
 public List<NamedCountView> householdDistribution(){return List.of(new NamedCountView("ACTIVE_HOUSEHOLD","有效家庭户",mapper.countHouseholds()));}
 public Map<String,Object> floatingPopulation(){DashboardOverviewView v=overview(30,30);return Map.of("active",v.getActiveFloatingPopulation(),"generatedDate",LocalDate.now());}
 public Map<String,Object> certificateExpiry(int days){DashboardOverviewView v=overview(30,days);return Map.of("expiryDays",days,"expiringResidencePermits",v.getExpiringResidencePermits());}
 public Map<String,Object> keyPopulation(){Map<String,Object> result=new LinkedHashMap<>();result.put("active",mapper.countKeyPopulation());result.put("definition","ACTIVE records only");result.put("generatedDate",LocalDate.now());return result;}
 private String key(String type,Object...args){DataScopeCriteria s=DataScopeCriteria.current();return cache.key("statistics:"+type+":"+s.dataScope()+":"+Objects.toString(s.regionCode(),"-")+":"+Objects.toString(s.departmentId(),"-")+":"+LocalDate.now()+":"+Arrays.toString(args));}
 private <T>T cached(String key,TypeReference<T> type,java.time.Duration ttl,Supplier<T> db){Optional<T> hit=cache.get(key,type);if(hit.isPresent())return hit.get();T value=db.get();cache.put(key,value,ttl);return value;}
}
