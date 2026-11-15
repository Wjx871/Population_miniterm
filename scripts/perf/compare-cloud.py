"""Controlled experiments on the disposable cloud lab, never a developer database."""
import importlib.util
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import time


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


stack = module("cloud_stack", "scripts/ops/verify-cloud-stack.py")
load = module("read_load", "scripts/perf/read-load.py")
OUT = stack.OUT / "comparison"


def seed():
    # Explicitly synthetic, deliberately invalid identity numbers. No real PII.
    assert os.environ.get("GITHUB_ACTIONS") == "true"
    assert os.environ["OPS_PROJECT_NAME"].startswith("population-cloud-")
    assert stack.sql("mysql-primary", "SELECT COUNT(*) FROM person WHERE id_card LIKE 'OPS%';") == "0"
    for start in range(0, 20000, 1000):
        rows = [f"('Synthetic {i}','M','OPS{i:015d}','1990-01-01','cloud fixture')" for i in range(start + 1, start + 1001)]
        stack.sql("mysql-primary", "INSERT INTO person(name,gender,id_card,birth_date,current_address) VALUES " + ",".join(rows) + ";")
    stack.sql("mysql-primary", """INSERT INTO floating_population(registration_no,person_id,source_region_code,current_region_code,
        current_address,residence_reason_code,residence_proof_type,arrival_date,registration_date,status,current_flag,remark)
        SELECT CONCAT('OPS-',id_card),person_id,'110000','110105','synthetic cloud address','EMPLOYMENT','LEASE',
        CURRENT_DATE,CURRENT_DATE,IF(MOD(person_id,4)=0,'ACTIVE','CLOSED'),IF(MOD(person_id,4)=0,1,NULL),'cloud-only load fixture'
        FROM person WHERE id_card LIKE 'OPS%';""")
    stack.sql("mysql-primary", "ANALYZE TABLE floating_population;")
    sync()
    return {"synthetic_people": 20000, "synthetic_floating_rows": 20000,
            "total_people": int(stack.sql("mysql-primary", "SELECT COUNT(*) FROM person;")),
            "active_floating": int(stack.sql("mysql-primary", "SELECT COUNT(*) FROM floating_population WHERE status='ACTIVE' AND current_flag=1;"))}


def sync():
    gtid = stack.sql("mysql-primary", "SELECT @@GLOBAL.gtid_executed;")
    assert stack.sql("mysql-replica", f"SELECT WAIT_FOR_EXECUTED_GTID_SET('{gtid}',30);") == "0"


def index_experiment():
    query = "SELECT COUNT(*) FROM floating_population f WHERE f.status='ACTIVE' AND f.current_flag=1"
    migration = Path("doc/database/migrations/V4_015_floating_statistics_index.sql").read_text()
    for _ in range(2):
        stack.sql("mysql-primary", migration)
    definition = stack.sql("mysql-primary", "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema='population_ops' AND table_name='floating_population' AND index_name='idx_floating_status_current';")
    assert definition == "status,current_flag", definition
    result = {"query": query, "index": "idx_floating_status_current(status,current_flag)", "scope": "disposable_lab_only",
              "migration_reapplications": 2, "comparison": "same maintained index, optimizer visibility OFF versus ON"}
    stack.sql("mysql-primary", "SET GLOBAL log_output='TABLE'; SET GLOBAL slow_query_log=ON;")
    try:
        for stage in ("before", "after"):
            visibility = "INVISIBLE" if stage == "before" else "VISIBLE"
            stack.sql("mysql-primary", f"ALTER TABLE floating_population ALTER INDEX idx_floating_status_current {visibility}; ANALYZE TABLE floating_population;")
            # Retain engine-measured iterator timing, avoiding docker/client setup in SQL timings.
            plans = [stack.sql("mysql-primary", "SET SESSION long_query_time=0; EXPLAIN ANALYZE " + query + ";") for _ in range(5)]
            for _ in range(5):
                stack.sql("mysql-primary", "SET SESSION long_query_time=0; " + query + ";")
            result[stage] = {"explain_analyze": plans, "count": int(stack.sql("mysql-primary", query + ";"))}
        assert result["before"]["count"] == result["after"]["count"]
        result["slow_log_matches"] = int(stack.sql("mysql-primary", "SELECT COUNT(*) FROM mysql.slow_log WHERE sql_text LIKE 'SELECT COUNT(*) FROM floating_population f WHERE%';"))
        assert result["slow_log_matches"] >= 10
    finally:
        stack.sql("mysql-primary", "ALTER TABLE floating_population ALTER INDEX idx_floating_status_current VISIBLE;")
        stack.sql("mysql-primary", "SET GLOBAL slow_query_log=OFF;")
    sync()
    (OUT / "index-experiment.json").write_text(json.dumps(result, indent=2))


