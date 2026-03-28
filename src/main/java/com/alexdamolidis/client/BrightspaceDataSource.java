package com.alexdamolidis.client;

public interface BrightspaceDataSource {
    public String sendGetRequest(String url);
    public byte[] downloadAttachment(String url);
}
