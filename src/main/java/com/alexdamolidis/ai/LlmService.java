package com.alexdamolidis.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.RateLimitReachedException;
import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.EstimateConfidence;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.util.RetryUtility;

public class LlmService{
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    // Floor for the effort estimate, so a near zero answer cannot make an assignment look like no work at all.
    private static final double MIN_ESTIMATED_HOURS = 0.1;

    private final LlmDataSource dataSource;

    // An assignment waiting for Claude. The prompt is built once, and its hash records what the estimate was based on.
    private record PendingEnrichment(Assignment assignment, String prompt, String materialHash){}

    public LlmService(LlmDataSource dataSource){
        this.dataSource = dataSource;
    }

    /**
     * Constructs a formatted text prompt containing the assignment details, its attachments, and any
     * files linked from the course's Content section. Nothing time dependent is included, so the prompt
     * only changes when the assignment's material does.
     *
     * @param assignment The assignment object containing raw data from Brightspace
     * @param courseName name of the course the assignment belongs to
     * @return A formatted string ready to be sent to the LLM
     */
    public String buildPrompt(Assignment assignment, String courseName){
        List<Attachment> files = assignment.getAttachments() != null ? assignment.getAttachments() : List.of();

        String attachmentData = formatFiles(files.stream().filter(file -> !file.isCourseContent()).toList(), "Attachment Name: ");
        String contentData    = formatFiles(files.stream().filter(Attachment::isCourseContent).toList(), "Content Title: ");

        String instructions = assignment.getInstructionText() != null && !assignment.getInstructionText().isBlank()
                            ? assignment.getInstructionText()
                            : "None provided in the assignment folder.";
        return String.format(
            "Course: %s \n" +
            "Assignment Name: %s \n" +
            "Instructions: %s \n" +
            "Attachment Data: \n %s \n" +
            "Related Course Content: \n %s",
            courseName,
            assignment.getName(),
            instructions,
            (attachmentData.isEmpty() ? "No Attachments" : attachmentData),
            (contentData.isEmpty() ? "None found" : contentData)
        );
    }

    // Files are sorted by id so the prompt, and therefore its hash, does not depend on the order they were loaded in.
    private String formatFiles(List<Attachment> files, String label){
        StringBuilder builder = new StringBuilder();

        files.stream()
             .sorted(Comparator.comparing(Attachment::getFileId, Comparator.nullsFirst(Comparator.naturalOrder())))
             .forEach(file -> builder.append("\n ")
                                     .append(label)
                                     .append(file.getFileName())
                                     .append("\n")
                                     .append(file.getAttachmentText() != null
                                             ? file.getAttachmentText()
                                             : "[Attachment could not be read]")
                                     .append("\n"));
        return builder.toString();
    }

    /**
     * Maps the generated summary, reasoning, and effort estimate into the local assignment model.
     * Falls back to default enrichment when the model returned nothing usable.
     *
     * @param enrichment the parsed enrichment, may be null
     * @param assignment object to be updated
     * @return true if the enrichment was applied, false if defaults were used instead
     */
    public boolean applyEnrichment(Enrichment enrichment, Assignment assignment){
        if(enrichment == null || enrichment.llmSummary() == null || enrichment.llmSummary().isEmpty()){
            logger.warn("Claude returned an empty response for: '{}'", assignment.getName());
            setDefaultEnrichment(assignment);
            return false;
        }

        assignment.setLlmSummary(enrichment.llmSummary());
        assignment.setReasoning(enrichment.reasoning() != null ? enrichment.reasoning() : "No reasoning provided.");
        assignment.setEstimatedHours(Math.max(enrichment.estimatedHours(), MIN_ESTIMATED_HOURS));
        assignment.setDifficulty(Math.min(Math.max(enrichment.difficulty(), 1), 5));
        assignment.setConfidence(enrichment.confidence() != null ? enrichment.confidence() : EstimateConfidence.LOW);
        return true;
    }

    /**
     * Orchestrates the AI enrichment process for given semester.
     * Only assignments that need it are sent to Claude: ones without an effort estimate yet, and ones whose
     * instructions, attachments, or linked course content changed since they were last estimated. Assignments
     * without a due date, and assignments already submitted or closed, are skipped.
     *
     * A failed assignment is left unenriched and the loop moves on, so it is retried on the next run.
     * If Claude is still rate limited after retries, enrichment stops early since every remaining
     * request would fail the same way. Either way, assignments enriched so far are kept.
     *
     * @param semester containing the courses to be enriched
     */
    public void enrichSemester(Semester semester){
        List<PendingEnrichment> pending = findAssignmentsNeedingEnrichment(semester);
        int totalAssignments = pending.size();
        logger.info("{} assignments need an effort estimate.", totalAssignments);

        for(int i = 0; i < totalAssignments; i++){
            PendingEnrichment next = pending.get(i);
            Assignment assignment = next.assignment();
            int currentCount = i + 1;

            logger.info("[{}/{}] enriching: '{}'.", currentCount, totalAssignments, assignment.getName());
            try{
                Enrichment enrichment = RetryUtility.executeWithRetry(() -> dataSource.enrich(next.prompt()), "Claude AI");

                if(applyEnrichment(enrichment, assignment)){
                    assignment.setMaterialHash(next.materialHash());
                }
            }catch(RateLimitReachedException e){
                logger.warn("Claude is still rate limited, stopping enrichment. The remaining {} assignments will be enriched on the next run.",
                            totalAssignments - currentCount + 1);
                return;

            }catch(TrackerException e){
                logger.warn("[{}/{}] Enrichment failed for '{}', it will be retried on the next run. Reason: {}",
                            currentCount, totalAssignments, assignment.getName(), e.getMessage());
            }
        }
    }

    /**
     * Collects the assignments in credit bearing courses that need to be sent to Claude.
     *
     * @param semester to extract assignments from
     * @return assignments needing enrichment, with their prompts
     */
    private List<PendingEnrichment> findAssignmentsNeedingEnrichment(Semester semester){
        List<PendingEnrichment> pending = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for(Course course : semester.getCourses()){
            if(!course.getIsWorthCredits() || course.getAssignments() == null) continue;

            for(Assignment assignment : course.getAssignments()){
                if(assignment.getDueDate() == null){
                    logger.debug("Skipping LLM enrichment for '{}', no due date present.", assignment.getName());
                    continue;
                }
                if(assignment.isSubmitted() || assignment.isClosed(now)){
                    logger.debug("Skipping LLM enrichment for '{}', already submitted or closed.", assignment.getName());
                    continue;
                }
                String prompt = buildPrompt(assignment, course.getName());
                String materialHash = hashOf(prompt);

                if(isUpToDate(assignment, materialHash)){
                    logger.debug("Skipping LLM enrichment for '{}', estimate is up to date.", assignment.getName());
                    continue;
                }
                if(assignment.getMaterialHash() != null){
                    logger.info("Instructions or files changed for '{}', estimating it again.", assignment.getName());
                }
                pending.add(new PendingEnrichment(assignment, prompt, materialHash));
            }
        }
        return pending;
    }

    private boolean isUpToDate(Assignment assignment, String materialHash){
        return assignment.getLlmSummary() != null
            && assignment.getEstimatedHours() != null
            && materialHash.equals(assignment.getMaterialHash());
    }

    private static String hashOf(String text){
        try{
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);

        }catch(NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 is not available on this JVM.", e);
        }
    }

    private void setDefaultEnrichment(Assignment assign){
        assign.setLlmSummary(null);
        assign.setReasoning(null);
        assign.setEstimatedHours(null);
        assign.setDifficulty(null);
        assign.setConfidence(null);
    }
}
