#!/usr/bin/env bash
# Maven-free build: compiles the sources into build/classes (needs a JDK 21, not just a JRE).
set -e
cd "$(dirname "$0")"
command -v javac >/dev/null 2>&1 || { echo "javac not found - install a JDK 21."; exit 1; }
rm -rf build && mkdir -p build/classes
javac -encoding UTF-8 --release 21 -d build/classes $(find src/main/java -name '*.java')
mkdir -p build/classes/dashboard build/classes/datasets
cp -r dashboard/. build/classes/dashboard/
cp -r datasets/. build/classes/datasets/
echo ">> Build OK: build/classes"
