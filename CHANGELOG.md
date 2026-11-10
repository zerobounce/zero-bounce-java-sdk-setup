# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.2.0] - 2026-11-11

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
- `httpclient5` 5.6.3 to 5.6.4.

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

- `httpclient5` 5.6.1 to 5.6.3 and `commons-codec` 1.22.0 to 1.22.1.
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

[Unreleased]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.2.0...HEAD
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
