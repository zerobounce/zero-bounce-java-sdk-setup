## ZeroBounce Java SDK
This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).\
This SDK is built using the Java 1.8 version.


## How to use the sample project

1. Build the JAR file for the SDK project.
2. Make sure there's no other JAR located in the maven cache file. On my machine, it is located here: `/.m2/repository/net/zerobounce/zerobouncesdk/`
3. Run the following command from the root of the project:
    ```shell
    mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
        -Dfile=zero-bounce-sdk/out/artifacts/zerobouncesdk_jar/zerobouncesdk.jar \
        -DgroupId=net.zerobounce \
        -DartifactId=zerobouncesdk \
        -Dversion=1.0 \
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
        <groupId>net.zerobounce</groupId>
        <artifactId>zerobouncesdk</artifactId>
        <version>1.0</version>
    </dependency>
    ```
4. Follow steps 1-5 from the ***How to use the sample project*** above.
5. Rebuild the project.
6. Enjoy!


## How to deploy the SDK

1. Be sure to set the *autoReleaseAfterClose* field to *false* in the `pom.xml` file of the **zero-bounce-sdk** if you don't want the artifact to be automatically deployed on Maven Central.
2. Use the same command found in the `publish.yml` file: `mvn --no-transfer-progress --batch-mode -Dgpg.passphrase=<YOUR_PASSPHRASE> clean deploy -Prelease` from the **zero-bounce-sdk** folder.


## USAGE

Initialize the sdk with your api key:
```java
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");
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
