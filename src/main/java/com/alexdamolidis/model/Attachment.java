package com.alexdamolidis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attachment {

    // Files pulled from the course's Content section share the attachment table, marked by this id prefix.
    public static final String COURSE_CONTENT_PREFIX = "content:";

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

    /**
     * Builds the id for a Content section file linked to an assignment. The folderId keeps ids unique
     * when one file matches several assignments, and the topic's last modified date makes an edited
     * file count as new, so it is downloaded and summarized again.
     *
     * @param folderId the assignment the file is linked to
     * @param topicId the Content topic id
     * @param lastModifiedDate the topic's last modified date from the table of contents
     * @return the attachment id
     */
    public static String courseContentId(String folderId, long topicId, String lastModifiedDate){
        return COURSE_CONTENT_PREFIX + folderId + ":" + topicId + "@" + lastModifiedDate;
    }

    public boolean isCourseContent(){
        return fileId != null && fileId.startsWith(COURSE_CONTENT_PREFIX);
    }

    @Override
    public String toString() {
        return "[fileName=" + fileName + ", fileId=" + fileId + ", fileSize=" + fileSize
                + ", attachmentText=" + attachmentText + "]";
    }
}