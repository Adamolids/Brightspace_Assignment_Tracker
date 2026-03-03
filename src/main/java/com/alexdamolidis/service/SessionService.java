package com.alexdamolidis.service;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;

import com.alexdamolidis.parser.StringParser;
import com.alexdamolidis.util.ContentExtractor;
import com.alexdamolidis.util.EndpointBuilder;

public class SessionService {
    
    /**
     * extracts session cookie data from cookies.txt, calls parseCookies to 
     * clean and set all relevant settings on each cookie, adds cookies to CookieStore.
     * 
     * @param  manager CookieManager object
     * 
     * @throws IOException if the cookies.txt file cannot be read.
    */
    public void initializeSession(CookieManager manager){
        String           cookieString = ContentExtractor.readFirstLine("cookies.txt");
        List<HttpCookie> cookies      = StringParser.parseCookies(cookieString);
        URI              uri          = EndpointBuilder.getBaseUri();

        for(HttpCookie cookie : cookies){
            manager.getCookieStore().add(uri, cookie);
        }
    }
}