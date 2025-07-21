## ZeroBounce Java SDK

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.java/zerobouncesdk/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.zerobounce.java/zerobouncesdk) [![Build Status](https://github.com/zerobounce/zero-bounce-java-sdk-setup/actions/workflows/publish.yml/badge.svg)](https://github.com/zerobounce/zero-bounce-java-sdk-setup/actions/workflows/publish.yml)

This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).\
This SDK is built using the Java 1.8 version.


### Installation

You can install ZeroBounceSDK by adding the dependency to your `pom.xml` file:
```xml
<dependency>
    <groupId>com.zerobounce.java</groupId>
    <artifactId>zerobouncesdk</artifactId>
    <version>1.2.1</version>
</dependency>
```


## How to use the sample project

1. Build the JAR file for the SDK project.
2. Make sure there's no other JAR located in the maven cache file. On my machine, it is located here: `~/.m2/repository/com/zerobounce/java/zerobouncesdk/`
3. Run the following command from the root of the project:
    ```shell
    mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
        -Dfile=zero-bounce-sdk/out/artifacts/zerobouncesdk_jar/zerobouncesdk.jar \
        -DgroupId=com.zerobounce.java \
        -DartifactId=zerobouncesdk \
        -Dversion=1.2.1 \
        -Dpackaging=jar \
        -DlocalRepositoryPath=local-libs
    ```
4. Run `mvn clean dependency:purge-local-repository` in the Sample project.
5. Run `mvn compile` in the Sample project.
6. Rebuild the Sample project.
7. Run & enjoy!


## How to use the SDK from this repository in your project

We highly recommend you use the latest version available on Maven. However, if you plan on bundling the repository version of the SDK in your project, then here's what you need to do:
1. Create a folder named *local-libs* in the root of your project, that will act as a "local" repository.
2. In your `pom.xml` file paste the following code that will use a "local" repository:
    ```xml
    <repositories>
        <repository>
            <id>local-repo</id>
            <name>Local Repo</name>
            <url>file://${project.basedir}/local-libs</url>
        </repository>
    </repositories>
    ```
3. Inside the `<dependencies></dependencies>` block, paste the following code:
    ```xml
    <dependency>
        <groupId>com.zerobounce.java</groupId>
        <artifactId>zerobouncesdk</artifactId>
        <version>1.2.1</version>
    </dependency>
    ```
4. Follow steps 1-5 from the ***How to use the sample project*** above.
5. Rebuild the project.
6. Enjoy!


## How to deploy the SDK

1. Be sure to set the *autoReleaseAfterClose* field to *false* in the `pom.xml` file of the **zero-bounce-sdk** if you don't want the artifact to be automatically deployed on Maven Central.
2. Use the command: `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<YOUR_PASSPHRASE> clean deploy -Prelease` from the **zero-bounce-sdk** folder.


## USAGE

Initialize the sdk with your api key:
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");
```
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>", timeoutInMillis);
```


## Examples

Then you can use any of the SDK methods, for example:

* ##### Validate an email address
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
            public void onError(String errorMessage) {
                System.out.println("validate error=" + errorMessage);
            }
        });
    );
    ```

* ##### Validate batch a list of email addresses
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
            public void onError(String errorMessage) {
                System.out.println("validateBatch error=" + errorMessage);
            }
        });
    );
    ```

* ##### Find the email and other domain formats based on a given domain name
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
            public void onError(String errorMessage) {
                System.out.println("guessFormat error=" + errorMessage);
            }
        });
    );
    ```

* ##### Check how many credits you have left on your account
    ```java
    ZeroBounceSDK.getInstance().getCredits(
        new ZeroBounceSDK.OnSuccessCallback<ZBCreditsResponse>() {
            @Override
            public void onSuccess(ZBCreditsResponse response) {
                System.out.println("getCredits response=" + response.toString());
            }
        }, new ZeroBounceSDK.OnErrorCallback() {
            @Override
            public void onError(String errorMessage) {
                System.out.println("getCredits error=" + errorMessage);
            }
        });
    ```

* ##### Check your API usage for a given period of time
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
            public void onError(String errorMessage) {
                System.out.println("getApiUsage error=" + errorMessage);
            }
        });
    ```

