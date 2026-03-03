package com.alexdamolidis.util;

import java.net.URI;

public class EndpointBuilder {
    private static final String baseUrl    = "https://slate.sheridancollege.ca";
    private static final URI    baseUri    = URI.create(baseUrl);
    private static final String domain     = "slate.sheridancollege.ca";
    private static final String apiVersion = "1.45";
    
    private EndpointBuilder(){}

/// https://slate.sheridancollege.ca/d2l/api/lp/{ apiVersion }/enrollments/myenrollments/?orgUnitTypeId=3
    public static String buildMyEnrollmentsUrl(){
        return baseUrl + "/d2l/api/lp/" + apiVersion + "/enrollments/myenrollments/?orgUnitTypeId=3";
    }

/// https://slate.sheridancollege.ca/d2l/api/le/{ apiVersion }/{ orgUnitId }/dropbox/folders/
    public static String buildAllAssignmentsUrl(String orgUnitId){
        return baseUrl + "/d2l/api/le/" + apiVersion + "/" + orgUnitId + "/dropbox/folders/";
    }

/// https://slate.sheridancollege.ca/d2l/api/le/{ apiVersion }/{ orgUnitId }/dropbox/folders/{ folderId }/attachments/{ fileId }
    public static String buildAttachmentUrl(String orgUnitId, String folderId, String fileId){
        return baseUrl + "/d2l/api/le/" + apiVersion + "/" + orgUnitId + "/dropbox/folders/" + folderId + "/attachments/" + fileId;
    }

    public static String getBaseUrl(){
        return baseUrl;
    }

    public static URI getBaseUri(){
        return baseUri;    
    }

    public static String getDomain(){
        return domain;
    }

    public static String getApiVersion(){
        return apiVersion;
    }
}