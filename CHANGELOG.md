# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Deprecated

- `ZeroBounceWarmup.EmailContent.setIndustry` (not an API field; not sent), `EmailContent.setWebsite` (use `setCompanyWebsite`), `ManagedSending.setDailyLimit` (the API does not apply a daily limit; not sent) and `ManagedSending.setTimezoneOffset(Integer)` (use `setTimezoneOffset(double)`; an int literal already calls it). They still compile and work as described under Fixed.

### Fixed

- `ZeroBounceWarmup.addSendingAccount` sends the documented ZeroBounce Managed body (https://www.zerobounce.net/docs/warmup-api/adding-a-sending-account), as the Ruby, Python, C, iOS and Pascal SDKs do: `email_content` carries `company_name`, `company_website`, `company_description`, `types_of_emails` and `example_email`, and `managed_sending` carries `weekdays_only`, `timezone`, `timezone_offset` and `warmup_start_date`. Up to 3.0.1 `EmailContent` and `ManagedSending` could not set `company_website`, `company_description`, `example_email`, `weekdays_only` or `timezone`, and sent `industry`, `website` and `daily_limit`, which the API does not read. New setters: `EmailContent.setCompanyWebsite`, `setCompanyDescription`, `setExampleEmail`, `ManagedSending.setWeekdaysOnly`, `setTimezone` and `setTimezoneOffset(double)`. A website set with `setWebsite` is now sent as `company_website` (unless `setCompanyWebsite` is set too); `industry` and `daily_limit` are no longer sent, and setting either logs a warning through the `ZBLogger` set with `ZeroBounceSDK.setLogger`. `timezone_offset` takes fractional hours from -11 to 14 (`setTimezoneOffset(5.5)`); 3.0.1 took whole hours only. An int literal such as `setTimezoneOffset(3)` still compiles and now goes out as `3.0`; NaN, infinities and anything outside -11 to 14 reach the error callback as "timezone_offset must be -11 to 14" before a request is sent. Nothing that compiled against 3.0.1 stops compiling.
- `ZeroBounceWarmup.downloadSeeds` and `downloadStatistics` report an HTML page answered with HTTP 200 (a proxy's or Cloudflare's block page) as an error with status 200, "ZeroBounce returned an HTML error page ("<title>") instead of an API response.", with the API key redacted from the title, as the C#, JavaScript, Ruby and Python SDKs do. Up to 3.0.1 the page reached the success callback as if it were the CSV. A body is taken for HTML when its content type is `text/html` or its first 2000 characters contain `<!doctype html`, `<html`, `<head` or `<body`.
- The Inbox Placement Tester treats an empty 2xx as a success only on `deleteInboxPlacementTest`, where the API answers 204 No Content: the success callback gets `null`. Up to 3.0.1 an empty 204 was a success with `null` on every Inbox Placement call (`getInboxPlacementProviders`, `getInboxPlacementTestTypes`, `createInboxPlacementTest`, `getInboxPlacementTest`, `listInboxPlacementTests`, `getInboxPlacementWebhook`, `setInboxPlacementWebhook`), while an empty 200 or 202 to `deleteInboxPlacementTest` reached the error callback. **Behaviour change:** an empty 2xx to any Inbox Placement call other than the delete now reaches the error callback with its status ("Empty response from ZeroBounce (HTTP 204)"), and an empty 200 or 202 to the delete is a success. An empty non-2xx is an error with its status, as before. This is the rule of the C#, Rust, Python, PHP and iOS SDKs. The other calls are unchanged.
- Repository only: `sendFile` and `scoringSendFile` build the multipart request they send from the same field list the offline tests check; up to 3.0.1 the request sent over the network was assembled separately, so no test saw its fields. A test sends all four overloads, with every option set and with none, to a real HTTP server on a loopback socket and compares every part (name, value, order, file name and file bytes) with the expected request; another fails when a new `sendFile` or `scoringSendFile` overload appears without being added to it.
- Repository only: a test makes every public request method of `ZeroBounceSDK` whose name mentions `InboxPlacement`, found by reflection, answer an empty 200, 202 and 204 (and 404 and 500) and checks the rule above in both directions.

- A response cut short of its `Content-Length` (the connection closed mid-body) is an error with status 0 and the message "Premature end of Content-Length delimited message body (expected: 1000; received: 33)", the same as `getFile` and `scoringGetFile` already gave. In 3.0.1 every call sent through `HttpURLConnection` read the cut-short body as if it were complete: every `ZeroBounceSDK` call except `getFile`, `scoringGetFile` and the multipart uploads, and every `ZeroBounceWarmup` call. `emailFinderGetFile` and `domainSearchGetFile` saved the partial file and reported success, and a JSON call whose body happened to end in valid JSON (`{"Credits":"5"}` followed by nothing) reached the success callback. They now write no file.
- `initialize(apiKey, apiBaseUrl)` and `initialize(apiKey, apiBaseUrl, listEvaluatorBaseUrl, inboxPlacementBaseUrl)` change nothing when they throw `IllegalArgumentException` for a URL that is not https. In 3.0.1 the two-argument form stored the new key before checking the URL, and the four-argument form also stored the new API base URL (and the list evaluator URL when the Inbox Placement Tester URL was the one refused), so a caller that caught the exception went on with the new key against the old or half-applied URLs. Every URL is now checked before anything is stored.
- `ZeroBounceWarmup.downloadSeeds` and `downloadStatistics` return the CSV exactly as the API sent it, line breaks included (LF or CRLF, with or without a final newline). In 2.2.0, 3.0.0 and 3.0.1 they read the body line by line and joined the lines without separators, so `seed,provider\nseed1@example.com,gmail\n` came back as `seed,providerseed1@example.com,gmail` and could not be split into rows.
- Every other call reads its response body as received too, decoded as UTF-8. Since 1.1.2 the JSON calls and the uploads had read it line by line in the same way. JSON was parsed the same either way, but the error message taken from a multi-line non-JSON error body ran its lines together (`first linesecond line`); it now keeps the line breaks.
- Repository only: tests send every public request method of `ZeroBounceSDK` and `ZeroBounceWarmup`, found by reflection, to a real HTTP server on a loopback socket and fail if a body cut short is not an error with status 0 (and a download leaves a file), if a 301, 302, 303, 307 or 308 with a `Location` is followed or not reported with its status, if any request goes out without a non-zero connect and read timeout, or if `{"success": false}` on an HTTP 200 does not reach the error callback (the uploads included), or if any path joins the lines of a body; the Warmup text downloads are compared byte for byte with what the server sent; a server that never answers ends in a timeout on each kind of transport, and a download whose write fails part-way leaves no file.
- The ten bulk uploads (`sendFile`, `scoringSendFile`, `sendEmailFinderFile`, `sendDomainSearchFile` and `sendListEvaluatorFile`, from a file or a stream) report an HTTP 2xx whose body is not JSON (`hello`, a proxy's text) as an error with status 200, "ZeroBounce returned a response that is not valid JSON.", as every other JSON call does. In 3.0.1 it reached the error callback with status 0 and Gson's message ("Not a JSON Object: ...").
- Repository only: tests now pin the default timeouts (120 s connect, read and connection-request timeout on every request of both clients; a non-zero check let a changed default pass), a non-JSON 2xx and an HTML page found by its doctype alone on every public request method, and the exact wire fields of `testSmtpConfiguration` (`from_address`, `username`, `password`, `server`, `port`), `changeFromAddress` (`team_id`, `old_from_address`, `new_from_address`), `createInboxPlacementTest` (`name`) and the filter calls (`api_key`, `rule`, `target`, `value`).
- A boolean field that holds something else is quoted in the error cut at 40 characters without splitting a character: a cut through a surrogate pair (an emoji) left half of it in the message.

### Security

- The API key is redacted (`***`) from every error message built from a response body, as it already was from transport errors and Warmup errors, and as the C#, JavaScript and Python SDKs do. In 3.0.1 every `ZeroBounceSDK` request method passed the body's text to the error callback unchanged, so a server that quoted the request back put the key in front of the caller: a `400` `Bad request: GET /v2/getcredits?api_key=<key>` reached the error callback as is, and so did the key in an HTTP 200 `{"error": "Invalid API key <key>"}`. This covered the JSON calls, the bulk and list-evaluator downloads, the multipart uploads, the batch call, the email finder and domain search files and the Inbox Placement Tester. The key is now replaced as sent, URL-encoded and JSON-escaped; `ZeroBounceWarmup` replaces those forms of its key and of the SMTP password too (it replaced only the key and password as sent).
- Repository only: a test sends every public request method of `ZeroBounceSDK` and `ZeroBounceWarmup`, found by reflection, to a loopback server whose error bodies (a plain-text 400, a JSON 400 and an HTTP 200 carrying an error) quote the request URL, the `API-Key` header, the body and the key, and fails if the key or the Warmup SMTP password reaches any error callback in any form.
- Repository only: tests send every public request method of `ZeroBounceWarmup`, found by reflection, to a loopback server that answers HTTP 200 with a body Warmup must refuse and that quotes the key and the request (text that is not JSON to the JSON calls, a JSON error to the CSV downloads, and an HTML page whose title holds the key), and fail unless each call reports one error with status 200 that carries neither the key nor the SMTP password. The redaction of messages built from a 200 body had no test before.
- Repository only: a test makes every public request method of `ZeroBounceWarmup`, found by reflection, fail in the transport with a message that quotes the request URL and the key (as sent, URL-encoded and JSON-escaped), and for `testSmtpConfiguration` the SMTP password, and fails unless each reports one error with status 0 that carries neither secret in any form.
- What the SDK writes to the `ZBLogger` set with `setLogger` never carries the API key. In 3.0.1 the multipart uploads logged the whole response body at debug level even with payload logging off, and `setLogPayloads(true)` logged every body as received, so a body quoting the key (`{"error": "Invalid API key <key>"}`) put it in the log; logged exceptions passed their message and causes on as they were. Every log message and logged exception (with its causes, as a copy with the same type name and stack trace) now has the key redacted, and the upload bodies are logged only with payload logging on.
- The key and the SMTP password are also redacted when quoted JSON-escaped without Gson's HTML escapes, or with `/` written as `\/` (PHP's `json_encode`), and URL-encoded with `%20` for a space. A test sweeps every public request method with a key and a password containing `"`, `/`, `+` and `%`.

## [3.0.1] - 2026-11-11

### Fixed

- `ZBActivityDataResponse.toString()` starts with `ZBActivityDataResponse{`; 3.0.0 printed `ZBActivityData{`.
- `ZBGetApiUsageResponse.toString()` includes `subStatusLeadingPeriodRemoved` and `subStatusAliasAddress`, which 3.0.0 left out.
- Repository only: a test sweeps every public request method of `ZeroBounceSDK`, found and invoked by reflection, and fails if any of them sends a request over anything but HTTPS, or if a public request method is missing from the sweep. Another checks the `toString()` of every response model for its type name and every field.

## [3.0.0] - 2026-11-12

### Breaking

- An exception thrown by your own success or error callback now propagates out of the `ZeroBounceSDK` or `ZeroBounceWarmup` call that invoked it. In 2.2.0 an exception from the success callback was caught on every `ZeroBounceSDK` request and on the `ZeroBounceWarmup` JSON calls, and handed to the error callback as if the request had failed. An exception from the error callback was caught the same way on every `ZeroBounceSDK` request, and on `ZeroBounceWarmup` calls answered with an error status or a redirect, so the SDK called the error callback a second time with that exception's message before it propagated. The error callback now receives only failed requests: transport failures, error responses, and bodies the SDK cannot read.

#### Upgrading from 2.2

- If a success or error callback can throw, catch the exception in the callback or around the SDK call.

### Added

- `ErrorResponse.toString()` and `ZBValidateBatchResponse.toString()` name the type and every field (`ErrorResponse{success=false, errors=[...], statusCode=403}`). In 2.2.0 they printed the default `ErrorResponse@<hash>`.

### Changed

- `initialize` refuses an API key with surrounding whitespace or control characters, such as a key read from a file with its trailing newline, as `ZeroBounceWarmup.initialize` already did. The key is not stored or sent, and every call reports "api_key has surrounding whitespace or control characters" through its error callback, with status 0, until `initialize` is called with a clean key. In 2.2.0 such a key was sent as given. A key with spaces inside it is unchanged.
- Booleans in responses (`free_email`, `mx_found`, `catchall_domain`, `found`, `success`) are read strictly: `true`/`false`, the strings `"true"`/`"false"` in any case, and `1`/`0` as a number or a string. In 2.2.0 the strings `"yes"` and `"1"` were read as `false`, and a bare `1` or `0` reached the error callback with a Gson parser exception. Any other value now reaches the error callback with a short message naming the field, such as `ZeroBounce returned "yes" for free_email, which is not a boolean.`
- Dependencies that ship to consumers: the POM no longer declares `httpclient5`, which the SDK never used; it uses Apache HttpClient 4 through `httpmime`. It now declares `httpclient` 4.5.14 and `httpcore` 4.4.16, the versions `httpmime` 4.5.14 already brought in, so the SDK runs on the same classes as before, and `httpclient5`, `httpcore5`, `httpcore5-h2` and `slf4j-api` 1.7.36 (which only `httpclient5` brought in) leave your classpath. If your own code uses HttpClient 5, declare it yourself. `org.jetbrains:annotations` is now `provided` scope and no longer reaches your runtime classpath; its annotations are not needed at run time.
- The published POM states the version as a literal (`<version>2.x.y</version>`) instead of the `${sdk.version}` expression, and leaves out the build section and profiles.

### Fixed

- `getFile`, `scoringGetFile`, `emailFinderGetFile` and `domainSearchGetFile` saved a JSON error body as the results file when it did not come as `application/json`. `getFile` and `scoringGetFile` checked the body only for that content type, so `{"error": ...}` sent as `text/plain`, `application/octet-stream` or with no content type was written to disk and reached the success callback; `emailFinderGetFile` and `domainSearchGetFile` missed `{"Error": ...}`, `{"Message": ...}` and `{"detail": ...}`. All four now treat a JSON object with a `success` field, or with an `error`, `message`, `error_message`, `Error`, `Message` or `detail` field, as an error with the HTTP status, whatever its content type, and write no file, as the C++ and Rust SDKs do. Any body sent as `application/json` is an error too. A CSV is saved as before. An error body with no message, `{"success": false}`, now carries one instead of an empty error list (from all four calls on an HTTP 400, and from `emailFinderGetFile` and `domainSearchGetFile` on an HTTP 200 too).

## [2.2.0] - 2026-11-11

### Breaking

The details are under Changed and Fixed below.

#### Upgrading from 2.1.5

- A custom base URL must start with `https://`: `initialize(apiKey, "http://...")` throws `IllegalArgumentException`. `initialize(apiKey)` and the `ZBConstants` URLs are unaffected.
- Move error handling out of your success callbacks. An invalid or drained key, `{"success": false}`, a JSON `error` on an HTTP 200, an HTML page, an empty or non-JSON body, and a `validateBatch` account-level failure (error for address `all`) reach the error callback. `getCredits` still returns `-1` for an invalid key.
- Requests time out after 120 seconds by default and do not follow redirects.
- A blank API key, email, file id, or filter rule, target or value reaches the error callback before a request is sent.
- The `ai_agent_mailbox` sub-status, which 2.1.5 decoded as `null`, is `ZBValidateSubStatus.AI_AGENT_MAILBOX`.
- `ErrorResponse.equals` and `hashCode` include the new status code.

### Added

- `validate(email, ipAddress, ZBValidateOptions, ...)` sends the documented optional parameters: `timeout` (3-60 seconds), `activityData`, `verifyPlus`, `domainInfo`, `greylistProcessing`, and `callbackUrl`, and `setUsePost(true)` sends them as a form-encoded `POST /v2/validate`. An option left unset is not sent. A timeout outside 3-60 seconds, a blank `callbackUrl`, and a `callbackUrl` without `greylistProcessing(true)` reach the error callback before the request is sent.
- `validateBatch(emails, ZBValidateOptions, ...)` sends `timeout` (10-120 seconds, as a JSON string), `activityData`, and `verifyPlus`; a timeout outside that range is rejected before the request.
- `validateWithOptions` takes `ZBValidateRequestOptions`: `timeout`, `activityData`, `verifyPlus`, and `usePost` (form-encoded `POST /v2/validate`). It is a wrapper over `validate(String, String, ZBValidateOptions, ...)`, so a timeout outside 3-60 seconds is rejected before the request. Prefer `ZBValidateOptions`, which also carries `domainInfo`, `greylistProcessing`, and `callbackUrl`.
- `validateBatchWithOptions` sends `timeout`, `activityData`, and `verifyPlus` on `POST /v2/validatebatch`.
- `ZBValidateResponse` reads `active_in_days` and `active_first_seen`, the `domain_info` fields `domain_website_exists` and `domain_registrant_company_name` (`getDomainWebsiteExists`, `getDomainRegistrantCompanyName`), and `Message` (`getMessage`), which carries the "Greylist validation still in progress" answer to a repeated `greylist_processing` request.
- `ZBValidateSubStatus.AI_AGENT_MAILBOX` for the `ai_agent_mailbox` sub-status, which 2.1.5 decoded as `null`.
- `scoreEmail` (`GET /v2/scoring`), `scoreEmail(email, usePost, ...)`, and `scoreEmailPost` (form-encoded `POST /v2/scoring`).
- `ZBScoreEmailResponse`, the result both of them hand to the success callback, with `getEmail`, `getScore`, `getStatus`, and `getError`. The score is read as a `String`: the API returns it as a number or a string depending on the account.
- Bulk email finder (`sendEmailFinderFile`, `emailFinderFileStatus`, `emailFinderGetFile`, `emailFinderDeleteFile`). `has_header_row` is sent as `true` or `false` when it is set. The upload requires `first_name_column` or `full_name_column`.
- Bulk domain search (`sendDomainSearchFile`, `domainSearchFileStatus`, `domainSearchGetFile`, `domainSearchDeleteFile`). `has_header_row` is sent as `true` or `false` when it is set.
- `sendEmailFinderFileStream`, `sendDomainSearchFileStream`, and `sendListEvaluatorFileStream` upload from an `InputStream` with a file name, like the existing `sendFile` stream overloads.
- List evaluator (`sendListEvaluatorFile`, `getListEvaluatorResult`, `sendEvaluatedFile`). The upload and the result share the list evaluator base URL, so overriding that host moves both. `sendEvaluatedFile` takes the evaluator's `file_id` as a form field; there is no file-upload overload, because the live API rejects one with "file_id: This field is required".
- Filters (`addFilter`, `listFilters`, `deleteFilter`).
- S3 bulk (`validateS3File`, `scoreS3File`, `emailFinderS3File`, `domainSearchS3File`, `exportS3File`, `listS3Files`). `email_address_column` is sent when it is set, including `0`. `validateS3File` and `scoreS3File` also take `ValidateS3FileOptions` and `ScoreS3FileOptions` for the column indexes those two jobs accept; both overloads require `file_name` and `return_url`, so both option classes have `setFileName` and `setReturnUrl`.
- `ZBS3ListFileEntry`, the entry type `listS3Files` hands over, with `getName`, `getSize` (a `long`), and `getLastModified` (the raw string). Each field reads both the capitalised keys the docs show (`Name`, `Size`, `LastModified`) and the lower-cased ones the other ZeroBounce SDKs read, so neither spelling decodes as null.
- SFTP/FTP bulk (`validateSFTPFile`, `scoreSFTPFile`, `emailFinderSFTPFile`, `domainSearchSFTPFile`, `exportSFTPFile`), on the bulkapi host without the `/v2` path. The server, path and protocol are never sent by the SDK; they come from your ZeroBounce settings. `exportSFTPFile(fileId, exportPath, ...)` sends `export_path`, not the `export_bucket` that `exportS3File` sends: `null` uses the path from your settings, and a blank one is rejected before the request.
- `ZBBulkFileJobOptions` carries the fields the S3 and SFTP/FTP file jobs share: `setFileName`, `setReturnUrl`, `setHasHeaderRow`, and `setRemoveDuplicates`. `has_header_row` is always sent and defaults to `true`; `remove_duplicates` is sent only when it is set. `file_name` and `return_url` are required on every one of those jobs, `validateSFTPFile` included, and a missing or blank one reaches the error callback before a request is sent. `ValidateS3FileOptions` and `ScoreS3FileOptions` fill the same fields.
- Inbox Placement Tester (`getInboxPlacementProviders`, `getInboxPlacementTestTypes`, `createInboxPlacementTest`, `getInboxPlacementTest`, `listInboxPlacementTests`, `deleteInboxPlacementTest`, `getInboxPlacementWebhook`, `setInboxPlacementWebhook`). It authenticates with an `Authorization: Bearer` header and lists from `GET /tests`. `createInboxPlacementTest` rejects a name over 64 characters, a `testType` other than 1, 2, or 3, and a type 1 test without a `senderEmail`, before the request is sent. `ListInboxPlacementTestsOptions` takes `status`, `page`, `pageSize`, `createdAfter`, and `createdBefore`; the page size goes on the wire as `page_size`, which is the name the API honours.
- `ZeroBounceWarmup`, a client for the Warmup API. It is a separate product with its own host (`ZeroBounceWarmup.DEFAULT_BASE_URL`, `https://warmup-api.zerobounce.net`, every call under `/api/v1/warmup`) and its own team-scoped key, sent in an `API-Key` header rather than an `api_key` parameter. It is not reachable from `ZeroBounceSDK`: call `ZeroBounceWarmup.getInstance()` for a shared client or `new ZeroBounceWarmup()` for one per key, then `initialize(apiKey)` or `initialize(apiKey, baseUrl)`, and `setTimeout(timeoutInMillis)` for the 120-second default. A Warmup key with surrounding whitespace or control characters, and a base URL that is not `https://`, are refused in `initialize`; every later call then fails with that message instead of sending the key anywhere.
- All 26 Warmup endpoints: sending accounts (`listSendingAccounts`, `getSendingAccount`, `addSendingAccount`, `updateSendingAccount`, `deleteSendingAccount`, `changeFromAddress`, `listFromAddresses`), engagement rules (`getEngagementRules`, `setEngagementRule`, `getCustomEngagement`, `createCustomEngagement`, `updateCustomEngagement`, `deleteCustomEngagement`), seeds and statistics (`getSeeds`, `downloadSeeds`, `getSeedCount`, `getProgressToday`, `getSentToday`, `getEmailsPerDay`, `getStatistics`, `getStatisticsPerDay`, `getStatisticsDetailed`, `downloadStatistics`, `getFromAddressStatistics`), `checkDomainDmarc`, and `testSmtpConfiguration`.
- A Warmup success is a `JsonObject`. A body that is not an object is wrapped under `data`, because `/seeds` answers with an array and `/seed-count` with a bare number, and a documented empty success (HTTP 204, or an empty 200) is handed over as `null`. `downloadSeeds` and `downloadStatistics` take an `OnSuccessCallback<String>` and hand over the CSV text.
- Warmup arguments are checked before the request: `addSendingAccount` takes an `onboardingModel` of `self_managed` or `zb_managed`, and `AddSendingAccountOptions` (`engagementRuleId`, `ignoreConflict`, `EmailContent`, `ManagedSending`); `self_managed` requires `engagementRuleId` 0-4 and refuses `emailContent` or `managedSending`; `timezoneOffset` is -11 to 14 and `warmupStartDate` is `YYYY-MM-DD`; `createCustomEngagement` and `updateCustomEngagement` take `replyPct` 5-45, `importantPct` 5-40, and `clickPct` 0-70 and at most `importantPct` x 1.75. `checkDomainDmarc` takes an email address, not a bare domain, which is what the live API wants. `testSmtpConfiguration` stores nothing and sends its test message to a ZeroBounce recipient rather than `fromAddress`; the username and password are masked out of every error it raises, and the live API answers a failed SMTP login with HTTP 500.
- `ErrorResponse.getStatusCode()` and `setStatusCode(int)` carry the HTTP status of a failed response, so a caller can tell a Cloudflare 403 from an API 400/404 and a Warmup 429 apart. It is `0` when the failure never reached the API, and it is part of `equals` and `hashCode`.
- `ZBGetApiUsageResponse` maps `sub_status_leading_period_removed` and `sub_status_alias_address`, two counters the API already returns and that were previously discarded. Unlike every other counter on the class, neither has a getter yet, so the parsed values are not reachable from caller code in this release.
- `new ZeroBounceSDK()` builds a client for one API key. `getInstance()` remains a process-wide singleton.
- `initialize(apiKey, apiBaseUrl, listEvaluatorBaseUrl, inboxPlacementBaseUrl)` overrides the list evaluator and Inbox Placement Tester hosts as well, which `initialize(apiKey, apiBaseUrl)` leaves on the live API. A `null` keeps that default.

### Changed

- **Breaking:** a custom API base URL must start with `https://`. `initialize(apiKey, "http://...")`, including a plain-http local mock server, now throws `IllegalArgumentException`; 2.1.5 accepted any URL.
- API requests time out after 120 seconds by default and do not follow redirects. In 2.1.5, `initialize(apiKey)` set no timeout.
- `{"success": false}` on an HTTP 200 reaches the error callback, with the body's message and `getSuccess()` false. The API answers an unknown bulk file id that way. In 2.1.5 it reached the success callback from `fileStatus`, `scoringFileStatus`, `deleteFile`, `scoringDeleteFile`, `sendFile`, and `scoringSendFile`; it does the same on the calls new in this release.
- An HTTP 200 that carries a JSON `error`, an HTML page, or a body that is not JSON reaches the error callback on every call, whatever its HTTP method. In 2.1.5 every HTTP 200 went to the success callback, unless Gson could not read the body at all.
- `httpclient5` 5.6.3 to 5.6.4. The SDK never used HttpClient 5 (it uses HttpClient 4 through `httpmime`), so this changed nothing the SDK does; the unused declaration only put `httpclient5` on consumers' classpath. It is removed in [Unreleased].

### Fixed

- An invalid or drained API key reached the success callback. The API answers it with an HTTP 200 and no `success` field, and in 2.1.5 every call handed that body to the success callback: `{"error": "Invalid API key or your account ran out of credits"}` from `validate` and `getActivityData` (and `{"error": "Invalid API key"}` from `getApiUsage`) arrived as a result whose fields were empty or zero, and so did `{"Message": "Invalid API key or your account ran out of credits"}` from `findEmail`, `findDomain` and `guessFormat`. All of them now reach the error callback with that message, and so do the filters, new in this release, which answer `{"Error": ...}`. A bare `Message` is still a result where it is one: a filter change answers with one, and a repeated `greylist_processing` validate answers `{"Message": "Greylist validation still in progress. Please try again later."}`. `getCredits` still returns `-1` for an invalid key, as documented.
- A 403 HTML "Access denied" page is returned as a short error. An HTML page with status 200 is an error as well, including `getFile`, `emailFinderGetFile`, and `domainSearchGetFile`, instead of a saved file.
- Empty and non-JSON bodies are a short error. The message is no longer a Gson parser exception, and a repeated `Error:` / `[Error]:` prefix is removed, from a plain-text body and from the values of a JSON error alike.
- A Cloudflare JSON 403 (`error_code` 1020) is the title "Error 1020: Access denied", not every value of the body.
- `validateBatch` reports an account-level failure (HTTP 200, error for address `all`, such as an invalid API key or no credits) through the error callback. An error for one address stays on the successful result.
- An empty API key, a blank email (`validate`, `scoreEmail`, `getActivityData`), a blank file id (every file status, get-file, and delete-file call, the email finder and domain search ones included), and a blank filter rule, target, or value are rejected before a request is sent.
- `findEmail` and `findDomain` treat a blank domain or company name as unset. `findEmail` takes a nullable `firstName` and leaves `first_name` out when it is blank: it is optional on `guessformat`.
- A failed response with no body (for example an empty HTTP 500) reported an empty error list and status 0. It is now "Empty response from ZeroBounce (HTTP 500)" with the status.
- `initialize(apiKey, apiBaseUrl)` removes trailing slashes from the base URL. The `ZBConstants` values (`API_DEFAULT_URL`, `API_USA_URL`, `API_EU_URL`) end in a slash, and in 2.1.5 they, and any custom base URL ending in a slash, were used as given, so `validate`, `validateBatch`, `guessFormat`, `findEmail`, `findDomain`, `getApiUsage`, `getCredits`, and `getActivityData` went to a double-slash path such as `https://api-eu.zerobounce.net/v2//validate`. They now go to `https://api-eu.zerobounce.net/v2/validate`, and so do the calls new in this release on that host (`scoreEmail`, the filters). The list evaluator and Inbox Placement Tester base URLs of `initialize(apiKey, apiBaseUrl, listEvaluatorBaseUrl, inboxPlacementBaseUrl)` are trimmed the same way. The constants keep their values. Without a base URL, and on the bulk endpoints, the URLs were already correct and are unchanged.
- The release build packed every `.java` file under `zero-bounce-sdk/src`, the test sources included, into the main jar next to the classes, and into the `-sources` jar a second time. The main jar now holds only the compiled classes, and the `-sources` jar only the `src/main/java` sources.

### Security

- Redirects are disabled, so a redirect cannot send the request, and the `api_key` on its query string, to another host. (Query values were already percent-encoded in 2.1.5.)
- Error callbacks no longer include the raw HTML access-denied page, and the text of an exception that reaches the error callback replaces the API key with `***`, on every call. In 2.1.5 an exception's message, which can quote the request URL and its `api_key`, went to the error callback as it was.

## [2.1.5] - 2026-08-14

No changes to the SDK source. Dependency and build updates only.

### Changed

- `httpclient5` 5.6.1 to 5.6.3 and `commons-codec` 1.22.0 to 1.22.1. The SDK never used HttpClient 5, so the `httpclient5` bump changed nothing the SDK does.
- Build and test tooling: JUnit 6.0.3 to 6.1.2, `maven-surefire-plugin` 3.5.5 to 3.5.6, `jacoco-maven-plugin` 0.8.14 to 0.8.15, and `central-publishing-maven-plugin` 0.10.0 to 0.11.0.

## [2.1.4] - 2026-06-09

### Added

- `ZBValidateResponse.catchallDomain`, read from `catchall_domain`.

## [2.1.0] - 2026-04-05

### Added

- `getFile` and `scoringGetFile` accept `ZBGetFileOptions`, with `downloadType` (`phase_1`, `phase_2`, `combined`) and `activityData` (validation `getFile` only).
- `SendFileOptions.setAllowPhase2`, sent as `allow_phase_2` on validation `sendFile` only.
- `ZBFileStatusResponse.filePhase2Status`.

### Fixed

- `getFile` calls the error callback when the body is a JSON error, including HTTP 200 with `"success": false`, instead of saving that body as the results file.

## [2.0.16] - 2026-03-03

### Changed

- License the project under MIT.

## [2.0.0] - 2026-03-03

### Added

- Stream overloads of `sendFile` and `scoringSendFile`.
- Additional counters on `ZBGetApiUsageResponse`.
- `ZBValidateStatus.NONE` and further `ZBValidateSubStatus` values, including `ACCEPT_ALL`, `ROLE_BASED_ACCEPT_ALL`, and `GOLD`.

## [1.3.0] - 2025-11-06

### Added

- `findEmail` and `findDomain`.

### Changed

- The SDK builds with Java 21.

### Deprecated

- `guessFormat`. Use `findEmail` or `findDomain`.

## [1.2.0] - 2025-01-13

### Added

- ISO-8601 values in response dates.

### Fixed

- Query values, including the email address, are percent-encoded.

## [1.1.9] - 2024-09-17

### Added

- `initialize(apiKey, timeoutInMillis)`. `0` means no timeout.

## [1.1.7] - 2023-08-25

### Fixed

- `ZBValidateStatus` and `ZBValidateSubStatus` are public.

## [1.0.0] - 2019-12-04

### Added

- Initial release, including validation, bulk file, credits, activity, and AI scoring file methods.

[Unreleased]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v3.0.1...HEAD
[3.0.1]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.2.0...v3.0.0
[2.2.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.1.5...v2.2.0
[2.1.5]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.1.4...v2.1.5
[2.1.4]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.1.0...v2.1.4
[2.1.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.0.16...v2.1.0
[2.0.16]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.0.0...v2.0.16
[2.0.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.3.0...v2.0.0
[1.3.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.1.9...v1.2.0
[1.1.9]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.1.7...v1.1.9
[1.1.7]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.0.0...v1.1.7
[1.0.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/releases/tag/v1.0.0
