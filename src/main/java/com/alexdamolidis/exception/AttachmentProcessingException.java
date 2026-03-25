package com.alexdamolidis.exception;

public class AttachmentProcessingException extends TrackerException {

    public AttachmentProcessingException(String message) {
        super(message);
    }

    public AttachmentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}