package com.alexdamolidis.util;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.alexdamolidis.exception.AttachmentProcessingException;
import com.alexdamolidis.exception.BrightspaceSyncException;
import com.alexdamolidis.service.SessionService;

public class BrightspaceClient {

    private final CookieManager cookieManager;
    private final HttpClient httpClient;
    private final SessionService sessionService;

    /**
     * Initializes a new BrightspaceClient instance. Sets up a CookieManager and HttpClient with appropriate settings,
     * then calls SessionService to load session cookies from cookies.txt into the CookieManager.
     */
    public BrightspaceClient() {
        this.sessionService = new SessionService();
        this.cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        sessionService.initializeSession(cookieManager);
        this.httpClient = HttpClient.newBuilder().cookieHandler(cookieManager)
                                    .connectTimeout(Duration.ofSeconds(10))
                                    .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Contructor only for testing, dependency injection.
     */
    public BrightspaceClient(HttpClient httpClient, SessionService sessionService){
        this.httpClient = httpClient;
        this.sessionService = sessionService;
        this.cookieManager = null;
    }

    /**
     * sends a GET request to specified URL and returns the response body. Utilizes 
     * executeWithRetry for exponential backoff on 429 status code.
     * 
     * @param url the destination URL for the GET request 
     * @return body of response as a String
     * @throws BrightspaceSyncException if a network error occurs, or the response is not a valid JSON
     * @throws RuntimeException if the request thread is interrupted
     */
    public String sendGetRequest(String url) {

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/json").GET().build();
        
        return RetryUtility.executeWithRetry(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                HttpValidator.validate(response, "Brightspace API");

                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (!contentType.contains("application/json")) {
                    throw new BrightspaceSyncException("Expected JSON but received: " + contentType + ". Please check session"); 
                }

                return response.body();

            } catch (IOException e) {
                throw new BrightspaceSyncException("Network failure while calling: " + url, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request was interrupted", e);
            }
        }, "Brightspace API");
    }

    /**
     * Sends a GET request to download binary content(attachments). Accepts any content type.
     * 
     * @param url The destination URL for the download
     * @return array of bytes comprised of body contents from response
     * @throws AttachmentProcessingException if a network error occurs, or the response is not a valid JSON
     * @throws RuntimeException if the request thread is interrupted
     */
    public byte[] downloadAttachment(String url){

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        return RetryUtility.executeWithRetry( () -> {
            try{
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                HttpValidator.validate(response, "Brightspace API");

                return response.body();

            }catch(IOException e){
                throw new AttachmentProcessingException("Network error during attachment download from: " + url, e);

            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new RuntimeException("Download was interrupted for URL: " + url, e);
            }
        }, "Brightspace API");
    }    

    public HttpClient getHttpClient() {
        return this.httpClient;
    }
}