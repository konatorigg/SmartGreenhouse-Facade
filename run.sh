#!/usr/bin/env sh
set -eu
mkdir -p out
find src/main/java -name '*.java' > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -cp out greenhouse.app.Main
