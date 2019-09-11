## ZeroBounce Java SDK
This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).

## INSTALLATION
```bash

```

## USAGE
Import the sdk in your file:
```java

``` 

Initialize the sdk with your api key:
```java 
ZeroBounceSDK.getInstance().initialize("<YOUR_API_KEY>");
```

## Examples
Then you can use any of the SDK methods, for example:
- Validate an email address
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
- Check how many credits you have left on your account
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
