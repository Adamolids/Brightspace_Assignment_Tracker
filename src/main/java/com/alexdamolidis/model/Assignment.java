package com.alexdamolidis.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Assignment {

    @JsonProperty("Id")
    private String folderId;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("DueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime dueDate;

    @JsonProperty("Attachments")
    private List<Attachment> attachments = new ArrayList<>();
    private String instructionText;

    @JsonProperty("llmSummary")
    private String llmSummary;
    
    @JsonProperty("priority")
    private int priority;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("CustomInstructions")
    private void unpackInstructions(Map<String, Object> custom){
        if(custom != null){
            this.instructionText = (String)custom.get("Text");
        }
    }

    public Assignment(){}

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public void setInstructionText(String instructionText){
        this.instructionText = instructionText;
    }

    public String getFolderId() {
        return folderId;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public String getInstructionText() {
        return instructionText;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void addAttachment(Attachment attachment){
        this.attachments.add(attachment);
    }

    public void setLlmSummary(String llmSummary){
        this.llmSummary = llmSummary;
    }

    public void setPriority(int priority){
        this.priority = priority;
    }

    public String getLlmSummary(){
        return llmSummary;
    }

    public void setReasoning(String reasoning){
        this.reasoning = reasoning;
    }

    public String getReasoning(){
        return reasoning;
    }

    public int getPriority(){
        return priority;
        /*
        if(this.priority == 1){
            return "this is low priority (1)";
        }else if(this.priority == 2){
            return "this is medium priority (2)";
        }else if(this.priority == 3){
            return "this is high priority (3)";
        }else{
            return "this is very high priority (4)";
        }        
        */
    }

    public boolean updateNameAndDateProperties(Assignment newAssign){

        boolean changed = false;

        if(!Objects.equals(this.name, newAssign.name)){
            this.name = newAssign.name;
            changed = true;
        }
        if(!Objects.equals(this.dueDate, newAssign.dueDate)){
            this.dueDate = newAssign.dueDate;
            changed = true;
        }

        return changed;
    }

    @Override
    public String toString() {
        return "Assignment [folderUrl=" + folderId + ", name=" + name + ", dueDate=" + dueDate + ", attachments="
                + attachments + ", instructionText=" + instructionText + "]";
    }   
}