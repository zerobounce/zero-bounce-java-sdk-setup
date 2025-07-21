package com.zerobounce;

/**
 * A model class used for throwing exceptions related to the SDK (validation errors any other kind of errors that are
 * unrelated to the server's response). This class is not and should not be used for throwing any kind of server
 * errors. For error responses, use [ErrorResponse] instead.
 */
public class ZBException extends Exception {

    public ZBException(String message) {
        super(message);
    }
}
