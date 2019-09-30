## ZeroBounce Java SDK
This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).

## USAGE
Initialize the sdk with your api key:
```java 
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");
```

## Examples
Then you can use any of the SDK methods, for example:
* ##### Validate an email address
```java
ZeroBounceSDK.getInstance().validate("<ANY_EMAIL_ADDRESS>", "<OPTIONAL_IP_ADDRESS>",
    new ZeroBounceSDK.OnSuccessCallback<ZBValidateResponse>() {
        @Override
        public void onSuccess(ZBValidateResponse response) {
            System.out.println("validate response=" + response.toString());
        }
    }, new ZeroBounceSDK.OnFailureCallback() {
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
    }, new ZeroBounceSDK.OnFailureCallback() {
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
    }, new ZeroBounceSDK.OnFailureCallback() {
        @Override
        public void onError(String errorMessage) {
            System.out.println("getCredits error=" + errorMessage);
        }
    });
```
* ##### The sendfile API allows user to send a file for bulk email validation
```java
File myFile = new File("<FILE_PATH>");  // The csv or txt file
int emailAddressColumn = 3;             // The column index of the email address in the file. Index starts at 1

ZeroBounceSDK.getInstance().sendFile(
    myFile,
    emailAddressColumn,
    new ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse>() {
        @Override
        public void onSuccess(ZBSendFileResponse response) {
            System.out.println("sendFile response=" + response.toString());
        }
    }, new ZeroBounceSDK.OnFailureCallback() {
        @Override
        public void onError(String errorMessage) {
            System.out.println("getCredits error=" + errorMessage);
        }
    });
```
* ##### The getfile API allows users to get the validation results file for the file been submitted using sendfile API
```java
String fileId = "<FILE_ID>";                    // The returned file ID when calling sendfile API
String downloadPath = "<FILE_DOWNLOAD_PATH>";   // The path where the file will be downloaded

ZeroBounceSDK.getInstance().getfile(
    fileId,
    downloadPath,
    new ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse>() {
        @Override
        public void onSuccess(ZBGetFileResponse response) {
            System.out.println("getfile response=" + response.toString());
        }
    }, new ZeroBounceSDK.OnFailureCallback() {
        @Override
        public void onError(String errorMessage) {
            System.out.println("getCredits error=" + errorMessage);
        }
    });
```
* ##### Check the status of a file uploaded via "sendFile" method
```java
String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API

ZeroBounceSDK.getInstance().fileStatus(
    fileId,
    new ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse>() {
        @Override
        public void onSuccess(ZBFileStatusResponse response) {
            System.out.println("fileStatus response=" + response.toString());
        }
    }, new ZeroBounceSDK.OnFailureCallback() {
        @Override
        public void onError(String errorMessage) {
            System.out.println("getCredits error=" + errorMessage);
        }
    });
```
* ##### Deletes the file that was submitted using scoring sendfile API. File can be deleted only when its status is _`Complete`_
```java
String fileId = "<FILE_ID>";    // The returned file ID when calling sendfile API

ZeroBounceSDK.getInstance().deleteFile(
    fileId,
    new ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse>() {
        @Override
        public void onSuccess(ZBDeleteFileResponse response) {
            System.out.println("deleteFile response=" + response.toString());
        }
    }, new ZeroBounceSDK.OnFailureCallback() {
        @Override
        public void onError(String errorMessage) {
            System.out.println("getCredits error=" + errorMessage);
        }
    });
```
