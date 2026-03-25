package com.alexdamolidis.exception;

public class BrightspaceSyncException extends TrackerException{
    
    public BrightspaceSyncException(String message){
        super(message);
    }
    
    public BrightspaceSyncException(String message, Throwable e){
        super(message, e);
    }
}
