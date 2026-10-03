#!/bin/sh
# Builds everything into out/  (macOS / Linux:  sh build.sh)
cd "$(dirname "$0")" || exit 1

# Completely wipe and cleanly recreate the out folder
rm -rf out
mkdir out

# Compile only existing source directories to avoid wildcard matching errors on empty folders
echo "Compiling project files..."
javac -encoding US-ASCII -d out -cp "lib/*" \
      src/dbmonitor/common/*.java \
      src/dbmonitor/db/*.java \
      src/dbmonitor/rules/*.java \
      src/dbmonitor/server/*.java \
      src/dbmonitor/admin/*.java \
      src/dbmonitor/employee/*.java \
      test/*.java || { echo "BUILD FAILED"; exit 1; }

echo "BUILD OK"