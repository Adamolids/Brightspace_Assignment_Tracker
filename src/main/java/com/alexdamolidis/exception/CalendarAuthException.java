package com.alexdamolidis.exception;

public class CalendarAuthException extends TrackerException{

    public CalendarAuthException(String message){
        super(message);
    }

    public CalendarAuthException(String message, Throwable cause){
        super(message, cause);
    }
}
