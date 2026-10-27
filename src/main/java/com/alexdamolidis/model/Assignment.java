package com.alexdamolidis.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Assignment {

    // Folders without an availability window are treated as closed this many days after the due date.
    private static final int LATE_SUBMISSION_GRACE_DAYS = 5;

    @JsonProperty("Id")
    private String folderId;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("DueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime dueDate;

    @JsonProperty("Attachments")
    private List<Attachment> attachments = new ArrayList<>();

    private String instructionText;

    @JsonProperty("llmSummary")
    private String llmSummary;

    @JsonProperty("priority")
    private int priority;

    @JsonProperty("reasoning")
    private String reasoning;

    private String calendarEventId;

    private OffsetDateTime openDate;
    private OffsetDateTime closeDate;

    private Double             estimatedHours;
    private Integer            difficulty;
    private EstimateConfidence confidence;
    private String             materialHash;

    private boolean submitted;

    @JsonProperty("CustomInstructions")
    private void unpackInstructions(Map<String, Object> custom){
        if(custom != null){
            this.instructionText = (String)custom.get("Text");
        }
    }

    @JsonProperty("Availability")
    private void unpackAvailability(Map<String, Object> availability){
        if(availability != null){
            this.openDate  = parseDate(availability.get("StartDate"));
            this.closeDate = parseDate(availability.get("EndDate"));
        }
    }

    private static OffsetDateTime parseDate(Object value){
        return value != null ? OffsetDateTime.parse(value.toString()) : null;
    }

    public Assignment(){}

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDueDate(OffsetDateTime dueDate) {
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

    public OffsetDateTime getDueDate() {
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

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
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
    }

    public String getCalendarEventId(){
        return calendarEventId;
    }

    public void setCalendarEventId(String calendarEventId){
        this.calendarEventId = calendarEventId;
    }

    public OffsetDateTime getOpenDate(){
        return openDate;
    }

    public void setOpenDate(OffsetDateTime openDate){
        this.openDate = openDate;
    }

    public OffsetDateTime getCloseDate(){
        return closeDate;
    }

    public void setCloseDate(OffsetDateTime closeDate){
        this.closeDate = closeDate;
    }

    public Double getEstimatedHours(){
        return estimatedHours;
    }

    public void setEstimatedHours(Double estimatedHours){
        this.estimatedHours = estimatedHours;
    }

    public Integer getDifficulty(){
        return difficulty;
    }

    public void setDifficulty(Integer difficulty){
        this.difficulty = difficulty;
    }

    public EstimateConfidence getConfidence(){
        return confidence;
    }

    public void setConfidence(EstimateConfidence confidence){
        this.confidence = confidence;
    }

    public String getMaterialHash(){
        return materialHash;
    }

    public void setMaterialHash(String materialHash){
        this.materialHash = materialHash;
    }

    public boolean isSubmitted(){
        return submitted;
    }

    public void setSubmitted(boolean submitted){
        this.submitted = submitted;
    }

    /**
     * Copies the fields Brightspace can change mid semester (name, dates, and instructions) from
     * freshly fetched assignment data. Enrichment, submission status, and attachments are left alone.
     *
     * @param newAssign assignment data fetched from the Brightspace API, with instructions already cleaned
     * @return true if any field changed
     */
    public boolean updateFromApi(Assignment newAssign){

        boolean changed = false;

        if(!Objects.equals(this.name, newAssign.name)){
            this.name = newAssign.name;
            changed = true;
        }
        if(!Objects.equals(this.dueDate, newAssign.dueDate)){
            this.dueDate = newAssign.dueDate;
            changed = true;
        }
        if(!Objects.equals(this.openDate, newAssign.openDate) || !Objects.equals(this.closeDate, newAssign.closeDate)){
            this.openDate  = newAssign.openDate;
            this.closeDate = newAssign.closeDate;
            changed = true;
        }
        if(!Objects.equals(this.instructionText, newAssign.instructionText)){
            this.instructionText = newAssign.instructionText;
            changed = true;
        }

        return changed;
    }

    /**
     * An assignment is closed once its folder stops accepting submissions. Folders without an
     * availability window are treated as closed {@value #LATE_SUBMISSION_GRACE_DAYS} days after the due date.
     *
     * @param now the current time
     * @return true if submissions are no longer accepted
     */
    public boolean isClosed(OffsetDateTime now){
        if(closeDate != null) return now.isAfter(closeDate);
        return dueDate != null && now.isAfter(dueDate.plusDays(LATE_SUBMISSION_GRACE_DAYS));
    }

    /**
     * Brightspace only reports submission status while a folder is open, and refuses the request
     * (403) before it opens or after it closes.
     *
     * @param now the current time
     * @return true if the folder is currently accepting submissions
     */
    public boolean isOpenForSubmission(OffsetDateTime now){
        return (openDate == null || !now.isBefore(openDate)) && !isClosed(now);
    }

    @Override
    public String toString() {
        return "Assignment [folderUrl=" + folderId + ", name=" + name + ", dueDate=" + dueDate + ", attachments="
                + attachments + ", instructionText=" + instructionText + "]";
    }
}
