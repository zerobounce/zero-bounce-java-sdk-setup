# Agent notes — zero-bounce-java-sdk-setup

## Layout

- `zero-bounce-sdk/` is the published Maven module. `src/` at the repo root is only the sample app.
- `ZeroBounceSDK.java` holds every request. `sendRequest` is the original GET/JSON-POST helper; `sendRequestWithMethod` is the one that can also do form-encoded POSTs and the Inbox Placement Tester's PUT and DELETE.
- `ZBValidateOptions`, `ZBBulkFileJobOptions`: the option objects, each with a package-private `fill` that both builds the request and rejects an invalid combination before anything is sent.
- `ZeroBounceWarmup.java`: a separate client for the Warmup API, a different product with its own host and its own team-scoped key. Not part of `ZeroBounceSDK`.
- `OfflineApiTest.java`, `OfflineWarmupTest.java`: the offline suites. A mocked `HttpURLConnection` answers every call and records the url, method, headers and body, so the assertions are on what actually went on the wire.
- `live/LiveApi.java`: the opt-in live suite. It sits outside the Maven module's source root, so `mvn test` and CI never compile or run it.

## Build and test

Maven runs in Docker (there is no `mvn` on the host, and `mvnw` has no wrapper jar committed):

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

## Rules

- **Only endpoints documented at https://www.zerobounce.net/docs/.** Do not add a method for an endpoint the public docs do not describe, even if it exists on the server. Check before concluding something is undocumented: the S3 and SFTP bulk endpoints live under https://www.zerobounce.net/docs/api-integrations, not the main API reference.
- **Four base URLs.** `apiBaseUrl`, `bulkApiBaseUrl` (ends in `/v2`), `bulkApiRootUrl` (the S3/SFTP jobs, **no** `/v2`), and `listEvaluatorBaseUrl` / `inboxPlacementBaseUrl`. The last two are separate hosts rather than paths built from the bulk base, so `initialize(apiKey, apiBaseUrl)` alone leaves them on the live API — use the four-argument `initialize` in tests.
- **Where the live API and the docs disagree, follow the API and say so in a comment at the call site.** The ones found so far: `validatebatch` takes `timeout` as a JSON *string*; the Inbox Placement Tester authenticates with `Authorization: Bearer`, not `api_key`; its listing is `GET /tests`, not `/test`; `send-evaluated-file` takes `file_id` as a form field, not a file upload; the S3 and SFTP jobs require `return_url` and `file_name`, `validateSFTPFile` included; `GET /filters/list` answers with a JSON *array*; the Warmup `/seed-count` answers with a bare number and `check-domain-dmarc` takes an email address, not a domain; and a failed SMTP login in `test-smtp-configuration` comes back as an HTTP 500.
- **`exportSFTPFile` sends `export_path`**, not the `export_bucket` that `exportS3File` sends.
- **`{"success": false}` on an HTTP 200 is a failure.** The API answers an unknown bulk file id that way. `apiSuccessIsFalse` turns it into an error on every request path.
- **A response is not always a JSON object.** Read into a `JsonElement` when it may be an array or a scalar, and remember Gson parses leniently: junk reads as a string instead of raising, so the shape is checked before parsing.
- **`ErrorResponse.parseError` already handles `detail`.** It collects every value of the error JSON, so the Inbox Placement Tester's Django-style `{"detail": ...}` surfaces without a special case. A test pins that.
- **The version lives in the parent pom's `<sdk.version>`.** Both poms read it; bumping one alone breaks the release.
- **Offline tests only, in the Maven module.** Never call the live API from a test under `src/test`. The live suite is the separate, opt-in `live/LiveApi.java`.
- **Errors carry the HTTP status.** `ErrorResponse.getStatusCode()` is set on every path, so callers can tell a Cloudflare 403 from an API 400/404 and a Warmup 429. A failure that never reached the API leaves it 0.

## Releasing

**Run the live suite by hand before every release.** The offline suite proves the SDK sends what we think it sends; only the live suite proves the API accepts it. Run it with real keys, including `ZEROBOUNCE_LIVE_SPEND=1` and `ZEROBOUNCE_LIVE_CHANGES=1`, and do not release on a failure.
