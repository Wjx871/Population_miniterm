-- Household and migration prerequisite data for course demonstrations only.
-- Run after population_miniterm.sql. All people, phones and addresses are fictional.
USE population_miniterm;

INSERT INTO person(name,gender,id_card,birth_date,ethnicity,phone,current_address,status,current_status_code) VALUES
 ('演示二号家庭户主','M','110101198001010010','1980-01-01','汉族','13900001001','北京市课程演示一街1号','正常','REGISTERED'),
 ('演示二号家庭配偶','F','110101198202020020','1982-02-02','汉族','13900001002','北京市课程演示一街1号','正常','REGISTERED'),
 ('演示二号家庭子女','F','110101201203030030','2012-03-03','汉族','13900001003','北京市课程演示一街1号','正常','REGISTERED'),
 ('演示二号集体户主','M','110105197504040044','1975-04-04','汉族','13900001004','北京市课程演示集体户1号','正常','REGISTERED'),
 ('演示二号集体成员甲','F','110105199505050059','1995-05-05','汉族','13900001005','北京市课程演示集体户1号','正常','REGISTERED'),
 ('演示二号集体成员乙','M','110105199606060061','1996-06-06','汉族','13900001006','北京市课程演示集体户1号','正常','REGISTERED'),
 ('演示二号可迁出人员','M','110106198707070076','1987-07-07','汉族','13900001007','北京市课程演示源区7号','正常','REGISTERED'),
 ('演示二号可迁入人员','F','110106199808080083','1998-08-08','汉族','13900001008','北京市课程演示待迁地址8号','正常','PENDING'),
 ('演示二号目标户主','M','110102199909090090','1999-09-09','汉族','13900001009','北京市课程演示目标区9号','正常','REGISTERED')
ON DUPLICATE KEY UPDATE person_id=person_id;

INSERT INTO household(household_no,head_person_id,address,region_code,household_type,establish_date,status) VALUES
 ('DEMO-HH2-FAMILY',(SELECT person_id FROM person WHERE id_card='110101198001010010'),'北京市课程演示一街1号','110101','FAMILY','2010-01-01','ACTIVE'),
 ('DEMO-HH2-COLLECTIVE',(SELECT person_id FROM person WHERE id_card='110105197504040044'),'北京市课程演示集体户1号','110105','COLLECTIVE','2015-04-04','ACTIVE'),
 ('DEMO-HH2-SOURCE',(SELECT person_id FROM person WHERE id_card='110106198707070076'),'北京市课程演示源区7号','110106','FAMILY','2018-07-07','ACTIVE'),
 ('DEMO-HH2-TARGET',(SELECT person_id FROM person WHERE id_card='110102199909090090'),'北京市课程演示目标区9号','110102','FAMILY','2020-09-09','ACTIVE')
ON DUPLICATE KEY UPDATE household_id=household_id;

INSERT INTO household_member(household_id,person_id,relationship,join_date,status)
SELECT h.household_id,p.person_id,x.relationship,x.join_date,'ACTIVE'
FROM (
 SELECT 'DEMO-HH2-FAMILY' household_no,'110101198001010010' id_card,'HEAD' relationship,DATE '2010-01-01' join_date UNION ALL
 SELECT 'DEMO-HH2-FAMILY','110101198202020020','SPOUSE',DATE '2010-01-01' UNION ALL
 SELECT 'DEMO-HH2-FAMILY','110101201203030030','CHILD',DATE '2012-03-03' UNION ALL
 SELECT 'DEMO-HH2-COLLECTIVE','110105197504040044','HEAD',DATE '2015-04-04' UNION ALL
 SELECT 'DEMO-HH2-COLLECTIVE','110105199505050059','MEMBER',DATE '2022-05-05' UNION ALL
 SELECT 'DEMO-HH2-COLLECTIVE','110105199606060061','MEMBER',DATE '2022-06-06' UNION ALL
 SELECT 'DEMO-HH2-SOURCE','110106198707070076','HEAD',DATE '2018-07-07' UNION ALL
 SELECT 'DEMO-HH2-TARGET','110102199909090090','HEAD',DATE '2020-09-09'
) x JOIN person p ON p.id_card=x.id_card JOIN household h ON h.household_no=x.household_no
ON DUPLICATE KEY UPDATE member_id=member_id;

INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by)
SELECT p.person_id,h.household_id,h.address,h.region_code,x.register_type,x.register_date,x.register_date,'ACTIVE',u.user_id
FROM (
 SELECT '110101198001010010' id_card,'DEMO-HH2-FAMILY' household_no,'INITIAL' register_type,DATE '2010-01-01' register_date UNION ALL
 SELECT '110101198202020020','DEMO-HH2-FAMILY','MIGRATION_IN',DATE '2010-01-01' UNION ALL
 SELECT '110101201203030030','DEMO-HH2-FAMILY','BIRTH',DATE '2012-03-03' UNION ALL
 SELECT '110105197504040044','DEMO-HH2-COLLECTIVE','INITIAL',DATE '2015-04-04' UNION ALL
 SELECT '110105199505050059','DEMO-HH2-COLLECTIVE','MIGRATION_IN',DATE '2022-05-05' UNION ALL
 SELECT '110105199606060061','DEMO-HH2-COLLECTIVE','MIGRATION_IN',DATE '2022-06-06' UNION ALL
 SELECT '110106198707070076','DEMO-HH2-SOURCE','INITIAL',DATE '2018-07-07' UNION ALL
 SELECT '110102199909090090','DEMO-HH2-TARGET','INITIAL',DATE '2020-09-09'
) x JOIN person p ON p.id_card=x.id_card JOIN household h ON h.household_no=x.household_no
JOIN sys_user u ON u.username='household'
WHERE NOT EXISTS(SELECT 1 FROM residence existing WHERE existing.person_id=p.person_id)
  AND EXISTS(SELECT 1 FROM household_member member WHERE member.person_id=p.person_id AND member.household_id=h.household_id AND member.status='ACTIVE')
ON DUPLICATE KEY UPDATE residence_id=residence_id;

-- Intentionally no business_application, material, approval, migration or archive rows.
-- Use the professional migration APIs with DEMO-HH2-SOURCE / DEMO-HH2-TARGET and the two candidate people.
