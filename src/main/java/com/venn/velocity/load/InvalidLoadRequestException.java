package com.venn.velocity.load;

/**
 * Thrown when a load attempt cannot be interpreted (missing fields, malformed amount or timestamp).
 */
public class InvalidLoadRequestException extends RuntimeException {

    public InvalidLoadRequestException(String message) {
        super(message);
    }

    public InvalidLoadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
