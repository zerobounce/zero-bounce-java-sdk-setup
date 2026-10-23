# Agent notes — zero-bounce-java-sdk-setup

## Layout

- `zero-bounce-sdk/` is the published Maven module. `src/` at the repo root is only the sample app.
- `ZeroBounceSDK.java` holds every request. `sendRequest` is the original GET/JSON-POST helper; `sendRequestWithMethod` is the one that can also do form-encoded POSTs and the Inbox Placement Tester's PUT and DELETE.
- `ZBValidateOptions`, `ZBBulkFileJobOptions`: the option objects, each with a package-private `fill` that both builds the request and rejects an invalid combination before anything is sent.
- `OfflineApiTest.java`: the offline suite. A mocked `HttpURLConnection` answers every call and records the url, method and body, so the assertions are on what actually went on the wire.

## Build and test

Maven runs in Docker (there is no `mvn` on the host, and `mvnw` has no wrapper jar committed):

```sh
cd ../sdk-docs && docker compose run --rm --build java
```

That build runs `mvn -q test` and fails the build if a test fails — but the container's `CMD` then fails on `/root/.m2` permissions, which is not a test failure. To see the test output, or to run against the working tree without rebuilding:

```sh
docker run --rm -u 0 -v "$PWD":/src -w /src/zero-bounce-sdk zerobounce-sdk-java:test mvn -B test
```

## Rules

- **Only endpoints documented at https://www.zerobounce.net/docs/.** Do not add a method for an endpoint the public docs do not describe, even if it exists on the server. Check before concluding something is undocumented: the S3 and SFTP bulk endpoints live under https://www.zerobounce.net/docs/api-integrations, not the main API reference.
- **Four base URLs.** `apiBaseUrl`, `bulkApiBaseUrl` (ends in `/v2`), `bulkApiRootUrl` (the S3/SFTP jobs, **no** `/v2`), and `listEvaluatorBaseUrl` / `inboxPlacementBaseUrl`. The last two are separate hosts rather than paths built from the bulk base, so `initialize(apiKey, apiBaseUrl)` alone leaves them on the live API — use the four-argument `initialize` in tests.
- **Two bulk shapes are easy to get wrong.** `validateSFTPFile`'s `file_name` is optional — leaving it unset uses the input bucket from the account's settings, and every other job requires it. `exportSFTPFile` sends `export_path`, not the `export_bucket` that `exportS3File` sends.
- **`ErrorResponse.parseError` already handles `detail`.** It collects every value of the error JSON, so the Inbox Placement Tester's Django-style `{"detail": ...}` surfaces without a special case. A test pins that.
- **The version lives in the parent pom's `<sdk.version>`.** Both poms read it; bumping one alone breaks the release.
- **Offline tests only.** Never call the live API from a test.
