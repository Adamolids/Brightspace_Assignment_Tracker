package com.alexdamolidis.exception;

public class DatabaseTransactionException extends TrackerException {
    
    public DatabaseTransactionException(String message){
        super(message);
    }
    public DatabaseTransactionException(String message, Throwable e){
        super(message, e);
    }
}
