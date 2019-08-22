## Zero Bounce Java SDK
This SDK contains methods for interacting easily with ZeroBounce API.
More information about ZeroBounce you can find in the [official documentation](https://www.zerobounce.net/docs/).

## INSTALLATION
```bash

```

## USAGE
Import the sdk in your file:
```java
import com.zerobounce;
``` 

Initialize the sdk with your api key:
```java 
ZeroBounceSDK zeroBounce = new ZeroBounceSDK();
zeroBounce.initialize("<YOUR_API_KEY>");
```

## Examples
Then you can use any of the SDK methods:
```java
# Validate an email address
zeroBounce.validate(
        "<ANY_EMAIL_ADDRESS>",
        "<OPTIONAL_IP_ADDRESS>",
        <SUCCESS_CALLBACK>,
        <FAILURE_CALLBACK>
);

# Check how many credits you have left on your account
zeroBounce.get_credits(
        <SUCCESS_CALLBACK>,
        <FAILURE_CALLBACK>
);

# Send a file for bulk email validation
zerobouncesdk.send_file(
        <FILE_TO_SEND>,
        <EMAIL_ADDRESS_COLUMN>,
        <SUCCESS_CALLBACK>,
        <FAILURE_CALLBACK>
);
```  
