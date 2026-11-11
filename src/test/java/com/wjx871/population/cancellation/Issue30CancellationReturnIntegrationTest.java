package com.wjx871.population.cancellation;
import static org.assertj.core.api.Assertions.assertThat;import static org.assertj.core.api.Assertions.assertThatThrownBy;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import com.fasterxml.jackson.databind.*;import java.util.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.http.MediaType;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:issue30-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE") @AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE) class Issue30CancellationReturnIntegrationTest {private org.springframework.test.web.servlet.result.StatusResultMatchers status(){return org.springframework.test.web.servlet.result.MockMvcResultMatchers.status();}@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired CancellationService cancellationService;@Autowired com.wjx871.population.auth.AuthService auth;@Autowired com.wjx871.population.security.JwtService jwt;@MockBean CancellationTransactionHook hook;
@BeforeEach void setup(){reset(hook);jdbc.update("DELETE FROM household_archive");jdbc.update("DELETE FROM cancellation_record");jdbc.update("DELETE FROM residence_archive");jdbc.update("DELETE FROM migration_in");jdbc.update("DELETE FROM migration_out");jdbc.update("DELETE FROM residence");jdbc.update("DELETE FROM household_member");jdbc.update("DELETE FROM sys_approval_log");jdbc.update("DELETE FROM sys_approval_request");jdbc.update("DELETE FROM application_material");jdbc.update("DELETE FROM business_application");jdbc.update("DELETE FROM operation_log");jdbc.update("DELETE FROM household WHERE household_id>=201");jdbc.update("DELETE FROM person WHERE person_id>=201");jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) VALUES(201,'张甲','1','110101199001010011','旧址','正常','REGISTERED'),(202,'李乙','2','110101199202020022','旧址','正常','REGISTERED'),(203,'王丙','1','110101199303030033',NULL,'正常','MIGRATED_OUT')");jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,household_type,establish_date,status,version) VALUES(201,'HC-201',201,'北京市旧址','110101','FAMILY',DATE '2020-01-01','ACTIVE',0),(202,'HC-202',NULL,'北京市空户','110101','FAMILY',DATE '2020-01-01','PENDING_CANCELLATION',0)");jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(201,201,'HEAD',DATE '2020-01-01','ACTIVE',0),(201,202,'CHILD',DATE '2020-01-01','ACTIVE',0)");jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(201,201,'北京市旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),(202,201,'北京市旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");}

@Test void draftReturnIsRejectedWithoutChanges() throws Exception {
 long id=createPerson("population",201,202L,201);
 int logsBefore=count("sys_approval_log","application_id",id);
 returnIt(id,0,409);
 assertThat(status(id)).isEqualTo("DRAFT");
 assertThat(jdbc.queryForObject("SELECT business_status FROM cancellation_record WHERE application_id=?",String.class,id)).isEqualTo("DRAFT");
 assertThat(count("sys_approval_request","application_id",id)).isZero();
 assertThat(count("sys_approval_log","application_id",id)).isEqualTo(logsBefore);
}
@Test void personReturnAndResubmit() throws Exception { long id=approvedPerson("DEATH",202L); verifyReturnCycle(id,"population",false); }
@Test void householdReturnAndResubmit() throws Exception { long id=approvedHousehold(); verifyReturnCycle(id,"household",true); }
@Test void personWithdrawAfterReturn() throws Exception { long id=approvedPerson("DEATH",202L); verifyWithdraw(id,"population"); }
@Test void householdWithdrawAfterReturn() throws Exception { long id=approvedHousehold(); verifyWithdraw(id,"household"); }
private void verifyReturnCycle(long id,String user,boolean household) throws Exception {
 assertStates(id,"APPROVED","APPROVED","APPROVED");
 returnIt(id,99,409); assertStates(id,"APPROVED","APPROVED","APPROVED");
 returnIt(id,2,200); assertStates(id,"RETURNED","APPROVED","APPROVED");
 returnIt(id,2,409); assertStates(id,"RETURNED","APPROVED","APPROVED");
 mvc.perform(get("/api/cancellations/applications/"+id).header("Authorization",bearer(token(user))))
  .andExpect(status().isOk()).andExpect(jsonPath("$.data.application.status").value("RETURNED"))
  .andExpect(jsonPath("$.data.cancellation.businessStatus").value("APPROVED"))
  .andExpect(jsonPath("$.data.cancellation.applicationStatus").value("RETURNED"))
  .andExpect(jsonPath("$.data.executable").value(false));
 mvc.perform(get("/api/cancellations").header("Authorization",bearer(token(user))))
  .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].applicationId").value(id))
  .andExpect(jsonPath("$.data.content[0].businessStatus").value("APPROVED"))
  .andExpect(jsonPath("$.data.content[0].applicationStatus").value("RETURNED"));
 if(household) executeHousehold(id,409); else executePerson(id,409);
 submit(id,user,200); assertStates(id,"UNDER_REVIEW","UNDER_REVIEW","PENDING");
 mvc.perform(get("/api/cancellations/applications/"+id).header("Authorization",bearer(token(user))))
  .andExpect(status().isOk()).andExpect(jsonPath("$.data.executable").value(false));
 long approval=jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",Long.class,id);
 mvc.perform(post("/api/approvals/"+approval+"/approve").header("Authorization",bearer(token("approver"))).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"OK\",\"version\":0}")).andExpect(status().isOk());
 assertStates(id,"APPROVED","APPROVED","APPROVED");
 mvc.perform(get("/api/cancellations/applications/"+id).header("Authorization",bearer(token(user))))
  .andExpect(status().isOk()).andExpect(jsonPath("$.data.executable").value(true));
 assertThat(jdbc.queryForList("SELECT action FROM sys_approval_log WHERE application_id=? ORDER BY log_id",String.class,id)).containsSubsequence("SUBMIT","APPROVE","RETURN","SUBMIT","APPROVE");
}
private void verifyWithdraw(long id,String user) throws Exception {
 returnIt(id,2,200);
 mvc.perform(post("/api/applications/"+id+"/withdraw").header("Authorization",bearer(token(user)))).andExpect(status().isOk());
 assertStates(id,"WITHDRAWN","WITHDRAWN","APPROVED");
}
private void returnIt(long id,int version,int expected) throws Exception {
 mvc.perform(post("/api/applications/"+id+"/return").header("Authorization",bearer(token("household"))).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Return for review\",\"version\":"+version+"}")).andExpect(status().is(expected));
}
private void assertStates(long id,String app,String cancellation,String approval) {
 assertThat(status(id)).isEqualTo(app);
 assertThat(jdbc.queryForObject("SELECT business_status FROM cancellation_record WHERE application_id=?",String.class,id)).isEqualTo(cancellation);
 assertThat(jdbc.queryForObject("SELECT status FROM sys_approval_request WHERE application_id=?",String.class,id)).isEqualTo(approval);
}

