-- Course demonstration data only. All identities and phone numbers are fictional.
-- Run after population_miniterm.sql. Statements are repeatable and preserve referential integrity.
USE population_miniterm;

INSERT INTO person(person_id,name,gender,id_card,birth_date,ethnicity,phone,current_address,status,current_status_code) VALUES
 (700001,'演示迁出人员','M','110101199001010015','1990-01-01','汉族','13900000001','北京市东城区演示路1号','正常','REGISTERED'),
 (700002,'演示迁入人员','F','110101199202020025','1992-02-02','汉族','13900000002','天津市演示路2号','正常','PENDING'),
 (700003,'演示注销人员','M','110101195503030034','1955-03-03','汉族','13900000003','北京市东城区演示路3号','正常','REGISTERED'),
 (700004,'演示流动人员','F','110101199404040040','1994-04-04','汉族','13900000004','北京市朝阳区演示路4号','正常','REGISTERED')
ON DUPLICATE KEY UPDATE name=VALUES(name),phone=VALUES(phone),current_address=VALUES(current_address);

INSERT INTO household(household_id,household_no,head_person_id,address,region_code,household_type,establish_date,status) VALUES
 (700001,'DEMO-HH-OUT',700001,'北京市东城区演示路1号','110101','FAMILY','2010-01-01','ACTIVE'),
 (700002,'DEMO-HH-IN',700002,'北京市东城区演示路2号','110101','FAMILY','2012-02-02','ACTIVE'),
 (700003,'DEMO-HH-CANCEL',700003,'北京市东城区演示路3号','110101','FAMILY','2000-03-03','PENDING_CANCELLATION')
ON DUPLICATE KEY UPDATE address=VALUES(address),region_code=VALUES(region_code),status=VALUES(status);

INSERT INTO household_member(member_id,household_id,person_id,relationship,join_date,status) VALUES
 (700001,700001,700001,'HEAD','2010-01-01','ACTIVE'),
 (700002,700002,700002,'HEAD','2012-02-02','ACTIVE'),
 (700003,700003,700003,'HEAD','2000-03-03','ACTIVE')
ON DUPLICATE KEY UPDATE relationship=VALUES(relationship),status=VALUES(status);

INSERT INTO residence(residence_id,person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by) VALUES
 (700001,700001,700001,'北京市东城区演示路1号','110101','HOUSEHOLD','2010-01-01','2010-01-01','ACTIVE',(SELECT user_id FROM sys_user WHERE username='household')),
 (700003,700003,700003,'北京市东城区演示路3号','110101','HOUSEHOLD','2000-03-03','2000-03-03','ACTIVE',(SELECT user_id FROM sys_user WHERE username='household'))
ON DUPLICATE KEY UPDATE registered_address=VALUES(registered_address),status=VALUES(status);

INSERT INTO floating_population(floating_id,registration_no,person_id,source_region_code,source_address,current_region_code,current_address,residence_reason_code,residence_proof_type,arrival_date,planned_leave_date,registration_date,eligible_from_date,department_id,operator_id,status,current_flag,remark) VALUES
 (700001,'DEMO-FLOAT-001',700004,'120000','天津市演示地址','110105','北京市朝阳区演示路4号','EMPLOYMENT','LEASE','2025-12-01','2027-12-01','2025-12-01','2026-05-30',(SELECT department_id FROM sys_department WHERE department_code='POPULATION'),(SELECT user_id FROM sys_user WHERE username='population'),'ACTIVE',1,'满足首次申领演示条件')
ON DUPLICATE KEY UPDATE current_address=VALUES(current_address),eligible_from_date=VALUES(eligible_from_date),status=VALUES(status);

INSERT INTO business_application(application_id,application_no,business_type,title,applicant_user_id,applicant_department_id,applicant_region_code,target_person_id,status,reason,remark) VALUES
 (700001,'DEMO-PERMIT-001','RESIDENCE_PERMIT_APPLICATION','演示居住证签发记录',(SELECT user_id FROM sys_user WHERE username='population'),(SELECT department_id FROM sys_department WHERE department_code='POPULATION'),'110105',700004,'COMPLETED','课程演示初始化','仅用于建立即将到期居住证')
ON DUPLICATE KEY UPDATE title=VALUES(title),remark=VALUES(remark);

INSERT INTO residence_permit(permit_id,permit_no,person_id,floating_id,source_application_id,issue_region_code,issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,status,current_flag) VALUES
 (700001,'DEMO-PERMIT-001',700004,700001,700001,'110105',(SELECT department_id FROM sys_department WHERE department_code='POPULATION'),'北京市演示签发机关',CURRENT_DATE - INTERVAL 350 DAY,CURRENT_DATE - INTERVAL 350 DAY,CURRENT_DATE + INTERVAL 15 DAY,'VALID',1)
ON DUPLICATE KEY UPDATE valid_until=VALUES(valid_until),status=VALUES(status),current_flag=VALUES(current_flag);
