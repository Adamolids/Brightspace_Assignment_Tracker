package com.alexdamolidis.exception;

public class TrackerException extends RuntimeException{

    public TrackerException(String message){
        super(message);
    }

    public TrackerException(String message, Throwable e){
        super(message, e);
    }
}
