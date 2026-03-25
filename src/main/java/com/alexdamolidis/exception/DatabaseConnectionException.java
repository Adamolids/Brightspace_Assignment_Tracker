package com.alexdamolidis.exception;

public class DatabaseConnectionException extends TrackerException {
    
    public DatabaseConnectionException(String message){
        super(message);
    }
    public DatabaseConnectionException(String message, Throwable e){
        super(message, e);
    }
}
