#/bin/bash
set -e
./mvnw clean
./mvnw package
cp target/decompile-java-1.0-SNAPSHOT.jar vsix_build/server/decompile-java.jar
cd vsix_build
rm -rf decompile-java-*.vsix
npm install
npm run compile
npx @vscode/vsce package
