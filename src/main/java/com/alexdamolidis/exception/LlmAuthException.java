package com.alexdamolidis.exception;

/**
 * The LLM provider rejected the API key. Every other request would fail the same way, so enrichment
 * stops and the run fails instead of carrying on without estimates.
 */
public class LlmAuthException extends TrackerException{

    public LlmAuthException(String message, Throwable e){
        super(message, e);
    }
}
