# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

`zero-bounce-java-sdk-setup` is the ZeroBounce Java SDK plus a small JavaFX sample. The library that ships is `zero-bounce-sdk` (`com.zerobounce.java:zerobouncesdk` on Maven Central). Callers construct a `ZeroBounceSDK` or use `getInstance()`, call `initialize`, and receive results through success and error callbacks.

## Layout

- `zero-bounce-sdk/` is the published Maven module. `src/` at the repo root is only the sample app.
- `zero-bounce-sdk/src/main/java/com/zerobounce/ZeroBounceSDK.java`: public client; holds every request. `sendRequest` is the original GET/JSON-POST helper; `sendRequestWithMethod` also does form-encoded POSTs and the Inbox Placement Tester's PUT and DELETE.
- `zero-bounce-sdk/src/main/java/com/zerobounce/Priority3Api.java`: the multipart uploads and bulk-root file helpers for the bulk email finder, domain search, and list evaluator. The filters and the S3 jobs are in `ZeroBounceSDK` itself.
- `ZBValidateOptions`, `ZBBulkFileJobOptions`, `ZBValidateBatchOptions`, `ZBValidateRequestOptions`: the option objects, each with a package-private `fill` that both builds the request and rejects an invalid combination before anything is sent.
- `zero-bounce-sdk/src/main/java/com/zerobounce/ZeroBounceWarmup.java`: a separate client for the Warmup API, a different product with its own host and its own team-scoped key. Not part of `ZeroBounceSDK`.
- `zero-bounce-sdk/src/main/java/com/zerobounce/HttpMessages.java`: turns HTTP failures, HTML pages, and empty or non-JSON bodies into a short `ErrorResponse`.
- `zero-bounce-sdk/src/main/java/com/zerobounce/SdkHttp.java`: per-client HTTP seam. Production leaves `transport` null.
- `zero-bounce-sdk/src/test/java/com/zerobounce/OfflineApiTest.java`, `OfflineWarmupTest.java`: the offline suites. `FakeApi` (and a mocked `HttpURLConnection`) answer every call and record the url, method, headers and body, so the assertions are on what actually went on the wire.
- `live/LiveApi.java`: the opt-in live suite. It sits outside the Maven module's source root, so `mvn test` and CI never compile or run it.
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

If there is no `mvn` on the host (`mvnw` has no wrapper jar committed), Maven runs in Docker:

```sh
cd ../sdk-docs && docker compose run --rm --build java
```

That build runs `mvn -q test` and fails the build if a test fails — but the container's `CMD` then fails on `/root/.m2` permissions, which is not a test failure. To see the test output, or to run against the working tree without rebuilding:

```sh
docker run --rm -u 0 -v "$PWD":/src -w /src/zero-bounce-sdk zerobounce-sdk-java:test mvn -B test
```

The live suite is compiled and run by hand, never by Maven:

```sh
mvn -f zero-bounce-sdk/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="zero-bounce-sdk/target/classes:$(cat /tmp/cp.txt)"
javac -cp "$CP" -d live/out live/LiveApi.java
export ZEROBOUNCE_API_KEY=... ZEROBOUNCE_WARMUP_API_KEY=...
java -cp "$CP:live/out" com.zerobounce.live.LiveApi          # free calls only
ZEROBOUNCE_LIVE_SPEND=1 java -cp "$CP:live/out" com.zerobounce.live.LiveApi
ZEROBOUNCE_LIVE_CHANGES=1 java -cp "$CP:live/out" com.zerobounce.live.LiveApi
```

Keys come from the environment only. Never write one into a file, a commit, a log, a fixture or a message.

## Conventions

