package com.wjx871.population.audit;

import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class LogQueryService {
 private final LogQueryMapper mapper; private final SensitiveDataMaskingService masking;
 @Transactional(readOnly=true) public Page<LogQueryView> search(boolean loginOnly,String username,String type,String module,String result,String ip,LocalDateTime from,LocalDateTime to,int page,int size){
  if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"非法分页参数");DataScopeCriteria scope=DataScopeCriteria.current();
  List<LogQueryView> rows=mapper.search(loginOnly,trim(username),trim(type),trim(module),trim(result),trim(ip),from,to,scope,size,(long)page*size);rows.forEach(this::mask);
  return new PageImpl<>(rows,PageRequest.of(page,size),mapper.count(loginOnly,trim(username),trim(type),trim(module),trim(result),trim(ip),from,to,scope));}
 @Transactional(readOnly=true) public LogQueryView get(Long id){LogQueryView v=mapper.find(id,DataScopeCriteria.current()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"日志不存在"));mask(v);return v;}
 private void mask(LogQueryView v){v.setDetail(masking.auditDetail(v.getDetail()));v.setErrorMessage(masking.auditDetail(v.getErrorMessage()));}
 private String trim(String x){return x==null||x.isBlank()?null:x.trim();}
}
