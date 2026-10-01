#!/bin/sh
# Usage (macOS / Linux):  sh run.sh server | admin | employee | dashboard | checks
cd "$(dirname "$0")" || exit 1
[ -d out ] || { echo "Not built yet - run: sh build.sh"; exit 1; }
CP="out:lib/*"
case "$1" in
  server)    java -cp "$CP" dbmonitor.server.ServerMain ;;
  admin)     java -cp "$CP" dbmonitor.admin.AdminMain ;;
  employee)  java -cp "$CP" dbmonitor.employee.EmployeeMain ;;
  dashboard) java -cp "$CP" ConsoleDashboard ;;
  checks)    java -cp "$CP" Phase12Check; echo; java -cp "$CP" DaoCheck; echo
             java -cp "$CP" RuleCheck; echo; java -cp "$CP" DetectionCheck ;;  # server STOPPED
  *)         echo "Usage: sh run.sh server | admin | employee | dashboard | checks" ;;
esac
