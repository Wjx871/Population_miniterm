package com.wjx871.population.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.security.JwtService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:issue62-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@DirtiesContext(classMode=DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class Issue62ApplicationApprovalRegionScopeIntegrationTest {
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired AuthService auth;
 @Autowired JwtService jwt;

 @BeforeEach void seed() {
  db.update("INSERT INTO admin_region(region_id,region_code,region_name,parent_id,region_level,full_name,sort_no,status) VALUES (62001,'990000','Synthetic root',NULL,1,'Synthetic root',0,'ENABLED'),(62002,'990100','Synthetic branch',62001,2,'Synthetic branch',0,'ENABLED'),(62003,'990101','Synthetic child 1',62002,3,'Synthetic child 1',0,'ENABLED'),(62004,'990102','Synthetic child 2',62002,3,'Synthetic child 2',0,'ENABLED'),(62005,'990200','Synthetic sibling',62001,2,'Synthetic sibling',0,'ENABLED')");
  String[] scopes={"REGION","DEPARTMENT","SELF","ALL","REGION","REGION"};
  for(int i=0;i<scopes.length;i++) {
   long id=62001+i;
   String region=i==4?null:"990100";
   db.update("INSERT INTO sys_department(department_id,department_code,department_name,region_code,status,sort_no) VALUES(?,?,?,?,'ENABLED',0)",id,"I62D"+i,"Synthetic department",region);
   db.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) VALUES(?,?,?,'L3',?,'ENABLED')",id,"I62R"+i,"Synthetic role",scopes[i]);
   db.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) VALUES(?,?,?, ?,?,'Synthetic user','ENABLED')",id,"i62user"+i,"unused-synthetic",id,id);
   db.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,permission_id FROM sys_permission WHERE permission_code='application:view'",id);
   if(i!=5) db.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,permission_id FROM sys_permission WHERE permission_code='approval:view'",id);
  }
  // The same subject and status are compared across both interfaces; a forged prefix is a distinct code.
  app(1,62001,62001,"990100",false); app(2,62002,62002,"990101",false);
  app(3,62003,62003,"990102",false); app(4,62004,62004,"990200",false);
  app(5,62005,62005,"990100999",false); app(6,62006,62006,"990101",true);
 }
 private void app(int n,long user,long department,String region,boolean processed) {
  long id=62100+n;
  db.update("INSERT INTO business_application(application_id,application_no,business_type,title,applicant_user_id,applicant_department_id,applicant_region_code,status,reason,version,created_at) VALUES(?,?,'GENERAL_SERVICE','Synthetic',?,?,?,?,'Synthetic',0,DATEADD('SECOND', ?, CURRENT_TIMESTAMP))",id,"I62-A"+n,user,department,region,processed?"APPROVED":"UNDER_REVIEW",n);
  db.update("INSERT INTO sys_approval_request(approval_id,approval_no,application_id,status,current_department_id,current_region_code,submitted_by,submitted_at,version) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,0)",id,"I62-P"+n,id,processed?"APPROVED":"PENDING",department,region,user);
 }
 private JsonNode request(String username,String path,int expected) throws Exception {
  var result=mvc.perform(get(path).header("Authorization","Bearer "+jwt.createToken(auth.loadUser(username)))).andReturn().getResponse();
  assertThat(result.getStatus()).as(path).isEqualTo(expected);
  return json.readTree(result.getContentAsString()).path("data");
 }
 private Set<Long> ids(JsonNode rows,String field) { Set<Long> out=new HashSet<>(); for(JsonNode row:rows) out.add(row.path(field).asLong()); return out; }
 @Test void regionListsAgreeAndRejectPrefixAndSibling() throws Exception {
  assertThat(auth.loadUser("i62user0").regionCodes()).containsExactlyInAnyOrder("990100","990101","990102");
  JsonNode page=request("i62user0","/api/applications?status=UNDER_REVIEW&size=20",200);
  assertThat(ids(page.path("content"),"applicationId")).containsExactlyInAnyOrder(62101L,62102L,62103L);
  assertThat(page.path("totalElements").asInt()).isEqualTo(3);
  assertThat(ids(request("i62user0","/api/approvals/pending",200),"applicationId")).containsExactlyInAnyOrderElementsOf(ids(page.path("content"),"applicationId"));
  JsonNode first=request("i62user0","/api/applications?status=UNDER_REVIEW&size=2&page=0",200);
  JsonNode second=request("i62user0","/api/applications?status=UNDER_REVIEW&size=2&page=1",200);
  Set<Long> combined=ids(first.path("content"),"applicationId"); combined.addAll(ids(second.path("content"),"applicationId"));
  assertThat(combined).containsExactlyInAnyOrder(62101L,62102L,62103L);
  assertThat(first.path("totalElements").asInt()).isEqualTo(3); assertThat(second.path("totalElements").asInt()).isEqualTo(3);
  assertThat(ids(request("i62user0","/api/approvals/processed",200),"applicationId")).containsExactly(62106L);
  var beforeApps=db.queryForList("SELECT application_id,status,version FROM business_application ORDER BY application_id");
  var beforeApprovals=db.queryForList("SELECT approval_id,status,version FROM sys_approval_request ORDER BY approval_id");
  int before=db.queryForObject("SELECT COUNT(*) FROM sys_approval_log",Integer.class);
  request("i62user0","/api/applications/62104",403); request("i62user0","/api/approvals/62104",403);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_approval_log",Integer.class)).isEqualTo(before);
  assertThat(db.queryForList("SELECT application_id,status,version FROM business_application ORDER BY application_id")).isEqualTo(beforeApps);
  assertThat(db.queryForList("SELECT approval_id,status,version FROM sys_approval_request ORDER BY approval_id")).isEqualTo(beforeApprovals);
 }
 @Test void scopeSemanticsAndOrdinaryApplicantRemainDistinct() throws Exception {
  assertThat(ids(request("i62user1","/api/applications?status=UNDER_REVIEW",200).path("content"),"applicationId")).containsExactly(62102L);
  assertThat(ids(request("i62user1","/api/approvals/pending",200),"applicationId")).containsExactly(62102L);
  assertThat(ids(request("i62user1","/api/approvals/processed",200),"applicationId")).isEmpty();
  assertThat(ids(request("i62user2","/api/applications?status=UNDER_REVIEW",200).path("content"),"applicationId")).containsExactly(62103L);
  assertThat(ids(request("i62user2","/api/approvals/pending",200),"applicationId")).containsExactly(62103L);
  assertThat(ids(request("i62user3","/api/approvals/pending",200),"applicationId")).hasSize(5);
  request("i62user4","/api/applications",403); request("i62user4","/api/approvals/pending",403);
  assertThat(ids(request("i62user5","/api/applications",200).path("content"),"applicationId")).containsExactly(62106L);
  request("i62user5","/api/approvals/pending",403); request("i62user5","/api/applications/62101",403);
 }
}