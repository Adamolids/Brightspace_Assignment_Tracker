package com.alexdamolidis.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;

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
}
