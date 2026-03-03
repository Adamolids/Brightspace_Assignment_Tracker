package com.alexdamolidis.parser;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;

import com.alexdamolidis.util.EndpointBuilder;

public class StringParser {

    /**
     * seperates, creates, and sets cookie settings(path, domain, version)
     * for each cookie provided.
     * 
     * @param cookieString  String containing all session cookie data
     * 
     * @returns cookies ArrayList containing all session cookies
     */
    public static List<HttpCookie> parseCookies(String cookieString){
        List<HttpCookie> cookies = new ArrayList<>();

        String[] pairs = cookieString.split(";");
        
        for(String pair : pairs){
            String[] nameValue = pair.trim().split("=", 2);
            if(nameValue.length == 2){
                HttpCookie cookie = new HttpCookie(nameValue[0], nameValue[1]);
                cookie.setPath("/");    
                cookie.setDomain(EndpointBuilder.getDomain()); 
                cookie.setVersion(0);
                cookies.add(cookie);
            }
        }
        return cookies;
    }

    /**
     * utilizes jsoup to remove all html tags from a String.
     *
     * @param htmlText  String with html tags that should be removed
     * 
     * @return cleaned String with no html tags
    */
    public static String cleanHtml(String htmlText){
        if(htmlText == null)return "";

        return Jsoup.parse(htmlText).text();
    }
}