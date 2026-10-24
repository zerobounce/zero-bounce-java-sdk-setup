# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `validateWithOptions` takes `ZBValidateRequestOptions`: `timeout`, `activityData`, `verifyPlus`, and `usePost` (form-encoded `POST /v2/validate`).
- `validateBatchWithOptions` sends `timeout`, `activityData`, and `verifyPlus` on `POST /v2/validatebatch`.
- `ZBValidateResponse` reads `active_in_days` and `active_first_seen`.
- `scoreEmail` (`GET /v2/scoring`) and `scoreEmailPost` (form-encoded `POST /v2/scoring`).
- Bulk email finder (`sendEmailFinderFile`, `emailFinderFileStatus`, `emailFinderGetFile`, `emailFinderDeleteFile`). `has_header_row` is sent as `true` or `false` when it is set.
- Bulk domain search (`sendDomainSearchFile`, `domainSearchFileStatus`, `domainSearchGetFile`, `domainSearchDeleteFile`). `has_header_row` is sent as `true` or `false` when it is set.
- List evaluator (`sendListEvaluatorFile`, `getListEvaluatorResult`, `sendEvaluatedFile`).
- Filters (`addFilter`, `listFilters`, `deleteFilter`).
- S3 bulk (`validateS3File`, `scoreS3File`, `exportS3File`, `listS3Files`). `email_address_column` is sent when it is set, including `0`.
- `new ZeroBounceSDK()` builds a client for one API key. `getInstance()` remains a process-wide singleton.

### Changed

- A custom API base URL must start with `https://`.
- API requests time out after 120 seconds by default and do not follow redirects.

### Fixed

- A 403 HTML "Access denied" page is returned as a short error. An HTML page with status 200 is an error as well, including `getFile`, `emailFinderGetFile`, and `domainSearchGetFile`, instead of a saved file.
- Empty and non-JSON bodies are a short error. The message is no longer a Gson parser exception, and a repeated `Error:` / `[Error]:` prefix is removed.
- A Cloudflare JSON 403 (`error_code` 1020) is the title "Error 1020: Access denied", not the raw body.
- `validateBatch` reports an account-level failure (HTTP 200, error for address `all`, such as an invalid API key or no credits) through the error callback. An error for one address stays on the successful result.
- An empty API key, a blank email, a blank file id, and a blank filter rule, target, or value are rejected before a request is sent.
- `findEmail` and `findDomain` treat a blank domain or company name as unset.

### Security

- Redirects are disabled, and query values are percent-encoded, so a parameter cannot rewrite the request URL or send the client to another host.
- Error callbacks and exception messages no longer include the raw HTML access-denied page. Exception text replaces the API key with `***`.

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

[Unreleased]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.1.5...HEAD
[2.1.4]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.1.0...v2.1.4
[2.1.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.0.16...v2.1.0
[2.0.16]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v2.0.0...v2.0.16
[2.0.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.3.0...v2.0.0
[1.3.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.1.9...v1.2.0
[1.1.9]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.1.7...v1.1.9
[1.1.7]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/compare/v1.0.0...v1.1.7
[1.0.0]: https://github.com/zerobounce/zero-bounce-java-sdk-setup/releases/tag/v1.0.0
