#!/usr/bin/env bash
# CyberShield launcher (Linux / macOS). Usage: ./start.sh [port]
set -e
cd "$(dirname "$0")"
PORT="${1:-${CYBERSHIELD_PORT:-8080}}"

command -v java >/dev/null 2>&1 || { echo "Java 21+ is required. Install a JDK 21 and retry."; exit 1; }
JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
[ "${JV:-0}" -ge 21 ] || { echo "Java 21+ is required (found $JV)."; exit 1; }

if command -v mvn >/dev/null 2>&1; then
  echo ">> Building with Maven (tests included)..."
  mvn -q -DskipTests=false package
  exec java -jar target/cybershield.jar "$PORT"
else
  echo ">> Maven not found - compiling with javac (build.sh)."
  ./build.sh
  exec java -cp build/classes com.cybershield.CyberShieldApp "$PORT"
fi
