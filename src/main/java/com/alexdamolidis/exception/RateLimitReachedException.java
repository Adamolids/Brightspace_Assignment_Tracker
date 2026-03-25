package com.alexdamolidis.exception;

public class RateLimitReachedException extends TrackerException{

    public RateLimitReachedException(String message){
        super(message);
    }

    public RateLimitReachedException(String message, Throwable e){
        super(message, e);
    }
    
}
