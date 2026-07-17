#!/usr/bin/env python3
"""Generate deterministic, relationally consistent dashboard demo data for MySQL 8.

The output is deliberately SQL-only: review it or import it into a freshly
initialized disposable database. It never connects to a database itself.
"""

from __future__ import annotations

import argparse
from datetime import date
import hashlib
from pathlib import Path
import re
from typing import Iterable, Sequence


ID_WEIGHTS = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
ID_CHECK_CODES = "10X98765432"
DATABASE_NAME = re.compile(r"^[A-Za-z0-9_]+$")


def minimal_pdf(title: str) -> bytes:
    """Return a tiny deterministic PDF used only by fictional demo attachments."""
    stream = f"BT /F1 18 Tf 72 720 Td ({title}) Tj ET\n".encode("ascii")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"endstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    result = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for index, obj in enumerate(objects, start=1):
        offsets.append(len(result))
        result.extend(f"{index} 0 obj\n".encode("ascii"))
        result.extend(obj)
        result.extend(b"\nendobj\n")
    xref = len(result)
    result.extend(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("ascii"))
    result.extend(b"".join(f"{offset:010d} 00000 n \n".encode("ascii") for offset in offsets[1:]))
    result.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode("ascii"))
    return bytes(result)


DEMO_MATERIAL_FILES = {
    "demo-cancellation-identity-proof.pdf": minimal_pdf("Demo cancellation identity proof"),
    "demo-cancellation-application.pdf": minimal_pdf("Demo cancellation application"),
    "demo-household-book.pdf": minimal_pdf("Demo household book"),
    "demo-sensitive-export-justification.pdf": minimal_pdf("Demo sensitive export justification"),
    "demo-general-service-application.pdf": minimal_pdf("Demo general service application"),
}


def material_file_values(filename: str) -> tuple[int, str]:
    content = DEMO_MATERIAL_FILES[filename]
    return len(content), hashlib.sha256(content).hexdigest()


