package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyAssistantService {
    private static final String NOTICE = "回答依据系统内置业务指南，正式办理要求请以主管部门最新规定为准。";
    private static final String REFUSAL = "该助手仅提供办理政策和系统使用指引，不查询或修改人口敏感数据。";
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(\\b\\d{17}[0-9Xx]\\b|\\b1[3-9]\\d{9}\\b|完整身份证|身份证号|手机号|注销.*人员|绕过审批|修改.*人口|(?:生成|执行).*sql|系统密钥)");
    private static final Pattern UNRELATED = Pattern.compile("量子|天气|股票|编程|代码|游戏|旅游|菜谱");
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final List<Chunk> chunks = new ArrayList<>();
    @Value("${policy.ai.enabled:false}") private boolean aiEnabled;
    @Value("${policy.ai.base-url:https://api.deepseek.com}") private String aiBaseUrl;
    @Value("${policy.ai.api-key:}") private String apiKey;
    @Value("${policy.ai.model:deepseek-chat}") private String model;
    @Value("${policy.ai.timeout-seconds:30}") private int timeoutSeconds;

    public PolicyAssistantService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @PostConstruct void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:policy-knowledge/*.md");
            for (Resource resource : resources) parse(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            log.info("Policy knowledge base loaded: {} chunks", chunks.size());
        } catch (Exception e) { throw new IllegalStateException("Unable to load policy knowledge base", e); }
    }
    private void parse(String markdown) {
        Map<String,String> meta = new HashMap<>(); String[] parts = markdown.split("---", 3);
        if (parts.length >= 3) { for (String line : parts[1].split("\\R")) { int i=line.indexOf(':'); if(i>0) meta.put(line.substring(0,i).trim(), line.substring(i+1).trim()); } markdown=parts[2]; }
        String section="概述"; StringBuilder text=new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) { add(meta,section,text); section=line.substring(3).trim(); text.setLength(0); } else text.append(line).append('\n');
        } add(meta,section,text);
    }
    private void add(Map<String,String> meta,String section,StringBuilder text) {
        String content=text.toString().trim(); if(!content.isBlank()) chunks.add(new Chunk(meta.getOrDefault("title","业务办理指南"),section,meta.getOrDefault("category","业务办理"),meta.getOrDefault("sourceType","PROJECT_GUIDE"),meta.getOrDefault("version","V1"),meta.getOrDefault("logicalPath","policy-guide"),content));
    }
    public QueryResponse query(String question) {
        String traceId=UUID.randomUUID().toString();
        if (SENSITIVE.matcher(question).find()) return new QueryResponse(REFUSAL,"SAFE_REFUSAL",1.0,List.of(),List.of(),traceId);
        if (UNRELATED.matcher(question).find()) return new QueryResponse("当前内置业务指南未提供足够依据来确认这个问题。请换用户籍迁入、迁出、居住证、家庭户、注销或审批办理相关的问题咨询。","RETRIEVAL_ONLY",0,List.of(),List.of(),traceId);
        List<Hit> hits=search(question,4); double confidence=hits.isEmpty()?0:Math.min(.96,hits.get(0).score()/18);
        if (hits.isEmpty() || confidence < .15) return new QueryResponse("当前内置业务指南未提供足够依据来确认这个问题。请换用户籍迁入、迁出、居住证、家庭户、注销或审批办理相关的问题咨询。","RETRIEVAL_ONLY",confidence,List.of(),List.of(),traceId);
        List<Citation> citations=new ArrayList<>(); for(int i=0;i<hits.size();i++){Chunk c=hits.get(i).chunk(); citations.add(new Citation(i+1,c.title(),c.section(),c.category(),c.sourceType(),c.version(),c.logicalPath(),summary(c.content())));}
        String fallback=composeFallback(citations); String answer=tryAi(question,citations).orElse(fallback);
        return new QueryResponse(answer, answer.equals(fallback)?"RETRIEVAL_ONLY":"AI_ASSISTED",confidence,citations,actions(hits),traceId);
    }
    private List<Hit> search(String q,int topK) {
        List<String> tokens=ngrams(normalize(q)); Map<String,Integer> df=new HashMap<>(); for(Chunk c:chunks) new HashSet<>(ngrams(normalize(c.content()+c.title()+c.section()))).forEach(t->df.merge(t,1,Integer::sum)); int n=chunks.size();
        return chunks.stream().map(c->{List<String> doc=ngrams(normalize(c.content())); double score=0; for(String t:tokens){int f=Collections.frequency(doc,t); if(f>0) score+=Math.log((n-df.getOrDefault(t,0)+.5)/(df.getOrDefault(t,0)+.5)+1)*(f*2.2/(f+1.2)); if(normalize(c.title()+c.category()+c.section()).contains(t))score+=1.2;} return new Hit(c,score);}).filter(h->h.score()>0).sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(Math.max(1,Math.min(8,topK))).toList();
    }
    private static String normalize(String s){return s.replaceAll("\\s+","").toLowerCase(Locale.ROOT);}
    private static List<String> ngrams(String s){List<String> r=new ArrayList<>(); for(int i=0;i<s.length();i++)r.add(s.substring(i,Math.min(i+2,s.length()))); return r;}
    private Optional<String> tryAi(String question,List<Citation> citations) {
        if(!aiEnabled||apiKey==null||apiKey.isBlank()) return Optional.empty();
        try { String context=citations.stream().map(c->"["+c.index()+"] "+c.title()+"/"+c.section()+"："+c.summary()).collect(Collectors.joining("\n")); String prompt="仅依据以下资料回答问题，不得编造政策、材料、时限或法规。使用[1]引用。以办理说明、材料、步骤、注意事项、下一步组织；没有依据时明确说明。不要输出路径或技术实现。\n资料：\n"+context+"\n问题："+question;
            String body=objectMapper.writeValueAsString(Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),"temperature",0.1));
            HttpRequest request=HttpRequest.newBuilder(URI.create(aiBaseUrl.replaceAll("/$","")+"/v1/chat/completions")).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).timeout(java.time.Duration.ofSeconds(timeoutSeconds)).build();
            JsonNode node=objectMapper.readTree(httpClient.send(request,HttpResponse.BodyHandlers.ofString()).body()); String answer=node.path("choices").path(0).path("message").path("content").asText(); return answer.isBlank()?Optional.empty():Optional.of(answer+"\n\n"+NOTICE);
        } catch(Exception e) { log.warn("Policy AI unavailable; using retrieval-only fallback: {}",e.getClass().getSimpleName()); return Optional.empty(); }
    }
    private String composeFallback(List<Citation> citations){return "根据内置业务指南，建议按以下参考内容核对办理条件和系统入口：\n\n"+citations.stream().map(c->"["+c.index()+"] "+c.summary()).collect(Collectors.joining("\n\n"))+"\n\n"+NOTICE;}
    private static String summary(String s){return s.length()>260?s.substring(0,260)+"…":s;}
    private static List<SuggestedAction> actions(List<Hit> hits){return hits.stream().map(Hit::chunk).map(c->{String p=switch(c.category()){case "迁入","迁出"->"/migrations";case "居住证"->"/residence-permits";case "审批"->"/approvals";case "家庭户"->"/households";default->"/applications";};return new SuggestedAction(c.category()+"相关功能",p);}).distinct().limit(3).toList();}
    public List<String> suggestions(){return List.of("办理户籍迁入需要什么材料？","户籍迁出的流程是什么？","居住证到期后怎么办？","申请被驳回后怎么办？","审批通过是不是已经办结？");}
    record Chunk(String title,String section,String category,String sourceType,String version,String logicalPath,String content){} record Hit(Chunk chunk,double score){}
    public record Citation(int index,String title,String section,String category,String sourceType,String version,String logicalPath,String summary){} public record SuggestedAction(String label,String path){} public record QueryResponse(String answer,String mode,double confidence,List<Citation> citations,List<SuggestedAction> suggestedActions,String traceId){}
}
