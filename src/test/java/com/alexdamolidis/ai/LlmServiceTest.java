package com.alexdamolidis.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.exception.RateLimitReachedException;
import com.alexdamolidis.exception.TrackerApiException;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;

public class LlmServiceTest {

    // LlmDataSource is a single method interface, so a lambda stands in for a real
    // provider. These tests never reach the network.
    private static final LlmDataSource STUB_SOURCE = prompt -> null;

    @Test
    public void testBuildPromptWithAttachments(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("TestAssignment1Name");
        
        //set dueDate to null, when sent to getDaysUntilDue, it will return 0.
        assignment.setDueDate(null);
        assignment.setInstructionText("Develop a test method for Assignment1");

        Attachment att1 = new Attachment();
        att1.setFileName("rubric.pdf");
        att1.setAttachmentText("Grading criteria Assignment1.");
        assignment.addAttachment(att1);

        String prompt = llmService.buildPrompt(assignment);

        assertTrue(prompt.contains("TestAssignment1Name"));
        assertTrue(prompt.contains("Days Until Due: 0"));
        assertTrue(prompt.contains("rubric.pdf"));
        assertTrue(prompt.contains("Grading criteria"));
    }

    @Test
    public void testBuildPromptNoAttachments(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Check In");
        assignment.setInstructionText("Complete the check in.");

        String prompt = llmService.buildPrompt(assignment);

        assertTrue(prompt.contains("No Attachments"));
        assertFalse(prompt.contains("Attachment Name:"));
    }

    @Test
    public void testApplyEnrichmentPopulatesAssignment() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Backend Integration Lab");

        Enrichment enrichment = new Enrichment(3, "It is a complex lab.", "Complete the API integration.");

        llmService.applyEnrichment(enrichment, assignment);

        assertEquals(3, assignment.getPriority());
        assertEquals("It is a complex lab.", assignment.getReasoning());
        assertEquals("Complete the API integration.", assignment.getLlmSummary());
    }

    @Test
    public void testApplyEnrichmentMissingReasoningFallsBack() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();

        Enrichment enrichment = new Enrichment(2, null, "Test summary.");

        llmService.applyEnrichment(enrichment, assignment);

        assertEquals(2, assignment.getPriority());
        assertEquals("No reasoning provided.", assignment.getReasoning());
        assertEquals("Test summary.", assignment.getLlmSummary());
    }

    @Test
    public void testApplyEnrichmentNullResponseUsesDefaults() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Empty Response Assignment");

        llmService.applyEnrichment(null, assignment);

        assertEquals(0, assignment.getPriority());
        assertNull(assignment.getReasoning());
        assertNull(assignment.getLlmSummary());
    }

    @Test
    public void testBuildPromptUnreadableAttachment(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Lab 1");

        Attachment unreadable = new Attachment();
        unreadable.setFileName("broken.pdf");
        assignment.addAttachment(unreadable);

        String prompt = llmService.buildPrompt(assignment);

        assertTrue(prompt.contains("broken.pdf\n[Attachment could not be read]"));
        assertFalse(prompt.contains("broken.pdf\nnull"));
    }

    // A single failed request should not stop the rest of the semester from being enriched.
    @Test
    public void testEnrichSemesterContinuesPastFailedAssignment(){
        LlmDataSource source = prompt -> {
            if(prompt.contains("Broken Lab")){
                throw new TrackerApiException("Claude AI Error: 500");
            }
            return new Enrichment(2, "Reason.", "Summary.");
        };
        Semester semester = createSemester("Broken Lab", "Working Lab");

        new LlmService(source).enrichSemester(semester);

        List<Assignment> assignments = semester.getCourses().get(0).getAssignments();
        assertNull(assignments.get(0).getLlmSummary());
        assertEquals("Summary.", assignments.get(1).getLlmSummary());
    }

    // Once retries are exhausted, every remaining request would fail too, so enrichment
    // stops early but keeps what was already enriched.
    @Test
    public void testEnrichSemesterStopsWhenRateLimitIsReached(){
        AtomicInteger calls = new AtomicInteger();
        LlmDataSource source = prompt -> {
            if(calls.incrementAndGet() == 2){
                throw new RateLimitReachedException("Claude AI failed after 3 retries, stopping to protect API quota.");
            }
            return new Enrichment(2, "Reason.", "Summary.");
        };
        Semester semester = createSemester("Lab 1", "Lab 2", "Lab 3");

        new LlmService(source).enrichSemester(semester);

        List<Assignment> assignments = semester.getCourses().get(0).getAssignments();
        assertEquals(2, calls.get());
        assertEquals("Summary.", assignments.get(0).getLlmSummary());
        assertNull(assignments.get(1).getLlmSummary());
        assertNull(assignments.get(2).getLlmSummary());
    }

    private Semester createSemester(String... assignmentNames){
        Course course = new Course();
        course.setIsWorthCredits(true);

        for(String name : assignmentNames){
            Assignment assignment = new Assignment();
            assignment.setName(name);
            assignment.setDueDate(OffsetDateTime.now().plusDays(5));
            course.addAssignment(assignment);
        }
        Semester semester = new Semester("TestSemester");
        semester.addCourse(course);
        return semester;
    }
}
