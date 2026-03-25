package com.alexdamolidis.calendar;

import com.alexdamolidis.exception.CalendarAuthException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CalendarAuthHelper {

    private static final String CREDENTIALS_FILE = "googleAuth.json";
    private static final String TOKENS_DIR       = "tokens";
    private static final List<String> SCOPES     = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);

    public static Credential authorize(HttpTransport transport, JsonFactory jsonFactory){
        
        try (FileReader reader = new FileReader(CREDENTIALS_FILE)) {

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);
            
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport,
                    jsonFactory,
                    clientSecrets,
                    SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIR)))
                    .setAccessType("offline")
                    .build();
            
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8080).build();

            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        } catch(FileNotFoundException e){
            throw new CalendarAuthException("googleAuth.json not found in project root. Please refer to README.", e);
            
        } catch(IOException e){
            throw new CalendarAuthException("Failed to establish a secure connection to Google Calendar.", e);
        }
    }
}