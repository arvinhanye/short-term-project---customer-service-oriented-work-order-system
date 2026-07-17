#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /absolute/path/to/backup.sql" >&2
  exit 2
fi

backup=$1
test -f "$backup"
: "${TICKET_DRILL_MYSQL_USER:?set TICKET_DRILL_MYSQL_USER}"
: "${TICKET_DRILL_MYSQL_PASSWORD:?set TICKET_DRILL_MYSQL_PASSWORD}"

database="ticket_restore_drill_$(date +%Y%m%d_%H%M%S)"
export MYSQL_PWD=$TICKET_DRILL_MYSQL_PASSWORD
mysql -u "$TICKET_DRILL_MYSQL_USER" -e "CREATE DATABASE \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
cleanup() {
  mysql -u "$TICKET_DRILL_MYSQL_USER" -e "DROP DATABASE IF EXISTS \`$database\`"
}
trap cleanup EXIT INT TERM
mysql -u "$TICKET_DRILL_MYSQL_USER" "$database" < "$backup"
mysql -u "$TICKET_DRILL_MYSQL_USER" -N "$database" -e "SELECT CONCAT('orders=', COUNT(*)) FROM orders"
echo "Restore drill passed: $database"
