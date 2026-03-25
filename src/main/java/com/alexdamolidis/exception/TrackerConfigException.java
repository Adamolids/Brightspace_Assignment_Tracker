package com.alexdamolidis.exception;

public class TrackerConfigException extends TrackerException{
    
    public TrackerConfigException(String message){
        super(message);
    }
    public TrackerConfigException(String message, Throwable e){
        super(message, e);
    }

}
