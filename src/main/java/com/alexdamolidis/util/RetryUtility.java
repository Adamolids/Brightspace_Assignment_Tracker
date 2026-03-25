package com.alexdamolidis.util;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.RateLimitException;
import com.alexdamolidis.exception.RateLimitReachedException;

public class RetryUtility {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtility.class);
    
    /**
     * Executes a task with exponential backoff if a RateLimitException is thrown.
     * 
     * @param task the code to execute, must return a value
     * @param serviceName name for logging
     * @return the result of the task
     * @throws RuntimeException if the request thread is interrupted
     * @throws RateLimitReachedException if the task fails on the third retry
     */
    public static <T> T executeWithRetry(Supplier<T> task, String serviceName){

        int retryCount = 0;
        while(retryCount < 3){
            try{
                return task.get();

            } catch(RateLimitException e){
                retryCount++;
                long sleepMilis = (long) Math.pow(2, retryCount) * 1000;
                logger.warn("{} rate limited. Retry {}/3 in {}ms...", serviceName, retryCount, sleepMilis);
            
                try{
                    if(retryCount >= 3)break;
                    Thread.sleep(sleepMilis);
                } catch(InterruptedException ie){
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("retry interrupted.", ie);
                }
            }
        }
        throw new RateLimitReachedException(serviceName + " failed after 3 retries, stopping to protect API quota.");
    }
}