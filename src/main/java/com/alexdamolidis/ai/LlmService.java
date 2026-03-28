package com.alexdamolidis.ai;

import java.io.IOException;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.util.RetryUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmService{
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);
    private final ObjectMapper mapper;
    private final LlmDataSource dataSource;

    public LlmService(LlmDataSource dataSource){
        this.dataSource = dataSource;
        this.mapper = new ObjectMapper();
    }

    /**
     * Constructs a formatted text prompt containing the assignment details and attachments.
     * 
     * @param assignment assignment The assignment object containing raw data from Brightspace
     * @return A formatted string ready to be sent to the LLM
     */
    public String buildPrompt(Assignment assignment){
        StringBuilder attachmentsBuilder = new StringBuilder();

        if(assignment.getAttachments() != null){
            for(Attachment attachment : assignment.getAttachments()){
                attachmentsBuilder.append("\n Attachment Name: ")
                                  .append(attachment.getFileName())
                                  .append("\n")
                                  .append(attachment.getAttachmentText())
                                  .append("\n");
            }
        }
        return String.format(
            "Days Until Due: %s \n" +
            "Assignment Name: %s \n" +
            "Instructions: %s \n" +
            "Attachment Data: \n %s",
            assignment.getDaysUntilDue(),
            assignment.getName(),
            assignment.getInstructionText(),
          (attachmentsBuilder.length() == 0 ? "No Attachments" : attachmentsBuilder.toString())
        );
    }

    /**
     * Enriches the given assignment with AI generated data. 
     * Handles transient API failures (rate limiting) using retry logic with
     * exponential backoff, and applies the parsed response directly to the assignment.
     * 
     * @param assignment the assignment to enrich
     * @throws TrackerException if the request fails due to network issues or interruption
     * @throws RuntimeException if the request thread is interrupted
     */
    private void populateAiFields(Assignment assignment){

            String prompt = buildPrompt(assignment);

            String responseBody = RetryUtility.executeWithRetry(() -> {
                try{
                    return dataSource.getRawApiResponse(prompt);

                } catch(IOException e){
                    throw new TrackerException("Network Failure while contacting Gemini", e);

                } catch(InterruptedException ie){
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Process interrupted or canceled externally while waiting for response.", ie);
                }
            }, "Gemini AI");

            syncAiResponseToModel(responseBody, assignment);
    }

    /**
     * Parses the nested JSON response from the LLM and maps the generated priority,
     * reasoning, and summary into the local assignment model.
     * 
     * @param jsonResponse The raw JSON String returned by the API
     * @param assignment object to be updated
     */
    public void syncAiResponseToModel(String jsonResponse, Assignment assignment){
        try{
            JsonNode root = mapper.readTree(jsonResponse);

            JsonNode parts = root.path("candidates").path(0).path("content").path("parts").path(0);
            String contentText = parts.path("text").asText();

            if(contentText.isEmpty()){
                logger.warn("Gemini returned an empty response for: '{}'", assignment.getName());
                setDefaultEnrichment(assignment);
                return;
            }

            JsonNode aiResult = mapper.readTree(contentText);

            assignment.setPriority(aiResult.path("priority").asInt(0));
            assignment.setReasoning(aiResult.path("reasoning").asText("No reasoning provided."));
            assignment.setLlmSummary(aiResult.path("llmSummary").asText("No summary generated."));
        
        }catch(JsonProcessingException e){
            logger.error("LLM sent malformed JSON for: '{}'", assignment.getName());
            setDefaultEnrichment(assignment);
        }
    }

    /**
     * Orchestrates the AI enrichment process for given semester.
     * Retrieves all eligible assignments and populates thir AI generated fields.
     * assignments are skipped if the alreay contain enrichment or if they lack a due date.
     * 
     * @param semester containing the courses to be enriched
     */
    public void enrichSemester(Semester semester){
        List<Assignment> eligibleAssignments = getEligibleAssignments(semester);

        int currentCount = 0;
        int totalAssignments = eligibleAssignments.size();
		logger.debug("Generating priorities, reasoning, and summaries for '{}' assignments...", totalAssignments);

        for(Assignment assignment : eligibleAssignments){
            currentCount++;
    		if(assignment.getLlmSummary() != null && !assignment.getLlmSummary().isEmpty()){
            	logger.info("[{}/{}] Skipping LLM enrichment for '{}', already present.",
                            currentCount, totalAssignments, assignment.getName());
            	continue;
            }
            if(assignment.getDueDate() == null){
                logger.info("[{}/{}] Skipping LLM enrichment for '{}', no due date present.", 
                            currentCount, totalAssignments, assignment.getName());
                setDefaultEnrichment(assignment);
                continue;
            }
            logger.info("[{}/{}] enriching: '{}'.", currentCount, totalAssignments, assignment.getName());
            populateAiFields(assignment);
        }
    }

    /**
     * Extracts a list of assignments that are eligible for enrichment.
     * An assignment is considered eligible if its parent course is worth credits 
     * and the course's assignment list is not empty.
     * 
     * @param semester to extract assignments from
     * @return list of assignments eligible for enrichment
     */
    private List<Assignment> getEligibleAssignments(Semester semester) {
        return semester.getCourses().stream()
                .filter(Course::getIsWorthCredits)
                .filter(c -> c.getAssignments() != null)
                .flatMap(c -> c.getAssignments().stream())
                .toList();
    }

    private void setDefaultEnrichment(Assignment assign){
        assign.setLlmSummary(null);
        assign.setPriority(0);
        assign.setReasoning(null);
    }
}