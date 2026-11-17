#!/bin/bash
# Fresh, disposable Compose lab only. Does not run during normal application startup.
set -euo pipefail
export MYSQL_PWD
if [[ ! "${REPLICATION_PASSWORD:-}" =~ ^[A-Za-z0-9_-]{20,32}$ ]]; then
  echo 'REPLICATION_PASSWORD must use 20-32 alphanumeric/underscore/hyphen characters.' >&2
  exit 1
fi
primary() { mysql --host=mysql-primary --user=root --default-character-set=utf8mb4 --batch --skip-column-names "$@"; }
replica() { mysql --host=mysql-replica --user=root --default-character-set=utf8mb4 --batch --skip-column-names "$@"; }
for node in mysql-primary mysql-replica; do
  tables=$(mysql --host="$node" --user=root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='population_ops'")
  if [[ "$tables" != 0 ]]; then
    echo "Refusing to bootstrap nonempty population_ops on $node. No reset is performed." >&2
    exit 1
  fi
done
if [[ -n "$(replica --execute='SHOW REPLICA STATUS')" ]]; then
  echo 'Refusing an already configured replica.' >&2
  exit 1
fi

# The snapshot includes V4_001 through V4_010 and V4_014 initial person ownership.
sed 's/population_miniterm/population_ops/g' /schema/population_miniterm.sql | primary
for migration in /schema/migrations/V4_*.sql; do
  name=$(basename "$migration")
  if [[ "$name" < V4_011 || "$name" == V4_014_person_initial_scope.sql ]]; then continue; fi
  sed 's/population_miniterm/population_ops/g' "$migration" | primary population_ops
done
primary population_ops < /schema/activate_jwt_revocation_checkpoint.sql
primary --execute="CREATE USER 'replicator'@'%' IDENTIFIED BY '$REPLICATION_PASSWORD'; GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';"

# Application processes must remain stopped while this initial snapshot is created.
# GTID_PURGED is included; the target was checked empty and has no replication channel.
mysqldump --host=mysql-primary --user=root --single-transaction --set-gtid-purged=ON \
  --routines --triggers --databases population_ops | replica
replica --execute="CHANGE REPLICATION SOURCE TO SOURCE_HOST='mysql-primary', SOURCE_USER='replicator', SOURCE_PASSWORD='$REPLICATION_PASSWORD', SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1; START REPLICA;"
# Generate a transaction after START REPLICA; waiting only for the dump's GTIDs
# would not prove that live replication is working.
primary population_ops --execute='CREATE TABLE IF NOT EXISTS ops_replication_bootstrap_probe(id INT PRIMARY KEY, created_at TIMESTAMP(6)); INSERT INTO ops_replication_bootstrap_probe VALUES(1,NOW(6));'
gtid=$(primary --execute='SELECT @@GLOBAL.gtid_executed')
waited=$(replica --execute="SELECT WAIT_FOR_EXECUTED_GTID_SET('$gtid',30)")
if [[ "$waited" != 0 ]]; then echo 'Replica did not catch up within 30 seconds.' >&2; exit 1; fi
replica --execute='SET PERSIST read_only=ON; SET PERSIST super_read_only=ON;'
echo '[PASS] Fresh schema and GTID replication initialized; replica caught up and is read-only.'
# Re-enable labels explicitly: --skip-column-names also suppresses the labels
# needed by the status filter on some mysql client versions.
replica --column-names --vertical --execute='SHOW REPLICA STATUS' | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Last_IO_Error:|Last_SQL_Error:'
