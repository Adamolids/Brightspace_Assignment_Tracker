package com.alexdamolidis.util;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
     * Contructor only for testing requests
     */
    public BrightspaceClient(HttpClient httpClient, SessionService sessionService){
        this.httpClient = httpClient;
        this.sessionService = sessionService;
        this.cookieManager = null;
    }

    /**
     * sends a GET request to specified URL and returns the response body.
     * 
     * @param url the destination URL for the GET request.
     * 
     * @returns body of response as a String.
     * 
     * @throws RuntimeException If a network error occurs, the request is interrupted,
     *      the server returns a non 200 status code, or the response is not a valid JSON.
     */
    public String sendGetRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); //

            int status = response.statusCode();
            if (status != 200) {
                switch (status) {
                case 401 -> throw new RuntimeException("401 Unauthorized: Session is invalid or cookies have expired.");
                case 403 -> throw new RuntimeException("403 Forbidden: You do not have permission to access this resource.");
                case 429 -> throw new RuntimeException("429 Too Many Requests: Rate limit exceeded. Slow down the sync frequency.");
                case 404 -> throw new RuntimeException("404 Not Found: The API endpoint is incorrect. Check URL: " + url);
                default  -> throw new RuntimeException("Brightspace returned an unexpected HTTP " + status);
                }
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.contains("application/json")) {
                throw new RuntimeException("Expected JSON but received: " + contentType + ". Please check session"); //
            }

            return response.body();

        } catch (IOException e) {
            throw new RuntimeException("Network failure while calling: " + url, e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Request was interrupted", e);
        }
    }

    /**
     * Sends a GET request to downlaod binary content(attachments). Accepts any content type.
     * 
     * @param url The destination URL for the downlaod.
     * 
     * @return array of bytes comprised of body contents from response.
     * 
     * @throws RuntimeException If the network call fails, the request is interrupted,
     * or the server returns a non 200 status code.
     */
    public byte[] downloadAttachment(String url){
        try{
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Error while fetching attachment: " + response.statusCode());
            }
            return response.body();

        }catch(IOException e){
            throw new RuntimeException("Network error during attachment download from: " + url, e);

        }catch(InterruptedException e){
            throw new RuntimeException("Download was interrupted for URL: " + url, e);
        }
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }
}