#/bin/bash
./mvnw clean
./mvnw package
cp target/decompile-java-1.0-SNAPSHOT.jar vsix_build/server/decompile-java.jar
cd vsix_build
vsce package
