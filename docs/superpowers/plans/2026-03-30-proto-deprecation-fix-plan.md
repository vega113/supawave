# Proto & Java Deprecation Warning Fix Plan

## Audit Results

### 1. Proto Unused Imports (1 warning)
- `diff.proto:25` — `import "org/waveprotocol/box/server/rpc/rpc.proto"` is unused
- **Fix**: Remove the import line
- **Risk**: None — unused import has no effect on generated code

### 2. Protobuf PARSER Field Deprecation (12 files, 31 usages)
Files: `Diff.java`, `WaveClientRpc.java`, `ProtoDeltaStoreData.java`, `ClientServer.java`, `Proto.java`, etc.
- **Root cause**: protobuf 3.x deprecated the `PARSER` static field in favor of `parser()` method
- **Fix**: Replace `.PARSER` with `.parser()` in all `input.readMessage()` calls across proto_src/
- **Risk**: None — `parser()` returns the same parser instance

### 3. Protobuf valueOf(int) Enum Deprecation (3 warnings in gen/messages)
- `ResponseStatusProtoImpl.java:282` — `ResponseCode.valueOf(value.getValue())`
- `ProtocolSignatureProtoImpl.java:317` — `SignatureAlgorithm.valueOf(value.getValue())`
- `ProtocolSignerInfoProtoImpl.java:356` — `HashAlgorithm.valueOf(value.getValue())`
- **Fix**: Replace `valueOf(int)` with `forNumber(int)`
- **Risk**: None — `forNumber` is the non-deprecated replacement

### 4. JsonParser Deprecation (4 files, 7 usages)
- `WebSocketChannel.java:53,66` — `new JsonParser()` / `.parse()`
- `RobotSerializer.java:97,170,193` — `new JsonParser()` / `.parse()`
- `GsonUtil.java:77` — `new JsonParser().parse()`
- **Fix**: Replace with `JsonParser.parseString()` static method (Gson 2.8.6+, we have 2.10.1)
- **Risk**: None — static method is functionally identical

### 5. Class.newInstance() Deprecation (1 warning)
- `ProtoSerializer.java:108` — `dtoClass.newInstance()`
- **Fix**: Replace with `dtoClass.getDeclaredConstructor().newInstance()`
- **Risk**: None — same behavior, adds `NoSuchMethodException` to catch chain

### 6. Commons CLI Deprecation (2 files, ~8 usages)
- `AbstractCliRobotAgent.java` — `HelpFormatter`, `PosixParser`, `OptionBuilder`
- `PstCommandLine.java:100` — `HelpFormatter`
- **Fix**: `PosixParser` → `DefaultParser`, `OptionBuilder` → `Option.builder()`, `HelpFormatter` → `HelpFormatter.builder().get()` (commons-cli 1.11.0)
- **Risk**: Low — API replacements are documented

### 7. Commons Lang3 StringEscapeUtils Deprecation (1 file, 2 usages)
- `FolderServlet.java:106,125` — `StringEscapeUtils.unescapeHtml4()`
- **Fix**: Add `commons-text` dependency, use `org.apache.commons.text.StringEscapeUtils`
- **Risk**: Low — commons-text is the official replacement

### 8. Internal API Deprecation (2 warnings, intentional)
- `AbstractLogger.java:182` — calls deprecated `logXml()` (implements interface method)
- `WaveletBasedConversation.java:71` — calls deprecated `peekBlipId()` (see TODO comment)
- **Fix**: Add `@SuppressWarnings("deprecation")` — these are intentional uses with known TODOs
- **Risk**: None

### 9. Unchecked Generic Array Creation (ApiDocsServlet, 12 warnings)
- `ApiDocsServlet.java` — `list(Map<String, Object>...)` varargs calls
- **Fix**: Add `@SafeVarargs` to the `list()` helper method
- **Risk**: None — method is `private static`, safe for @SafeVarargs

## CI Gate

Add `-Xlint:deprecation` and `-Xlint:unchecked` to `javacOptions` in `build.sbt`, plus `-Werror` to convert warnings to errors. This prevents future deprecation warnings from being introduced.

## Test Strategy
- Compile all subprojects: `sbt compile compileGwt`
- Verify zero warnings in output
- Existing tests cover behavioral correctness