private long approvedPerson(String reason,Long head)throws Exception{long id=createPerson("population",201,head,201,reason);material(id,"APPLICANT_IDENTITY_PROOF");material(id,"HOUSEHOLD_BOOK");material(id,reason.equals("DEATH")?"DEATH_CERTIFICATE":"SETTLEMENT_ABROAD_PROOF");submitApprove(id,"population");return id;}private long approvedHousehold()throws Exception{long id=createHousehold(202,201);material(id,"CANCELLATION_APPLICATION");material(id,"HOUSEHOLD_BOOK");material(id,"HOUSEHOLD_CANCELLATION_PROOF");submitApprove(id,"household");return id;}
private long createPerson(String user,long person,Long head,int expected)throws Exception{return createPerson(user,person,head,expected,"DEATH");}private long createPerson(String user,long person,Long head,int expected,String reason)throws Exception{String nh=head==null?"null":head.toString();String b=mvc.perform(post("/api/cancellations/person/applications").header("Authorization",bearer(token(user))).contentType(MediaType.APPLICATION_JSON).content("{\"personId\":"+person+",\"cancelReasonCode\":\""+reason+"\",\"eventDate\":\"2026-07-13\",\"newHeadPersonId\":"+nh+",\"sourceHouseholdId\":999,\"title\":\"人员注销\",\"reason\":\"课程办理\"}")).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return expected==201?json.readTree(b).path("data").path("applicationId").asLong():0;}
private long createHousehold(long household,int expected)throws Exception{String b=mvc.perform(post("/api/cancellations/household/applications").header("Authorization",bearer(token("household"))).contentType(MediaType.APPLICATION_JSON).content("{\"householdId\":"+household+",\"cancelReasonCode\":\"NO_ACTIVE_MEMBERS\",\"eventDate\":\"2026-07-13\",\"title\":\"家庭户销户\",\"reason\":\"无有效成员\"}")).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return expected==201?json.readTree(b).path("data").path("applicationId").asLong():0;}
private void material(long id,String type){material(id,type,true,"VERIFIED");}private void material(long id,String type,boolean required,String verify){String hash=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'x.pdf',?,?,'application/pdf',1,?,?,?,7,6)",id,type,type,UUID.randomUUID()+".pdf","test",hash,required?1:0,verify);}private void submit(long id,String user,int expected)throws Exception{mvc.perform(post("/api/applications/"+id+"/submit").header("Authorization",bearer(token(user)))).andExpect(status().is(expected));}private void submitApprove(long id,String user)throws Exception{submit(id,user,200);long approval=jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",Long.class,id);mvc.perform(post("/api/approvals/"+approval+"/approve").header("Authorization",bearer(token("approver"))).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\",\"version\":0}")).andExpect(status().isOk());}
private void executePerson(long id,int expected)throws Exception{mvc.perform(post("/api/cancellations/person/applications/"+id+"/execute").header("Authorization",bearer(token("household"))).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().is(expected));}private void executeHousehold(long id,int expected)throws Exception{mvc.perform(post("/api/cancellations/household/applications/"+id+"/execute").header("Authorization",bearer(token("household"))).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().is(expected));}
private int count(String table,String key,long id){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+key+"=?",Integer.class,id);}
private String status(long id){return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",String.class,id);}
private String token(String user){return jwt.createToken(auth.loadUser(user));}
private String bearer(String token){return "Bearer "+token;}
}
