package com.alexdamolidis.util;

import java.net.http.HttpResponse;

import com.alexdamolidis.exception.RateLimitException;
import com.alexdamolidis.exception.TrackerApiException;

public class HttpValidator {
    
    /**
     * Helper method to validate api calls. Throws a RateLimitException when
     * encountering a 429 status code for retry utility.
     * 
     * @param response HttpResponse of any type
     * @param serviceName name of api being called for logging
     * @throws TrackerApiException when encountering a non 200 status code
     * @throws RateLimitException when encountering a 429 status code
     */
    public static void validate(HttpResponse<?> response, String serviceName){
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return;
        }

        String body = response.body() != null ? response.body().toString() : "No response body";
        
        switch (status) {
            case 401 -> throw new TrackerApiException(serviceName + " Error: 401 Unauthorized. Check your session cookies/API keys.");
            case 403 -> throw new TrackerApiException(serviceName + 
                        " Error: 403 Forbidden. This usually means your cookies are missing or Brightspace is blocking the request.");
            case 404 -> throw new TrackerApiException(serviceName + " Error: 404 Not Found. Endpoint might be incorrect.");
            case 429 -> throw new RateLimitException (serviceName + " Error: 429 Rate Limit. Slowing down requests.");
            case 500 -> throw new TrackerApiException(serviceName + " Error: 500 Internal Server Error. The server is having a bad day.");
            default  -> throw new TrackerApiException(serviceName + " Error: Unexpected HTTP " + status + ". Raw response: " + body);
        }
    }
}