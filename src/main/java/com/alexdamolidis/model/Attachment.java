package com.alexdamolidis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attachment {

    @JsonProperty("FileName")
    private String fileName;

    @JsonProperty("FileId")
    private String fileId;
    
    @JsonProperty("Size")
    private int fileSize;

    private String attachmentText;

    public Attachment(){}

    public String getFileName() {
        return fileName;
    }

    public String getFileId() {
        return fileId;
    }

    public int getFileSize(){
        return fileSize;
    }

    public void setAttachmentText(String attachmentText) {
        this.attachmentText = attachmentText;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public String getAttachmentText(){
        return attachmentText;
    }

    @Override
    public String toString() {
        return "[fileName=" + fileName + ", fileId=" + fileId + ", fileSize=" + fileSize
                + ", attachmentText=" + attachmentText + "]";
    }
}