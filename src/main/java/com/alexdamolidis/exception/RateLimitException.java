package com.alexdamolidis.exception;

public class RateLimitException extends TrackerException{
    
    public RateLimitException(String message){
        super(message);
    }

    public RateLimitException(String message, Throwable cause){
        super(message, cause);
    }
}