
package com.kycautomate.customer.exception;

public class DocumentServiceUnavailableException
        extends RuntimeException {

    public DocumentServiceUnavailableException(String message) {
        super(message);
    }

    public DocumentServiceUnavailableException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
