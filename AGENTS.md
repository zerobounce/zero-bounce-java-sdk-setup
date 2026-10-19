# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

`zero-bounce-java-sdk-setup` is the ZeroBounce Java SDK plus a small JavaFX sample. The library that ships is `zero-bounce-sdk` (`com.zerobounce.java:zerobouncesdk` on Maven Central). Callers construct a `ZeroBounceSDK` or use `getInstance()`, call `initialize`, and receive results through success and error callbacks.

## Layout

- `zero-bounce-sdk/src/main/java/com/zerobounce/ZeroBounceSDK.java`: public client.
- `zero-bounce-sdk/src/main/java/com/zerobounce/Priority3Api.java`: bulk email finder, domain search, list evaluator, filters, S3.
- `zero-bounce-sdk/src/main/java/com/zerobounce/HttpMessages.java`: turns HTTP failures, HTML pages, and empty or non-JSON bodies into a short `ErrorResponse`.
- `zero-bounce-sdk/src/main/java/com/zerobounce/SdkHttp.java`: per-client HTTP seam. Production leaves `transport` null.
- `zero-bounce-sdk/src/test/java/com/zerobounce/OfflineApiTest.java`: offline suite. `FakeApi` records every request and answers without the network.
- `zero-bounce-sdk/pom.xml`: SDK build. The version is `<sdk.version>` inside `<properties>`.
- `pom.xml`: the JavaFX sample. Its `<version>` is `1.0-SNAPSHOT` and is not the SDK version.
- `.github/workflows/sdk_ci.yml`: typecheck, `mvn test`, then `mvn clean install`.
- `.github/workflows/publish.yml`: manual Publish workflow. It deploys the tag to Maven Central.

## Commands

From `zero-bounce-sdk`:

```bash
mvn -B -DskipTests compile   # typecheck; there is no separate style linter
mvn -B test                  # unit tests, including OfflineApiTest
```

CI runs the same compile and `mvn test`. `mvn test` is the default test command.

## Conventions

- **One `ZeroBounceSDK` per API key.** `getInstance()` is a process-wide singleton. Do not reuse it across users or requests. Construct `new ZeroBounceSDK()` per key. The offline concurrency test does this. Do not share one instance across threads: `apiKey` is mutable and `SimpleDateFormat` is not thread-safe.
- **The SDK version is `<sdk.version>`.** Never edit the first `<version>` tag in `zero-bounce-sdk/pom.xml`; that tag is `${sdk.version}`. Plugin and dependency versions must not be copied from `<sdk.version>`.
- **Column indexes are 1-based for file uploads.** `sendFile`'s email column must be at least 1. Optional name, gender, and IP columns default to `0`, and `0` means the field is left out. `ValidateS3FileOptions.emailAddressColumn` is an `Integer`: when it is set, `0` is sent.
- **Optional parameters are omitted when unset.** `timeout`, `activityData`, `verifyPlus`, `allowPhase2`, `downloadType`, and `hasHeaderRow` are not sent until the caller sets them. Explicit `false` is sent as `false`. `timeout` `0` is sent.
- **`allow_phase_2` is validation `sendFile` and `validateS3File` only.** Scoring uploads ignore it. `activity_data` on get-file is validation only.
- **`validateBatch` uses `api.zerobounce.net`**, not `bulkapi.zerobounce.net`.
- **Booleans are the strings `true` and `false` on form and multipart fields, and JSON booleans in a JSON body.**
- **Failures use the error callback**, not thrown server errors. `ZBException` is only for invalid arguments such as an email column below 1. The message must be short: no raw HTML, no megabyte body, and no `Error: Error:` prefix. An account-level `validateBatch` error (`email_address` `all`) is an error callback. A per-address error stays on the success result.
- **Tests replace `transport`.** It is package-visible. Assign a `FakeApi` (or another `SdkHttp.Transport`) on that instance. Do not call the live API from the default test run.
- There is no runtime version constant and no User-Agent. Do not add one that can drift from `<sdk.version>`.
- Match the surrounding style. Do not reformat unrelated code.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

- Add every user-visible change to `## [Unreleased]` in the same commit as the change.
- Group entries under these headings, in this order, omitting empty ones: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.
- Write for SDK users: name the method, option, or behaviour, and say what changed. Leave out CI-only changes, internal refactors, and fixes to code that was never released.
- At 2.y.z, a breaking change bumps the major version, a new method or option bumps the minor version, and fixes alone bump the patch version.

## Releasing

Publishing and pushing tags need a maintainer's explicit approval. Do not bump the version, tag, or publish unless they asked.

1. Move `[Unreleased]` entries under `## [x.y.z] - YYYY-MM-DD`, leave an empty `[Unreleased]` section, and update the compare links at the bottom of `CHANGELOG.md`.
2. Set the same version in `<sdk.version>` in `zero-bounce-sdk/pom.xml`. Do not edit the first `<version>` tag. The sample `pom.xml` dependency on `com.zerobounce.java:zerobouncesdk` is a separate pin; update it only when the sample should depend on the new release.
3. `mvn -B -DskipTests compile` and `mvn -B test` in `zero-bounce-sdk`.
4. Tag `vx.y.z` on that commit and run the Publish workflow with that tag. It deploys to Maven Central and creates the GitHub release.
