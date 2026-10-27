package com.alexdamolidis.util;

import java.net.URI;

public class EndpointBuilder {
    private static final String baseUrl    = "https://slate.sheridancollege.ca";
    private static final URI    baseUri    = URI.create(baseUrl);
    private static final String domain     = "slate.sheridancollege.ca";
    private static final String apiVersion = "1.45";

    // The Content and submission routes only exist in newer Learning Environment versions. Slate's Platform
    // API stops at 1.64, so the shared apiVersion above stays at 1.45 and only these routes use 1.82.
    private static final String newerLeApiVersion = "1.82";

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

/// https://slate.sheridancollege.ca/d2l/api/le/{ newerLeApiVersion }/{ orgUnitId }/content/toc
    public static String buildContentTocUrl(String orgUnitId){
        return baseUrl + "/d2l/api/le/" + newerLeApiVersion + "/" + orgUnitId + "/content/toc";
    }

/// https://slate.sheridancollege.ca/d2l/api/le/{ newerLeApiVersion }/{ orgUnitId }/content/topics/{ topicId }/file
    public static String buildContentFileUrl(String orgUnitId, long topicId){
        return baseUrl + "/d2l/api/le/" + newerLeApiVersion + "/" + orgUnitId + "/content/topics/" + topicId + "/file";
    }

/// https://slate.sheridancollege.ca/d2l/api/le/{ newerLeApiVersion }/{ orgUnitId }/dropbox/folders/{ folderId }/submissions/mysubmissions/
    public static String buildMySubmissionsUrl(String orgUnitId, String folderId){
        return baseUrl + "/d2l/api/le/" + newerLeApiVersion + "/" + orgUnitId + "/dropbox/folders/" + folderId + "/submissions/mysubmissions/";
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