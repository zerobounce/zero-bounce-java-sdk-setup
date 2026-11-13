##### Instalation

You can install ZeroBounceSDK by adding the dependency to your `pom.xml` file:
```xml
<dependency>
    <groupId>com.zerobounce.java</groupId>
    <artifactId>zerobouncesdk</artifactId>
    <version><latest_version></version>
</dependency>
```


#### Using the SDK from Maven Central

The SDK is published on [Maven Central](https://mvnrepository.com/artifact/com.zerobounce.java/zerobouncesdk); the dependency in the Installation section resolves it there. This repository does not commit a built jar. To try an unreleased change from this repository, run `./mvnw install` in `zero-bounce-sdk/`, which installs the SDK into your local `~/.m2` repository under its `sdk.version`, and depend on that version from your project.


#### How to deploy the SDK

Releases are published with the GitHub Actions `Publish` workflow; see ***Publication*** below. To publish by hand:

1. Set *autoPublish* to *false* for the `central-publishing-maven-plugin` in the `release` profile of the **zero-bounce-sdk** `pom.xml` if you don't want the artifact to be published on Maven Central automatically.
2. Use the command: `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<YOUR_PASSPHRASE> clean deploy -Prelease` from the **zero-bounce-sdk** folder.


#### USAGE

Initialize the sdk with your api key:
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");
```
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>", timeoutInMillis);
```


#### Controlling SDK logging

Two loggers can write during a request:

- **The SDK's own logger** stays off until you register a `ZBLogger`. What it writes has the API key
  redacted.
- **Apache HttpClient**, which sends the requests, logs through your SLF4J backend. At DEBUG it writes
  the API key, the Warmup key, the SMTP password and uploaded files.

Before each request the SDK checks three of those loggers: `org.apache.hc.client5.http.impl.classic.MainClientExec`,
`org.apache.hc.client5.http.headers` and `org.apache.hc.client5.http.wire`. While any of them is at DEBUG or TRACE,
the call sends nothing and reports status 0 with the message "Apache HttpClient debug logging (org.apache.hc) is
enabled and would write your API key to the log; set org.apache.hc to INFO or above." A level changed at run time
applies to the next call.

Keep `org.apache.hc` at INFO or above in production. Logback with no configuration file logs
everything at DEBUG, so if you use Logback without a `logback.xml`, start from this one:

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- HttpClient writes API keys and passwords at DEBUG. -->
  <logger name="org.apache.hc" level="INFO"/>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

To send the SDK's own logging to your application's logging framework, register a `ZBLogger`
implementation before issuing any API calls. The helper class `ZBLoggers` adapts
`java.util.logging` (JUL) out of the box:

```java
import com.zerobounce.ZBLoggers;

ZeroBounceSDK.setLogger(
    ZBLoggers.jul(java.util.logging.Logger.getLogger("ZeroBounceSDK"))
);

// Optional: enable verbose payload logging for troubleshooting only.
ZeroBounceSDK.setLogPayloads(true);
```

Passing `null` to `ZeroBounceSDK.setLogger(...)` resets the logger to a no-op implementation, which
disables SDK logging again.

#### Bulk API v2 types

- **ZBFileStatusResponse** – Includes `errorReason` when present, and `filePhase2Status` (`file_phase_2_status`) when the bulk API returns it (for example after optional phase 2 processing).
- **ZBDownloadType** – `PHASE_1`, `PHASE_2`, or `COMBINED` for bulk `getfile` query parameter `download_type` (validation and scoring).
- **ZBGetFileOptions** – Optional `downloadType` and `activityData` for validation `getFile`; `scoringGetFile` uses `downloadType` only (`activityData` is not sent).


#### Examples

> **Note:** The snippets below print responses for demonstration purposes. Avoid logging raw API
> data that may contain PII in production systems.

Then you can use any of the SDK methods, for example:

* ####### Validate an email address
    ```java
    ZeroBounceSDK.getInstance().validate(
        "<ANY_EMAIL_ADDRESS>",
        "<OPTIONAL_IP_ADDRESS>",
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateResponse>() {
            @Override
            public void onSuccess(ZBValidateResponse response) {
                System.out.println("validate response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("validate error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Validate batch a list of email addresses
    ```java
    List<ZBValidateBatchData> emailsData = new ArrayList<ZBValidateBatchData>();
    emailsData.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
    emailsData.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
    emailsData.add(new ZBValidateBatchData("disposable@example.com", null));

    ZeroBounceSDK.getInstance().validateBatch(
        emailsData,
        new ZeroBounceSDK.OnSuccessCallback<ZBValidateBatchResponse>() {
            @Override
            public void onSuccess(ZBValidateBatchResponse response) {
                System.out.println("validateBatch response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("validateBatch error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Find the email and other domain formats based on a given domain name
    ```java
    ZeroBounceSDK.getInstance().guessFormat(
        "<DOMAIN_TO_TEST>",
        null,
        null,
        null,
        new ZeroBounceSDK.OnSuccessCallback<ZBEmailFinderResponse>() {
            @Override
            public void onSuccess(ZBEmailFinderResponse response) {
                System.out.println("guessFormat response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("guessFormat error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Check how many credits you have left on your account
    ```java
    ZeroBounceSDK.getInstance().getCredits(
        new ZeroBounceSDK.OnSuccessCallback<ZBCreditsResponse>() {
            @Override
            public void onSuccess(ZBCreditsResponse response) {
                System.out.println("getCredits response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("getCredits error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Check your API usage for a given period of time
    ```java
    Date startDate = new Date();    // The start date of when you want to view API usage
    Date endDate = new Date();      // The end date of when you want to view API usage
    
    ZeroBounceSDK.getInstance().getApiUsage(
        startDate, 
        endDate, 
        new ZeroBounceSDK.OnSuccessCallback<ZBGetApiUsageResponse>() {
            @Override
            public void onSuccess(ZBGetApiUsageResponse response) {
                System.out.println("getApiUsage response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("getApiUsage error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### The *sendFile* API allows user to send a file for bulk email validation
    ```java
    File myFile = new File("<FILE_PATH>");  // The csv or txt file
    int emailAddressColumn = 3;             // The column index of the email address in the file. Index starts at 1
    
    // sendFile and scoringSendFile throw the checked ZBException for an invalid argument,
    // such as an email column below 1, before anything is sent.
    try {
        ZeroBounceSDK.getInstance().sendFile(
            myFile,
            emailAddressColumn,
            null,
            new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() {
                @Override
                public void onSuccess(ZBSendFileResponse response) {
                    System.out.println("sendFile response=" + response.toString());
                }
            }, new ZeroBounceSDK.OnErrorCallback() {
                @Override
                public void onError(ErrorResponse errorResponse) {
                    System.out.println("sendFile error=" + errorResponse.getErrors());
                }
            });
    } catch (ZBException e) {
        System.out.println("sendFile rejected its arguments: " + e.getMessage());
    }
    ```

    Bulk validation uses `https://bulkapi.zerobounce.net/v2`. See [v2 send file](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-send-file), [v2 file status](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-file-status), and [v2 get file](https://www.zerobounce.net/docs/email-validation-api-quickstart/v2-get-file).

    Optional multipart field `allow_phase_2` (validation bulk only): use `ZeroBounceSDK.SendFileOptions` and `setAllowPhase2(Boolean)`:

    ```java
    ZeroBounceSDK.SendFileOptions opts = new ZeroBounceSDK.SendFileOptions()
            .setAllowPhase2(true);

    try {
        ZeroBounceSDK.getInstance().sendFile(
            myFile,
            emailAddressColumn,
            opts,
            successCallback,
            errorCallback);
    } catch (ZBException e) {
        System.out.println("sendFile rejected its arguments: " + e.getMessage());
    }
    ```

* ####### The *getFile* API allows users to get the validation results file for the file been submitted using *sendFile* API
    ```java
    String fileId = "<FILE_ID>";                    // The returned file ID when calling sendfile API
    String downloadPath = "<FILE_DOWNLOAD_PATH>";   // The path where the file will be downloaded
    
    ZeroBounceSDK.getInstance().getFile(
        fileId,
        downloadPath,
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() {
            @Override
            public void onSuccess(ZBGetFileResponse response) {
                System.out.println("getfile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("getfile error=" + errorResponse.getErrors());
            }
        });
    ```

    Optional query parameters (validation `getFile` only for activity data; `scoringGetFile` does not send `activity_data`):

    ```java
    import com.zerobounce.ZBDownloadType;
    import com.zerobounce.ZBGetFileOptions;

    ZBGetFileOptions getOpts = new ZBGetFileOptions()
            .setDownloadType(ZBDownloadType.COMBINED)
            .setActivityData(true);

    ZeroBounceSDK.getInstance().getFile(
        fileId,
        downloadPath,
        getOpts,
        successCallback,
        errorCallback);

    ZeroBounceSDK.getInstance().scoringGetFile(
        fileId,
        downloadPath,
        new ZBGetFileOptions().setDownloadType(ZBDownloadType.PHASE_2),
        successCallback,
        errorCallback);
    ```

    `phase_2` / `combined` downloads apply when phase 2 was enabled for the file and `file_phase_2_status` is `Complete`. JSON error bodies may still use HTTP 200; the SDK surfaces them via the failure callback.

* ####### Check the status of a file uploaded via *sendFile* API
    ```java
    String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API
    
    ZeroBounceSDK.getInstance().fileStatus(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() {
            @Override
            public void onSuccess(ZBFileStatusResponse response) {
                System.out.println("fileStatus response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("fileStatus error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Delete the file that was submitted using *sendFile* API. File can be deleted only when its status is `Complete`
    ```java
    String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API
    
    ZeroBounceSDK.getInstance().deleteFile(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse>() {
            @Override
            public void onSuccess(ZBDeleteFileResponse response) {
                System.out.println("deleteFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("deleteFile error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Gather insights into your subscribers’ overall email engagement. The request returns data regarding opens, clicks, forwards and unsubscribes that have taken place in the past 30, 90, 180 or 365 days.
    ```java
    ZeroBounceSDK.getInstance().getActivityData(
        "<ANY_EMAIL_ADDRESS>",
        new ZeroBounceSDK.OnSuccessCallback<ZBActivityDataResponse>() {
            @Override
            public void onSuccess(ZBActivityDataResponse response) {
                System.out.println("getActivityData response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("getActivityData error=" + errorResponse.getErrors());
            }
        });
    ```


##### AI Scoring API

* ####### The *scoringSendFile* API allows user to send a file for bulk email validation
    ```java
    File myFile = new File("<FILE_PATH>");  // The csv or txt file
    int emailAddressColumn = 3;             // The column index of the email address in the file. Index starts at 1
    
    // sendFile and scoringSendFile throw the checked ZBException for an invalid argument,
    // such as an email column below 1, before anything is sent.
    try {
        ZeroBounceSDK.getInstance().scoringSendFile(
            myFile,
            emailAddressColumn,
            null,
            new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() {
                @Override
                public void onSuccess(ZBSendFileResponse response) {
                    System.out.println("scoringSendFile response=" + response.toString());
                }
            }, new ZeroBounceSDK.OnErrorCallback() {
                @Override
                public void onError(ErrorResponse errorResponse) {
                    System.out.println("scoringSendFile error=" + errorResponse.getErrors());
                }
            });
    } catch (ZBException e) {
        System.out.println("scoringSendFile rejected its arguments: " + e.getMessage());
    }
    ```

* ####### The *scoringGetFile* API allows users to get the validation results file for the file been submitted using *scoringSendFile* API
    ```java
    String fileId = "<FILE_ID>";                    // The returned file ID when calling scoringSendfile API
    String downloadPath = "<FILE_DOWNLOAD_PATH>";   // The path where the file will be downloaded
    
    ZeroBounceSDK.getInstance().scoringGetFile(
        fileId,
        downloadPath,
        new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() {
            @Override
            public void onSuccess(ZBGetFileResponse response) {
                System.out.println("scoringGetfile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("scoringGetfile error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Check the status of a file uploaded via *scoringSendFile* API
    ```java
    String fileId = "<FILE_ID>";    // The returned file ID when calling scoringSendfile API
    
    ZeroBounceSDK.getInstance().scoringFileStatus(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() {
            @Override
            public void onSuccess(ZBFileStatusResponse response) {
                System.out.println("scoringFileStatus response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("scoringFileStatus error=" + errorResponse.getErrors());
            }
        });
    ```

* ####### Deletes the file that was submitted using scoring *scoringSendFile* API. File can be deleted only when its status is `Complete`
    ```java
    String fileId = "<FILE_ID>";    // The returned file ID when calling scoringSendfile API
    
    ZeroBounceSDK.getInstance().scoringDeleteFile(
        fileId,
        new ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse>() {
            @Override
            public void onSuccess(ZBDeleteFileResponse response) {
                System.out.println("scoringDeleteFile response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(ErrorResponse errorResponse) {
                System.out.println("scoringDeleteFile error=" + errorResponse.getErrors());
            }
        });
    ```


#### Documentation
You can generate the documentation using your desired IDE or using's maven's javadoc command.


#### Publication

Publishing to Maven Central goes through the Sonatype Central Portal, using the **GitHub Actions `Publish` workflow** (`.github/workflows/publish.yml`). It is triggered **manually per tag**; it does not run on a push or on release creation.

1. Set `sdk.version` in `zero-bounce-sdk/pom.xml` to the new version, create a **git tag** for it (e.g. `v2.2.0`), and push the tag.
2. In the repo go to **Actions → Publish**, click **Run workflow**, enter the tag (e.g. `v2.2.0`), and run.
3. The workflow checks that the tag exists, then runs `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=... clean deploy -Prelease` in `zero-bounce-sdk`. That builds and tests the SDK, signs the jars with GPG, and the `central-publishing-maven-plugin` uploads them to the Central Portal. `autoPublish` is `true` in the `release` profile, so the deployment is published once the Portal has validated it, without a manual step.
4. When the publish job succeeds, the workflow creates a GitHub Release for the tag with generated release notes.

The workflow reads these secrets: `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` (a Central Portal user token, for the `central` server), `SIGNING_KEY` (the GPG private key) and `SIGNING_PASSWORD` (its passphrase). If any of them changes, update it on GitHub.

##### Local setup for manual release
To publish from your local machine instead of the workflow:

1. Import the GPG key to your local machine (see below).
2. Add a `<server>` with the id `central` to your `~/.m2/settings.xml`, with a Central Portal user token as its username and password.
3. If you want to check the deployment before it goes to Maven Central, set *autoPublish* to *false* for the `central-publishing-maven-plugin` in the `release` profile of `zero-bounce-sdk/pom.xml`.
4. From the **zero-bounce-sdk** folder, run:
    ```shell
    mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<YOUR_PASSPHRASE> clean deploy -Prelease
    ```

With *autoPublish* set to *false*, the upload waits on the [Central Portal deployments page](https://central.sonatype.com/publishing/deployments) until you publish (or drop) it there. Once published, the artifact appears on the [Maven Repository](https://repo1.maven.org/maven2/com/zerobounce/java/zerobouncesdk/) and on [Central search](https://central.sonatype.com/artifact/com.zerobounce.java/zerobouncesdk); the [MVN Repository](https://mvnrepository.com/artifact/com.zerobounce.java/zerobouncesdk) index picks it up later.


#### Exporting and importing PGP keys
1. Export the keys:
    ```shell
    gpg --list-keys  ### In order to obtain the key hash for the next step
    gpg --export -a <LAST_8_DIGITS> > public.key
    gpg --export-secret-key -a <LAST_8_DIGITS> > private key
    ```

2. Import the keys:
    ```shell
    gpg --import public.key
    gpg --import private.key
    ```
3. Check that the new keys are imported:
    ```shell
    gpg --list-keys
    gpg --list-secret-keys
    ```

## Validate options

`validate` has an overload that takes `ZBValidateOptions`. An option left unset
is not sent, and booleans go on the wire as the strings `"true"`/`"false"`.

```java
ZBValidateOptions options = new ZBValidateOptions()
        .setTimeout(30)                 // 3-60 seconds
        .setActivityData(true)          // fills getActiveInDays() / getActiveFirstSeen()
        .setVerifyPlus(true)
        .setDomainInfo(true)            // fills getDomainWebsiteExists() / getDomainRegistrantCompanyName()
        .setGreylistProcessing(true)
        .setCallbackUrl("https://example.com/greylist")
        .setUsePost(true);              // form-encoded POST /v2/validate instead of GET

ZeroBounceSDK.getInstance().validate("<EMAIL>", null, options,
        response -> System.out.println(response),
        error -> System.out.println(error.getErrors()));
```

`setCallbackUrl` is where the greylist retry posts its result, so it is rejected
without `setGreylistProcessing(true)` — before any request is sent. While that
retry is still running the API answers with a `Message` instead of a result,
which lands on `response.getMessage()`.

## Real-time AI scoring

```java
ZeroBounceSDK.getInstance().scoreEmail("<EMAIL>", onSuccess, onError);        // GET /v2/scoring
ZeroBounceSDK.getInstance().scoreEmail("<EMAIL>", true, onSuccess, onError);  // form-encoded POST
```

## Filters

```java
ZeroBounceSDK.getInstance().addFilter("block", "domain", "example.com", onSuccess, onError);
ZeroBounceSDK.getInstance().listFilters(false, onSuccess, onError); // true sends a POST
ZeroBounceSDK.getInstance().deleteFilter("block", "domain", "example.com", onSuccess, onError);
```

## S3 and SFTP/FTP bulk

Queue a job on a file that already sits in your Amazon S3 bucket, or on your
SFTP/FTP server. See the [S3](https://www.zerobounce.net/docs/api-integrations/amazon-s3-bulk-api)
and [SFTP](https://www.zerobounce.net/docs/api-integrations/sftp_bulk_api) docs.
These live on the bulkapi host *without* the `/v2` path.

```java
ZeroBounceSDK zb = ZeroBounceSDK.getInstance();

// Amazon S3. The file name is the full path, as returned by listS3Files.
zb.listS3Files(null, null, onSuccess, onError);   // optional bucket, search

// file_name and return_url are required on every one of these jobs.
ZBBulkFileJobOptions s3 = new ZBBulkFileJobOptions()
        .setFileName("lists/leads.csv")
        .setHasHeaderRow(true)
        .setReturnUrl("https://example.com/zerobounce-done");

zb.validateS3File(s3, onSuccess, onError);
zb.scoreS3File(s3, onSuccess, onError);
zb.emailFinderS3File(s3, onSuccess, onError);
zb.domainSearchS3File(new ZBBulkFileJobOptions()
        .setFileName("lists/leads.csv")
        .setRemoveDuplicates(true)
        .setReturnUrl("https://example.com/zerobounce-done"), onSuccess, onError);
zb.exportS3File("<FILE_ID>", "my-export-bucket", onSuccess, onError);

// validateS3File and scoreS3File also take an options object with the column
// indexes those two jobs accept. They send the same required fields, so
// file_name and return_url are required on these overloads as well.
zb.validateS3File(new ZeroBounceSDK.ValidateS3FileOptions()
        .setFileName("lists/leads.csv")
        .setReturnUrl("https://example.com/zerobounce-done")
        .setEmailAddressColumn(1)
        .setAllowPhase2(true), onSuccess, onError);

zb.scoreS3File(new ZeroBounceSDK.ScoreS3FileOptions()
        .setFileName("lists/leads.csv")
        .setReturnUrl("https://example.com/zerobounce-done")
        .setEmailAddressColumn(1), onSuccess, onError);

// SFTP/FTP. The server, path and protocol come from your ZeroBounce settings.
// The docs list file_name as optional for validateSFTPFile, but the live API
// requires it there too, so the SDK rejects a job without it.
ZBBulkFileJobOptions sftp = new ZBBulkFileJobOptions()
        .setFileName("my_file.txt")
        .setReturnUrl("https://example.com/zerobounce-done");

zb.validateSFTPFile(sftp, onSuccess, onError);
zb.scoreSFTPFile(sftp, onSuccess, onError);
zb.emailFinderSFTPFile(sftp, onSuccess, onError);
zb.domainSearchSFTPFile(sftp, onSuccess, onError);

// Note the field: this one takes export_path, not export_bucket.
// Pass null to use the export path from your settings.
zb.exportSFTPFile("<FILE_ID>", "/exports", onSuccess, onError);
```

## List evaluator

The list evaluator has its own base URL, not a path built from the bulk one, and
the file id is URL-encoded as a single path segment.

```java
ZeroBounceSDK.getInstance().getListEvaluatorResult("<FILE_ID>", onSuccess, onError);
```

## Inbox Placement Tester

Runs on its own host, authenticates with an `Authorization: Bearer` header (the
key never goes on the query string), and sends camelCase JSON bodies. It spends the account's inbox-test credits.

```java
ZeroBounceSDK zb = ZeroBounceSDK.getInstance();

zb.getInboxPlacementProviders(onSuccess, onError);
zb.getInboxPlacementTestTypes(onSuccess, onError);

// The name is at most 64 characters and testType is 1, 2 or 3. A type 1 test
// mails from your own address, so senderEmail is required with it. All three
// are rejected before the request is sent.
zb.createInboxPlacementTest("Launch",
        new ZeroBounceSDK.CreateInboxPlacementTestOptions()
                .setTestType(1)
                .setSenderEmail("news@example.com")
                .setWebhookUrl("https://example.com/ipt-webhook"),
        onSuccess, onError);

// Send your campaign to the seeds you got back, then read the results.
zb.getInboxPlacementTest("<TEST_ID>", onSuccess, onError);

// The page size goes on the wire as page_size; as pageSize the API ignores it
// and answers with the whole list.
zb.listInboxPlacementTests(
        new ZeroBounceSDK.ListInboxPlacementTestsOptions()
                .setStatus("finished")
                .setPage(1)
                .setPageSize(25)
                .setCreatedAfter("2026-01-01")
                .setCreatedBefore("2026-02-01"),
        onSuccess, onError);
zb.setInboxPlacementWebhook("https://example.com/ipt-webhook", onSuccess, onError);
zb.getInboxPlacementWebhook(onSuccess, onError);
zb.deleteInboxPlacementTest("<TEST_ID>", onSuccess, onError);
```

## Per-API base URLs

The list evaluator and the Inbox Placement Tester are separate hosts, so
overriding the main base URL alone leaves them pointing at the live API.

```java
ZeroBounceSDK.getInstance().initialize(
        "<API_KEY>",
        "https://api.zerobounce.net/v2",
        "https://bulkapi.zerobounce.net/v2/listevaluator",
        "https://bulkapi.zerobounce.net/api/ipt");
```