def snapshot(token):
    values = {node: json.loads(stack.internal(node, "/api/ops/runtime", token)[1])["data"] for node in ("backend", "backend2")}
    values["cache"] = stack.cache_counts(token)
    values["routing"] = stack.routes(token)
    ids = stack.command("ps", "-q").decode().split()
    result = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids], capture_output=True, check=True)
    values["docker_stats"] = [json.loads(line) for line in result.stdout.decode().splitlines() if line]
    return values


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    dataset = seed()
    index_experiment()
    scenarios = [("single_primary_cache_off", False, False, False),
                 ("single_primary_cache_on", False, False, True),
                 ("single_replica_cache_off", False, True, False),
                 ("dual_primary_cache_off", True, False, False)]
    # Counterbalance order between rounds to reduce warm-cache/order bias.
    schedule = [scenarios, list(reversed(scenarios)), [scenarios[i] for i in (2, 0, 3, 1)]]
    output = {"dataset": dataset, "method": {"rounds": 3, "seconds_per_trial": 12, "concurrency": 8,
              "warmup_requests": 40, "load": "closed_loop", "background": "both JVMs and both MySQL nodes remain running in all cases",
              "single_definition": "Nginx routes only to backend-1; backend-2 remains idle", "index_schema": "V4_015 index visible and identical in every HTTP trial",
              "limits": "Shared GitHub runner; synthetic data; no production capacity or write workload claim. Hardware is recorded in environment.json."}, "trials": []}
    override = OUT / "scenario.json"
    base_dc = list(stack.DC)
    config_file = OUT / "scenario-nginx.conf"
    expected = None
    try:
        for round_number, order in enumerate(schedule, 1):
            for name, dual, replica, cache in order:
                print("[COMPARE]", round_number, name, flush=True)
                override.write_text(json.dumps({"services": {node: {"environment": {"REDIS_ENABLED": str(cache).lower(), "REPLICA_ENABLED": str(replica).lower(), "SCHEDULING_ENABLED": "false"}} for node in ("backend", "backend2")}}))
                # Compose options must precede the command; insert before --profile.
                stack.DC = base_dc[:-2] + ["-f", str(override)] + base_dc[-2:]
                stack.command("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "backend", "backend2")
                stack.wait_backend("backend"); stack.wait_backend("backend2")
                config_file.write_text(Path("config/nginx/population.dual.conf" if dual else "config/nginx/population.container.conf").read_text())
                stack.command("cp", str(config_file), "web:/etc/nginx/conf.d/default.conf")
                # Fresh process resolves recreated backend addresses, clears upstream failure state.
                stack.command("restart", "web")
                time.sleep(2)
                token = stack.login()
                stack.flush_cache()
                data, _ = stack.api("/api/statistics/overview", token)
                assert data["activeFloatingPopulation"] == dataset["active_floating"]
                digest = load.data_digest(data)
                if expected is None:
                    expected = digest
                assert digest == expected, "Business results differ across configurations"
                before = snapshot(token)
                destination = OUT / f"{round_number}-{name}.json"
                env = dict(os.environ, OPS_BEARER_TOKEN=token)
                subprocess.run([sys.executable, "scripts/perf/read-load.py", "--url", stack.BASE + "/api/statistics/overview",
                    "--duration", "12", "--concurrency", "8", "--warmup", "40", "--expected-data-sha256", expected,
                    "--output", str(destination)], check=True, env=env, stdout=subprocess.DEVNULL)
                after = snapshot(token)
                trial = json.loads(destination.read_text())
                assert set(trial["instances"]) == ({"backend-1", "backend-2"} if dual else {"backend-1"})
                before_routes, after_routes = before["routing"], after["routing"]
                if replica:
                    assert sum(x["replicaConnections"] for x in after_routes.values()) > sum(x["replicaConnections"] for x in before_routes.values())
                else:
                    assert sum(x["replicaConnections"] for x in after_routes.values()) == 0
                if cache:
                    assert sum(x["hits"] for x in after["cache"].values()) > sum(x["hits"] for x in before["cache"].values())
                output["trials"].append({"round": round_number, "scenario": name, "metrics": {key: val for key, val in trial.items() if key != "samples"}, "before": before, "after": after})
                (OUT / "comparison.json").write_text(json.dumps(output, indent=2))
        output["medians"] = {name: {metric: round(statistics.median(t["metrics"][metric] for t in output["trials"] if t["scenario"] == name), 3)
                                   for metric in ("p50_ms", "p95_ms", "successful_throughput_rps", "error_rate")} for name, *_ in scenarios}
        (OUT / "comparison.json").write_text(json.dumps(output, indent=2))
        print("[PASS] controlled comparison", json.dumps(output["medians"]), flush=True)
    finally:
        stack.DC = base_dc
        stack.command("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "backend", "backend2")
        stack.command("cp", "config/nginx/population.dual.conf", "web:/etc/nginx/conf.d/default.conf")
        stack.command("restart", "web")


if __name__ == "__main__":
    main()
