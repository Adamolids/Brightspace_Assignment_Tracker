package com.alexdamolidis.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.util.RetryUtility;

public class LlmService{
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);
    private final LlmDataSource dataSource;

    public LlmService(LlmDataSource dataSource){
        this.dataSource = dataSource;
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
     * exponential backoff, and applies the result directly to the assignment.
     * 
     * @param assignment the assignment to enrich
     * @throws RateLimitReachedException if the request is still rate limited after 3 retries
     * @throws TrackerApiException if the request fails for any non transient reason
     */
    private void populateAiFields(Assignment assignment){

            String prompt = buildPrompt(assignment);

            Enrichment enrichment = RetryUtility.executeWithRetry(() -> dataSource.enrich(prompt), "Claude AI");

            applyEnrichment(enrichment, assignment);
    }

    /**
     * Maps the generated priority, reasoning, and summary into the local assignment model.
     * Falls back to default enrichment when the model returned nothing usable.
     * 
     * @param enrichment the parsed enrichment, may be null
     * @param assignment object to be updated
     */
    public void applyEnrichment(Enrichment enrichment, Assignment assignment){
        if(enrichment == null || enrichment.llmSummary() == null || enrichment.llmSummary().isEmpty()){
            logger.warn("Claude returned an empty response for: '{}'", assignment.getName());
            setDefaultEnrichment(assignment);
            return;
        }

        assignment.setPriority(enrichment.priority());
        assignment.setReasoning(enrichment.reasoning() != null ? enrichment.reasoning() : "No reasoning provided.");
        assignment.setLlmSummary(enrichment.llmSummary());
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
