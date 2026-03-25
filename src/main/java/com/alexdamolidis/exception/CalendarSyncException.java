package com.alexdamolidis.exception;

public class CalendarSyncException extends TrackerException{

    public CalendarSyncException(String message){
        super(message);
    }

    public CalendarSyncException(String message, Throwable cause){
        super(message, cause);
    }
}