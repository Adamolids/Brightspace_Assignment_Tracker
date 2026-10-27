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
import com.alexdamolidis.model.EstimateConfidence;
import com.alexdamolidis.model.Semester;

public class LlmServiceTest {

    // LlmDataSource is a single method interface, so a lambda stands in for a real
    // provider. These tests never reach the network.
    private static final LlmDataSource STUB_SOURCE = prompt -> null;

    private static final Enrichment SAMPLE_ENRICHMENT = new Enrichment("Summary.", "Reason.", 3.0, 2, EstimateConfidence.HIGH);

    @Test
    public void testBuildPromptWithAttachments(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("TestAssignment1Name");
        assignment.setInstructionText("Develop a test method for Assignment1");

        Attachment att1 = new Attachment();
        att1.setFileName("rubric.pdf");
        att1.setAttachmentText("Grading criteria Assignment1.");
        assignment.addAttachment(att1);

        String prompt = llmService.buildPrompt(assignment, "Software Testing");

        assertTrue(prompt.contains("Course: Software Testing"));
        assertTrue(prompt.contains("TestAssignment1Name"));
        assertTrue(prompt.contains("rubric.pdf"));
        assertTrue(prompt.contains("Grading criteria"));
        assertFalse(prompt.contains("Days Until Due"));
    }

    @Test
    public void testBuildPromptNoAttachments(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Check In");
        assignment.setInstructionText("Complete the check in.");

        String prompt = llmService.buildPrompt(assignment, "Course");

        assertTrue(prompt.contains("No Attachments"));
        assertFalse(prompt.contains("Attachment Name:"));
    }

    @Test
    public void testBuildPromptSeparatesCourseContent(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setFolderId("101");
        assignment.setName("Assignment 2");

        Attachment content = new Attachment();
        content.setFileId(Attachment.courseContentId("101", 55, "2026-09-02T15:00:00.000Z"));
        content.setFileName("Assignment_2_Instructions");
        content.setAttachmentText("Calculate the macronutrients for one meal.");
        assignment.addAttachment(content);

        String prompt = llmService.buildPrompt(assignment, "Nutrition");

        assertTrue(prompt.contains("None provided in the assignment folder."));
        assertTrue(prompt.contains("No Attachments"));
        assertTrue(prompt.contains("Content Title: Assignment_2_Instructions\nCalculate the macronutrients"));
    }

    @Test
    public void testApplyEnrichmentPopulatesAssignment() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Backend Integration Lab");

        Enrichment enrichment = new Enrichment("Complete the API integration.", "It is a complex lab.", 6.5, 4, EstimateConfidence.MEDIUM);

        assertTrue(llmService.applyEnrichment(enrichment, assignment));

        assertEquals("Complete the API integration.", assignment.getLlmSummary());
        assertEquals("It is a complex lab.", assignment.getReasoning());
        assertEquals(6.5, assignment.getEstimatedHours());
        assertEquals(4, assignment.getDifficulty());
        assertEquals(EstimateConfidence.MEDIUM, assignment.getConfidence());
    }

    @Test
    public void testApplyEnrichmentMissingFieldsFallBack() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();

        Enrichment enrichment = new Enrichment("Test summary.", null, 0.0, 9, null);

        llmService.applyEnrichment(enrichment, assignment);

        assertEquals("No reasoning provided.", assignment.getReasoning());
        assertEquals(0.1, assignment.getEstimatedHours());
        assertEquals(5, assignment.getDifficulty());
        assertEquals(EstimateConfidence.LOW, assignment.getConfidence());
    }

    @Test
    public void testApplyEnrichmentNullResponseUsesDefaults() {
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Empty Response Assignment");

        assertFalse(llmService.applyEnrichment(null, assignment));

        assertNull(assignment.getReasoning());
        assertNull(assignment.getLlmSummary());
        assertNull(assignment.getEstimatedHours());
    }

    @Test
    public void testBuildPromptUnreadableAttachment(){
        LlmService llmService = new LlmService(STUB_SOURCE);
        Assignment assignment = new Assignment();
        assignment.setName("Lab 1");

        Attachment unreadable = new Attachment();
        unreadable.setFileName("broken.pdf");
        assignment.addAttachment(unreadable);

        String prompt = llmService.buildPrompt(assignment, "Course");

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
            return SAMPLE_ENRICHMENT;
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
            return SAMPLE_ENRICHMENT;
        };
        Semester semester = createSemester("Lab 1", "Lab 2", "Lab 3");

        new LlmService(source).enrichSemester(semester);

        List<Assignment> assignments = semester.getCourses().get(0).getAssignments();
        assertEquals(2, calls.get());
        assertEquals("Summary.", assignments.get(0).getLlmSummary());
        assertNull(assignments.get(1).getLlmSummary());
        assertNull(assignments.get(2).getLlmSummary());
    }

    // An estimate is only redone when the material it was based on changes.
    @Test
    public void testEnrichSemesterSkipsUnchangedAndRedoesChangedAssignments(){
        AtomicInteger calls = new AtomicInteger();
        LlmDataSource source = prompt -> {
            calls.incrementAndGet();
            return SAMPLE_ENRICHMENT;
        };
        LlmService llmService = new LlmService(source);
        Semester semester = createSemester("Lab 1");
        Assignment assignment = semester.getCourses().get(0).getAssignments().get(0);

        llmService.enrichSemester(semester);
        llmService.enrichSemester(semester);
        assertEquals(1, calls.get());

        assignment.setInstructionText("The professor posted instructions after the first run.");
        llmService.enrichSemester(semester);
        assertEquals(2, calls.get());
    }

    // Assignments enriched before effort estimates existed have a summary but no estimate.
    @Test
    public void testEnrichSemesterRedoesAssignmentsWithoutAnEstimate(){
        AtomicInteger calls = new AtomicInteger();
        LlmDataSource source = prompt -> {
            calls.incrementAndGet();
            return SAMPLE_ENRICHMENT;
        };
        Semester semester = createSemester("Lab 1");
        semester.getCourses().get(0).getAssignments().get(0).setLlmSummary("Summary from the old ranking.");

        new LlmService(source).enrichSemester(semester);

        assertEquals(1, calls.get());
    }

    @Test
    public void testEnrichSemesterSkipsSubmittedAndClosedAssignments(){
        AtomicInteger calls = new AtomicInteger();
        LlmDataSource source = prompt -> {
            calls.incrementAndGet();
            return SAMPLE_ENRICHMENT;
        };
        Semester semester = createSemester("Submitted Lab", "Closed Lab");
        List<Assignment> assignments = semester.getCourses().get(0).getAssignments();
        assignments.get(0).setSubmitted(true);
        assignments.get(1).setDueDate(OffsetDateTime.now().minusDays(10));

        new LlmService(source).enrichSemester(semester);

        assertEquals(0, calls.get());
    }

    private Semester createSemester(String... assignmentNames){
        Course course = new Course();
        course.setName("Test Course");
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
