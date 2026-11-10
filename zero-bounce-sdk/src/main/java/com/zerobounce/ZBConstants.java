package com.zerobounce;

/**
 * Defines API endpoint constants. This object is a singleton and provides
 * global access to the constants.
 * <p>
 * Each value ends in a slash; {@code ZeroBounceSDK.initialize(apiKey, apiBaseUrl)}
 * removes it, so requests go to {@code .../v2/validate} and not {@code .../v2//validate}.
 */
public class ZBConstants {

    public static final String API_DEFAULT_URL = "https://api.zerobounce.net/v2/";

    public static final String API_USA_URL = "https://api-us.zerobounce.net/v2/";

    public static final String API_EU_URL = "https://api-eu.zerobounce.net/v2/";
}
