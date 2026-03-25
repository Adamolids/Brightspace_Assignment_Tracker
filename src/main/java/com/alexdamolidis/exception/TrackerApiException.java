package com.alexdamolidis.exception;

public class TrackerApiException extends TrackerException{
 
    public TrackerApiException(String message){
        super(message);
    }
 
    public TrackerApiException(String message, Throwable e){
        super(message, e);
    }
    
}