* ##### The *sendFile* API allows user to send a file for bulk email validation
    ```java
    File myFile = new File("<FILE_PATH>");  // The csv or txt file
    int emailAddressColumn = 3;             // The column index of the email address in the file. Index starts at 1
    
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
            public void onError(String errorMessage) {
                System.out.println("sendFile error=" + errorMessage);
            }
        });
    ```

* ##### The *getFile* API allows users to get the validation results file for the file been submitted using *sendFile* API
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
            public void onError(String errorMessage) {
                System.out.println("getfile error=" + errorMessage);
            }
        });
    ```

* ##### Check the status of a file uploaded via *sendFile* API
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
            public void onError(String errorMessage) {
                System.out.println("fileStatus error=" + errorMessage);
            }
        });
    ```

* ##### Delete the file that was submitted using *sendFile* API. File can be deleted only when its status is `Complete`
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
            public void onError(String errorMessage) {
                System.out.println("deleteFile error=" + errorMessage);
            }
        });
    ```

* ##### Gather insights into your subscribers’ overall email engagement. The request returns data regarding opens, clicks, forwards and unsubscribes that have taken place in the past 30, 90, 180 or 365 days.
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
            public void onError(String errorMessage) {
                System.out.println("getActivityData error=" + errorMessage);
            }
        });
    ```


### AI Scoring API

* ##### The *scoringSendFile* API allows user to send a file for bulk email validation
    ```java
    File myFile = new File("<FILE_PATH>");  // The csv or txt file
    int emailAddressColumn = 3;             // The column index of the email address in the file. Index starts at 1
    
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
            public void onError(String errorMessage) {
                System.out.println("scoringSendFile error=" + errorMessage);
            }
        });
    ```

* ##### The *scoringGetFile* API allows users to get the validation results file for the file been submitted using *scoringSendFile* API
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
            public void onError(String errorMessage) {
                System.out.println("scoringGetfile error=" + errorMessage);
            }
        });
    ```

* ##### Check the status of a file uploaded via *scoringSendFile* API
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
            public void onError(String errorMessage) {
                System.out.println("scoringFileStatus error=" + errorMessage);
            }
        });
    ```

* ##### Deletes the file that was submitted using scoring *scoringSendFile* API. File can be deleted only when its status is `Complete`
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
            public void onError(String errorMessage) {
                System.out.println("scoringDeleteFile error=" + errorMessage);
            }
        });
    ```


## Documentation
You can generate the documentation using your desired IDE or using's maven's javadoc command.


## Publication
Every time a new release is created, the CI/CD pipeline will execute and a new artifact will be released on Maven Central. Don't forget to update the version before doing a release!
If you ever change the OSSRH login credentials, you'll need to also update the repository variables on Github.


### Local setup for manual release
In order to be able to publish to the Nexus repository from you local machine, you'll need to do the following step:
If you want to manually publish to the Nexus repository (and then release it to Maven Central), you should:

1. Import the GPG key to your local machine (see below)
2. Set the *autoReleaseAfterClose* inside the zero-bounce-sdk's `pom.xml` to *false*.
3. Run the following command:
    ```shell
    # For publishing to the staging repository
    mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<YOUR_PASSPHRASE> clean deploy -Prelease
    ```

You should then go to the [Nexus Sonatype](https://s01.oss.sonatype.org/), login and then open *Staging Repositories* and click on *Refresh*. Here you'll see the artifact you just uploaded. In order to publish it, you have to **close** it and then **release** it. These actions will take a few minutes to complete. After **releasing** the artifact, it will take:
- a few hours before you can see it on the [Maven Repository](https://repo1.maven.org/maven2/com/zerobounce/java/zerobouncesdk/) and on the [Sonatype Search](https://central.sonatype.com/artifact/com.zerobounce.java/zerobouncesdk/1.1.6)
- 1-3 days before you can see it on the [MVN Repository](https://mvnrepository.com/artifact/com.zerobounce.java/zerobouncesdk)


## Exporting and importing PGP keys
1. Export the keys:
    ```shell
    gpg --list-keys  # In order to obtain the key hash for the next step
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