- **Only endpoints documented at https://www.zerobounce.net/docs/.** Do not add a method for an endpoint the public docs do not describe, even if it exists on the server. Check before concluding something is undocumented: the S3 and SFTP bulk endpoints live under https://www.zerobounce.net/docs/api-integrations, not the main API reference.
- **Where the live API and the docs disagree, follow the API and say so in a comment at the call site.** The ones found so far: `validatebatch` takes `timeout` as a JSON *string*; the Inbox Placement Tester authenticates with `Authorization: Bearer`, not `api_key`; its listing is `GET /tests`, not `/test`, and honours `page_size`, ignoring `pageSize`; `send-evaluated-file` takes `file_id` as a form field, not a file upload; the S3 and SFTP jobs require `return_url` and `file_name`, `validateSFTPFile` included; `GET /filters/list` answers with a JSON *array*; the Warmup `/seed-count` answers with a bare number and `check-domain-dmarc` takes an email address, not a domain; and a failed SMTP login in `test-smtp-configuration` comes back as an HTTP 500.
- **Four base URLs.** `apiBaseUrl`, `bulkApiBaseUrl` (ends in `/v2`), `bulkApiRootUrl` (the S3/SFTP jobs, **no** `/v2`), and `listEvaluatorBaseUrl` / `inboxPlacementBaseUrl`. The last two are separate hosts rather than paths built from the bulk base, so `initialize(apiKey, apiBaseUrl)` alone leaves them on the live API — use the four-argument `initialize` in tests. The list evaluator upload and its result both read `listEvaluatorBaseUrl`; do not rebuild either from `bulkApiBaseUrl`, which would split the pair across two hosts.
- **One `ZeroBounceSDK` per API key.** `getInstance()` is a process-wide singleton. Do not reuse it across users or requests. Construct `new ZeroBounceSDK()` per key. The offline concurrency test does this. Do not share one instance across threads: `apiKey` is mutable and `SimpleDateFormat` is not thread-safe.
- **The SDK version is `<sdk.version>`** in the parent pom. Both poms read it; bumping one alone breaks the release. Never edit the first `<version>` tag in `zero-bounce-sdk/pom.xml`; that tag is `${sdk.version}`. Plugin and dependency versions must not be copied from `<sdk.version>`.
- **Column indexes are 1-based for file uploads.** `sendFile`'s email column must be at least 1. Optional name, gender, and IP columns default to `0`, and `0` means the field is left out. `ValidateS3FileOptions.emailAddressColumn` is an `Integer`: when it is set, `0` is sent.
- **Optional parameters are omitted when unset.** `timeout`, `activityData`, `verifyPlus`, `allowPhase2`, `downloadType`, and `hasHeaderRow` are not sent until the caller sets them. Explicit `false` is sent as `false`.
- **The two timeouts are not the same parameter.** Single `validate` takes 3 to 60 seconds and is rejected before the request when it is outside that, `validateWithOptions` included: that one is a wrapper over `validate`, not a second implementation. Batch `validatebatch` takes 10 to 120, is rejected the same way, and goes on the wire as a JSON *string* — a number there is answered with the account-level error.
- **`allow_phase_2` is validation `sendFile` and `validateS3File` only.** Scoring uploads ignore it. `activity_data` on get-file is validation only.
- **`validateBatch` uses `api.zerobounce.net`**, not `bulkapi.zerobounce.net`.
- **`exportSFTPFile` sends `export_path`**, not the `export_bucket` that `exportS3File` sends.
- **Booleans are the strings `true` and `false` on form and multipart fields, and JSON booleans in a JSON body.**
- **`{"success": false}` on an HTTP 200 is a failure.** The API answers an unknown bulk file id that way. `apiSuccessIsFalse` turns it into an error on every request path.
- **A response is not always a JSON object.** Read into a `JsonElement` when it may be an array or a scalar, and remember Gson parses leniently: junk reads as a string instead of raising, so the shape is checked before parsing.
- **Failures use the error callback**, not thrown server errors. `ZBException` is only for invalid arguments such as an email column below 1. The message must be short: no raw HTML, no megabyte body, and no `Error: Error:` prefix. An account-level `validateBatch` error (`email_address` `all`) is an error callback. A per-address error stays on the success result.
- **Errors carry the HTTP status.** `ErrorResponse.getStatusCode()` is set on every path, so callers can tell a Cloudflare 403 from an API 400/404 and a Warmup 429. A failure that never reached the API leaves it 0.
- **`ErrorResponse.parseError` already handles `detail`.** It collects every value of the error JSON, so the Inbox Placement Tester's Django-style `{"detail": ...}` surfaces without a special case. A test pins that.
- **Offline tests only, in the Maven module.** Never call the live API from a test under `src/test`. The live suite is the separate, opt-in `live/LiveApi.java`. Tests replace `transport`: it is package-visible, so assign a `FakeApi` (or another `SdkHttp.Transport`) on that instance.
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

**Run the live suite by hand before every release.** The offline suite proves the SDK sends what we think it sends; only the live suite proves the API accepts it. Run it with real keys, including `ZEROBOUNCE_LIVE_SPEND=1` and `ZEROBOUNCE_LIVE_CHANGES=1`, and do not release on a failure.

1. Move `[Unreleased]` entries under `## [x.y.z] - YYYY-MM-DD`, leave an empty `[Unreleased]` section, and update the compare links at the bottom of `CHANGELOG.md`.
2. Set the same version in `<sdk.version>` in `zero-bounce-sdk/pom.xml`. Do not edit the first `<version>` tag. The sample `pom.xml` dependency on `com.zerobounce.java:zerobouncesdk` is a separate pin; update it only when the sample should depend on the new release.
3. `mvn -B -DskipTests compile` and `mvn -B test` in `zero-bounce-sdk`.
4. Tag `vx.y.z` on that commit and run the Publish workflow with that tag. It deploys to Maven Central and creates the GitHub release.