def sql_literal(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def write_values(
    handle, table: str, columns: Sequence[str], rows: Iterable[Sequence[object]], chunk_size: int = 200
) -> None:
    pending: list[Sequence[object]] = []
    for row in rows:
        pending.append(row)
        if len(pending) == chunk_size:
            write_chunk(handle, table, columns, pending)
            pending = []
    if pending:
        write_chunk(handle, table, columns, pending)


def write_chunk(handle, table: str, columns: Sequence[str], rows: Sequence[Sequence[object]]) -> None:
    handle.write(f"INSERT INTO {table} ({','.join(columns)}) VALUES\n")
    handle.write(",\n".join("(" + ",".join(sql_literal(value) for value in row) + ")" for row in rows))
    handle.write(";\n\n")


def chinese_id_card(sequence: int, birthday: date) -> str:
    base = f"110101{birthday:%Y%m%d}{sequence % 1000:03d}"
    checksum = sum(int(digit) * weight for digit, weight in zip(base, ID_WEIGHTS)) % 11
    return base + ID_CHECK_CODES[checksum]


def person_rows(count: int) -> list[tuple[object, ...]]:
    rows: list[tuple[object, ...]] = []
    for sequence in range(1, count + 1):
        birthday = date(
            1958 + (sequence - 1) % 48,
            1 + ((sequence - 1) // 48) % 12,
            1 + ((sequence - 1) // 576) % 28,
        )
        gender = "M" if sequence % 2 else "F"
        current_status = "REGISTERED" if sequence <= 720 else "FLOATING"
        region = "110101" if sequence % 3 else "110105"
        rows.append(
            (
                sequence,
                f"演示居民{sequence:04d}",
                gender,
                chinese_id_card(sequence, birthday),
                birthday.isoformat(),
                "汉族",
                f"139{sequence:08d}",
                f"示例市{region[-2:]}区演示街道{sequence:04d}号",
                "正常",
                current_status,
                region,
            )
        )
    return rows


def application_rows() -> list[tuple[object, ...]]:
    rows: list[tuple[object, ...]] = []

    def add(
        kind: str, business_type: str, number: int, target: int, status: str, approval: str, offset: int
    ) -> None:
        rows.append(
            (
                f"DEMO-APP-{kind}-{number:04d}",
                business_type,
                f"演示{kind}业务{number:04d}",
                target,
                status,
                approval,
                offset,
            )
        )

    for number in range(1, 351):
        add("PERMIT", "RESIDENCE_PERMIT_FIRST_ISSUE", number, 720 + number, "COMPLETED", "APPROVED", number % 30)
    for number in range(1, 91):
        add("MIGRATION_IN", "MIGRATION_IN", number, number, "COMPLETED", "APPROVED", number % 30)
    for number in range(1, 81):
        add("MIGRATION_OUT", "MIGRATION_OUT", number, 90 + number, "COMPLETED", "APPROVED", number % 30)
    for number in range(1, 41):
        add("CANCELLATION", "PERSON_CANCELLATION", number, 170 + number, "UNDER_REVIEW", "PENDING", number % 30)
    for number in range(1, 26):
        add("DATA_EXPORT", "SENSITIVE_DATA_EXPORT", number, 210 + number, "UNDER_REVIEW", "PENDING", number % 30)
    approval_states = ("PENDING", "APPROVED", "REJECTED", "APPROVED")
    application_states = ("UNDER_REVIEW", "APPROVED", "REJECTED", "COMPLETED")
    for number in range(1, 101):
        state_index = (number - 1) % len(approval_states)
        add(
            "POPULATION_SERVICE", "GENERAL_SERVICE",
            number,
            250 + number,
            application_states[state_index],
            approval_states[state_index],
            number % 30,
        )
    return rows


def write_demo_materials_and_logs(handle) -> None:
    """Attach complete, verified fictional evidence to every pending demo approval.

    All metadata points to the deterministic PDFs produced by the companion
    script. This keeps the generated SQL relationally complete while keeping
    binary attachments out of Git and MySQL.
    """
    material_specs = (
        ("PERSON_CANCELLATION", "APPLICANT_IDENTITY_PROOF", "\u7533\u8bf7\u4eba\u8eab\u4efd\u8bc1\u660e", "demo-cancellation-identity-proof.pdf", 1),
        ("PERSON_CANCELLATION", "CANCELLATION_APPLICATION", "\u6ce8\u9500\u7533\u8bf7\u4e66", "demo-cancellation-application.pdf", 1),
        ("PERSON_CANCELLATION", "HOUSEHOLD_BOOK", "\u6237\u53e3\u7c3f", "demo-household-book.pdf", 1),
        ("SENSITIVE_DATA_EXPORT", "EXPORT_JUSTIFICATION", "\u654f\u611f\u6570\u636e\u5bfc\u51fa\u4f9d\u636e", "demo-sensitive-export-justification.pdf", 1),
        ("GENERAL_SERVICE", "GENERAL_SERVICE_APPLICATION", "\u901a\u7528\u4e1a\u52a1\u7533\u8bf7\u4e66", "demo-general-service-application.pdf", 1),
    )
    columns = (
        "application_id,material_type,material_name,original_filename,stored_filename,storage_path,"
        "content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,verify_comment,verified_at,uploaded_by"
    )
    for business_type, material_type, material_name, filename, required in material_specs:
        size, digest = material_file_values(filename)
        handle.write(
            f"INSERT INTO application_material({columns})\n"
            f"SELECT a.application_id,'{material_type}','{material_name}','{filename}','{filename}',"
            f"'data/uploads/{filename}','application/pdf',{size},'{digest}',{required},'VERIFIED',"
            "@demo_approver_user,'\u6f14\u793a\u6750\u6599\u5df2\u6838\u9a8c\u901a\u8fc7',NOW(),@demo_population_user\n"
            "FROM business_application a\n"
            f"WHERE a.business_type='{business_type}' AND a.status='UNDER_REVIEW'\n"
            f"  AND NOT EXISTS (SELECT 1 FROM application_material m WHERE m.application_id=a.application_id AND m.material_type='{material_type}');\n\n"
        )

    handle.write(
        "INSERT INTO sys_approval_log(approval_id,application_id,action,from_status,to_status,operator_user_id,comment,operation_time,ip_address)\n"
        "SELECT ar.approval_id,a.application_id,'SUBMIT','DRAFT','UNDER_REVIEW',a.applicant_user_id,'\u6f14\u793a\u6570\u636e\u5df2\u63d0\u4ea4\u5ba1\u6279',"
        "DATE_SUB(ar.submitted_at,INTERVAL 2 MINUTE),'127.0.0.1'\n"
        "FROM sys_approval_request ar JOIN business_application a ON a.application_id=ar.application_id\n"
        "WHERE a.application_no LIKE 'DEMO-APP-%'\n"
        "  AND NOT EXISTS (SELECT 1 FROM sys_approval_log l WHERE l.application_id=a.application_id AND l.action='SUBMIT');\n\n"
        "INSERT INTO sys_approval_log(approval_id,application_id,action,from_status,to_status,operator_user_id,comment,operation_time,ip_address)\n"
        "SELECT ar.approval_id,a.application_id,CASE WHEN ar.status='REJECTED' THEN 'REJECT' ELSE 'APPROVE' END,"
        "'UNDER_REVIEW',CASE WHEN a.status='COMPLETED' THEN 'APPROVED' ELSE a.status END,ar.decided_by,ar.decision_comment,ar.decided_at,'127.0.0.1'\n"
        "FROM sys_approval_request ar JOIN business_application a ON a.application_id=ar.application_id\n"
        "WHERE a.application_no LIKE 'DEMO-APP-%' AND ar.status IN ('APPROVED','REJECTED')\n"
        "  AND NOT EXISTS (SELECT 1 FROM sys_approval_log l WHERE l.application_id=a.application_id AND l.action IN ('APPROVE','REJECT'));\n\n"
    )


def write_demo_repair_sql(database: str, output: Path) -> None:
    """Generate idempotent repairs for a database seeded by an older generator."""
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(
            "-- Repair only fictional DEMO-* data generated by an older script.\n"
            f"USE `{database}`;\nSTART TRANSACTION;\n\n"
            "SET @demo_population_user := (SELECT user_id FROM sys_user WHERE username='population' LIMIT 1);\n"
            "SET @demo_approver_user := (SELECT user_id FROM sys_user WHERE username='approver' LIMIT 1);\n\n"
            "UPDATE business_application a JOIN sys_approval_request ar ON ar.application_id=a.application_id\n"
            "SET a.status='UNDER_REVIEW'\n"
            "WHERE a.application_no LIKE 'DEMO-APP-POPULATION_SERVICE-%'\n"
            "  AND a.status='SUBMITTED' AND ar.status='PENDING';\n\n"
        )
        write_demo_materials_and_logs(handle)
        handle.write("COMMIT;\n")


def generate(database: str, output: Path, people: int) -> None:
    registered = 720
    floating_start = registered + 1
    permit_count = 350
    if people < floating_start + permit_count - 1:
        raise ValueError(f"--people 至少需要 {floating_start + permit_count - 1}，以生成居住证关联数据")

    households = registered // 3
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(
            "-- Generated by scripts/data/generate_dashboard_demo_data.py.\n"
            "-- Fictional data only. Import only into a freshly initialized disposable database.\n"
            f"USE `{database}`;\n"
            "START TRANSACTION;\n\n"
            "SET @demo_population_user := (SELECT user_id FROM sys_user WHERE username='population' LIMIT 1);\n"
            "SET @demo_household_user := (SELECT user_id FROM sys_user WHERE username='household' LIMIT 1);\n"
            "SET @demo_approver_user := (SELECT user_id FROM sys_user WHERE username='approver' LIMIT 1);\n"
            "SET @demo_population_department := (SELECT department_id FROM sys_department WHERE department_code='POPULATION' LIMIT 1);\n"
            "SET @demo_approval_department := (SELECT department_id FROM sys_department WHERE department_code='APPROVAL' LIMIT 1);\n\n"
            "CREATE TEMPORARY TABLE demo_seed_person (\n"
            "  seq INT PRIMARY KEY, name VARCHAR(50) NOT NULL, gender CHAR(1) NOT NULL, id_card VARCHAR(18) NOT NULL,\n"
            "  birth_date DATE NOT NULL, ethnicity VARCHAR(30) NOT NULL, phone VARCHAR(20) NOT NULL, current_address VARCHAR(255) NOT NULL,\n"
            "  person_status VARCHAR(20) NOT NULL, current_status_code VARCHAR(30) NOT NULL, region_code VARCHAR(20) NOT NULL\n"
            ") ENGINE=Memory;\n\n"
        )
        write_values(
            handle,
            "demo_seed_person",
            (
                "seq", "name", "gender", "id_card", "birth_date", "ethnicity", "phone", "current_address",
                "person_status", "current_status_code", "region_code",
            ),
            person_rows(people),
        )
        handle.write(
            "INSERT INTO person(name,gender,id_card,birth_date,ethnicity,phone,current_address,status,current_status_code)\n"
            "SELECT name,gender,id_card,birth_date,ethnicity,phone,current_address,person_status,current_status_code\n"
            "FROM demo_seed_person ORDER BY seq;\n\n"
            "CREATE TEMPORARY TABLE demo_seed_application (\n"
            "  application_no VARCHAR(40) PRIMARY KEY, business_type VARCHAR(50) NOT NULL, title VARCHAR(200) NOT NULL,\n"
            "  target_seq INT NOT NULL, application_status VARCHAR(30) NOT NULL, approval_status VARCHAR(30) NOT NULL, created_offset INT NOT NULL\n"
            ") ENGINE=Memory;\n\n"
        )
        write_values(
            handle,
            "demo_seed_application",
            ("application_no", "business_type", "title", "target_seq", "application_status", "approval_status", "created_offset"),
            application_rows(),
        )
        handle.write(
            "CREATE TEMPORARY TABLE demo_seed_household (\n"
            "  household_no VARCHAR(30) PRIMARY KEY, head_seq INT NOT NULL, address VARCHAR(255) NOT NULL, region_code VARCHAR(20) NOT NULL\n"
            ") ENGINE=Memory;\n\n"
        )
        household_rows = [
            (f"DEMO-HH-BULK-{number:04d}", (number - 1) * 3 + 1, f"示例市演示社区{number:04d}号", "110101" if number % 2 else "110105")
            for number in range(1, households + 1)
        ]
        write_values(handle, "demo_seed_household", ("household_no", "head_seq", "address", "region_code"), household_rows)
        handle.write(
            "INSERT INTO household(household_no,head_person_id,address,region_code,household_type,establish_date,status)\n"
            "SELECT h.household_no,p.person_id,h.address,h.region_code,'FAMILY',DATE_SUB(CURRENT_DATE, INTERVAL (h.head_seq % 3650) DAY),'ACTIVE'\n"
            "FROM demo_seed_household h JOIN demo_seed_person s ON s.seq=h.head_seq JOIN person p ON p.id_card=s.id_card;\n\n"
            "INSERT INTO household_member(household_id,person_id,relationship,join_date,status)\n"
            "SELECT h.household_id,p.person_id,CASE WHEN MOD(s.seq-1,3)=0 THEN 'HEAD' WHEN MOD(s.seq-1,3)=1 THEN 'SPOUSE' ELSE 'CHILD' END,\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 3650) DAY),'ACTIVE'\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN demo_seed_household dh ON dh.household_no=CONCAT('DEMO-HH-BULK-',LPAD(FLOOR((s.seq-1)/3)+1,4,'0'))\n"
            "JOIN household h ON h.household_no=dh.household_no WHERE s.seq <= 720;\n\n"
            "INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by)\n"
            "SELECT p.person_id,h.household_id,h.address,h.region_code,CASE WHEN MOD(s.seq,3)=0 THEN 'MIGRATION_IN' ELSE 'INITIAL' END,\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),'ACTIVE',@demo_household_user\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN demo_seed_household dh ON dh.household_no=CONCAT('DEMO-HH-BULK-',LPAD(FLOOR((s.seq-1)/3)+1,4,'0'))\n"
            "JOIN household h ON h.household_no=dh.household_no WHERE s.seq <= 720;\n\n"
            "INSERT INTO floating_population(registration_no,person_id,source_region_code,source_address,current_region_code,current_address,residence_reason_code,residence_proof_type,arrival_date,planned_leave_date,registration_date,eligible_from_date,department_id,operator_id,status,current_flag,remark)\n"
            "SELECT CONCAT('DEMO-FLOAT-',LPAD(s.seq,4,'0')),p.person_id,'120000',CONCAT('外地示例地址',s.seq),'110105',s.current_address,\n"
            "       CASE WHEN MOD(s.seq,3)=0 THEN 'EMPLOYMENT' ELSE 'FAMILY_REUNION' END,'LEASE',DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 180) DAY),\n"
            "       DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY),DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 60) DAY),\n"
            "       @demo_population_department,@demo_population_user,'ACTIVE',1,'批量生成的虚构演示流动人口'\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card WHERE s.seq > 720;\n\n"
            "INSERT INTO business_application(application_no,business_type,title,applicant_user_id,applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,remark,submitted_at,completed_at,created_at)\n"
            "SELECT a.application_no,a.business_type,a.title,\n"
            "       @demo_population_user,@demo_population_department,'110105',p.person_id,\n"
            "       CASE WHEN a.business_type IN ('MIGRATION_IN','MIGRATION_OUT') THEN h.household_id ELSE NULL END,a.application_status,\n"
            "       '批量生成的虚构演示业务数据','DEMO-BULK',DATE_SUB(NOW(), INTERVAL a.created_offset DAY),\n"
            "       CASE WHEN a.application_status='COMPLETED' THEN DATE_SUB(NOW(), INTERVAL a.created_offset DAY) ELSE NULL END,DATE_SUB(NOW(), INTERVAL a.created_offset DAY)\n"
            "FROM demo_seed_application a JOIN demo_seed_person s ON s.seq=a.target_seq JOIN person p ON p.id_card=s.id_card\n"
            "LEFT JOIN demo_seed_household dh ON dh.household_no=CONCAT('DEMO-HH-BULK-',LPAD(FLOOR((a.target_seq-1)/3)+1,4,'0'))\n"
            "LEFT JOIN household h ON h.household_no=dh.household_no;\n\n"
            "INSERT INTO sys_approval_request(approval_no,application_id,status,current_approver_id,current_department_id,current_region_code,submitted_by,submitted_at,decided_by,decided_at,decision_comment)\n"
            "SELECT REPLACE(a.application_no,'DEMO-APP-','DEMO-APR-'),ba.application_id,a.approval_status,\n"
            "       CASE WHEN a.approval_status='PENDING' THEN @demo_approver_user ELSE NULL END,@demo_approval_department,'110105',@demo_population_user,\n"
            "       DATE_SUB(NOW(), INTERVAL a.created_offset DAY),CASE WHEN a.approval_status='PENDING' THEN NULL ELSE @demo_approver_user END,\n"
            "       CASE WHEN a.approval_status='PENDING' THEN NULL ELSE DATE_SUB(NOW(), INTERVAL a.created_offset DAY) END,'批量演示审批记录'\n"
            "FROM demo_seed_application a JOIN business_application ba ON ba.application_no=a.application_no;\n\n"
            "INSERT INTO residence_permit(permit_no,person_id,floating_id,source_application_id,issue_region_code,issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,status,current_flag)\n"
            "SELECT CONCAT('DEMO-PERMIT-',LPAD(s.seq,4,'0')),p.person_id,f.floating_id,ba.application_id,'110105',@demo_population_department,'示例市公安局',\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),\n"
            "       CASE WHEN s.seq <= 795 THEN DATE_ADD(CURRENT_DATE, INTERVAL (s.seq % 30 + 1) DAY) ELSE DATE_ADD(CURRENT_DATE, INTERVAL (s.seq % 300 + 60) DAY) END,\n"
            "       'ACTIVE',1\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN floating_population f ON f.registration_no=CONCAT('DEMO-FLOAT-',LPAD(s.seq,4,'0'))\n"
            "JOIN business_application ba ON ba.application_no=CONCAT('DEMO-APP-PERMIT-',LPAD(s.seq-720,4,'0'))\n"
            "WHERE s.seq BETWEEN 721 AND 1070;\n\n"
            "INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,to_address_snapshot,in_date,reason,transfer_batch_no,business_status,operator_id,executed_at)\n"
            "SELECT ba.application_id,p.person_id,'HOUSEHOLD_TRANSFER','120000',CONCAT('外地迁入地址',s.seq),h.region_code,h.household_id,h.address,\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),'批量演示迁入',CONCAT('DEMO-IN-',LPAD(s.seq,4,'0')), 'COMPLETED',@demo_population_user,DATE_SUB(NOW(), INTERVAL (s.seq % 30) DAY)\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN business_application ba ON ba.application_no=CONCAT('DEMO-APP-MIGRATION_IN-',LPAD(s.seq,4,'0'))\n"
            "JOIN residence r ON r.person_id=p.person_id JOIN household h ON h.household_id=r.household_id WHERE s.seq BETWEEN 1 AND 90;\n\n"
            "INSERT INTO migration_out(application_id,person_id,migration_type,from_region_code,from_household_id,from_address_snapshot,to_region_code,to_address,out_date,reason,transfer_batch_no,business_status,operator_id,executed_at)\n"
            "SELECT ba.application_id,p.person_id,'HOUSEHOLD_TRANSFER',h.region_code,h.household_id,h.address,'120000',CONCAT('外地迁出地址',s.seq),\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 30) DAY),'批量演示迁出',CONCAT('DEMO-OUT-',LPAD(s.seq,4,'0')), 'COMPLETED',@demo_population_user,DATE_SUB(NOW(), INTERVAL (s.seq % 30) DAY)\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN business_application ba ON ba.application_no=CONCAT('DEMO-APP-MIGRATION_OUT-',LPAD(s.seq-90,4,'0'))\n"
            "JOIN residence r ON r.person_id=p.person_id JOIN household h ON h.household_id=r.household_id WHERE s.seq BETWEEN 91 AND 170;\n\n"
            "INSERT INTO key_population(person_id,key_type,management_level,register_reason,register_date,responsible_department_id,responsible_user_id,status,remark)\n"
            "SELECT p.person_id,CASE WHEN MOD(s.seq,3)=0 THEN 'SPECIAL_CARE' ELSE 'OTHER' END,'MEDIUM','批量生成的重点人口演示记录',\n"
            "       DATE_SUB(CURRENT_DATE, INTERVAL (s.seq % 180) DAY),@demo_population_department,@demo_population_user,'ACTIVE','DEMO-BULK'\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card WHERE s.seq BETWEEN 1 AND 90;\n\n"
            "INSERT INTO cancellation_record(cancellation_no,application_id,cancel_object_type,person_id,source_household_id,cancel_reason_code,cancel_reason_detail,event_date,business_status,person_name_snapshot,identity_no_snapshot,address_snapshot,region_code_snapshot,operator_id)\n"
            "SELECT CONCAT('DEMO-CANCEL-',LPAD(s.seq-170,4,'0')),ba.application_id,'PERSON',p.person_id,h.household_id,'OTHER_APPROVED','批量生成的待办注销演示',CURRENT_DATE,\n"
            "       'UNDER_REVIEW',p.name,p.id_card,h.address,h.region_code,@demo_population_user\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN business_application ba ON ba.application_no=CONCAT('DEMO-APP-CANCELLATION-',LPAD(s.seq-170,4,'0'))\n"
            "JOIN residence r ON r.person_id=p.person_id JOIN household h ON h.household_id=r.household_id WHERE s.seq BETWEEN 171 AND 210;\n\n"
            "INSERT INTO data_export_request(application_id,export_module,export_scope,filter_snapshot,requested_fields,requested_format,reason,expected_row_limit,business_status,operator_id)\n"
            "SELECT ba.application_id,'POPULATION','REGION','{\\\"regionCode\\\":\\\"110105\\\"}','name,id_card,address','XLSX','批量生成的待办敏感导出演示',500,'UNDER_REVIEW',@demo_population_user\n"
            "FROM demo_seed_person s JOIN person p ON p.id_card=s.id_card\n"
            "JOIN business_application ba ON ba.application_no=CONCAT('DEMO-APP-DATA_EXPORT-',LPAD(s.seq-210,4,'0')) WHERE s.seq BETWEEN 211 AND 235;\n\n"
        )
        write_demo_materials_and_logs(handle)
        handle.write("COMMIT;\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成可导入 MySQL 8 的大屏批量演示数据 SQL")
    parser.add_argument("--database", default="population_miniterm_demo", help="目标数据库名")
    parser.add_argument("--output", type=Path, required=True, help="生成 SQL 的输出路径")
    parser.add_argument("--repair-output", type=Path, help="生成对旧版演示库可重复执行的补全 SQL")
    parser.add_argument("--people", type=int, default=1200, help="虚构人员数量，默认 1200")
    args = parser.parse_args()
    if not DATABASE_NAME.fullmatch(args.database):
        parser.error("--database 只能包含字母、数字和下划线")
    if args.people < 1070:
        parser.error("--people 至少为 1070")
    generate(args.database, args.output, args.people)
    if args.repair_output:
        write_demo_repair_sql(args.database, args.repair_output)
    print(f"已生成 {args.output}：{args.people} 名虚构人员，目标库 {args.database}")


if __name__ == "__main__":
    main()
