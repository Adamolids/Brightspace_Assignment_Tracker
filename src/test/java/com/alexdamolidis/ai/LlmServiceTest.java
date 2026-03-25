package com.alexdamolidis.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;

public class LlmServiceTest {
    
    @Test
    public void testBuildPromptWithAttachments(){
        LlmService llmService = new LlmService("RandomApiKey!");
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
        LlmService llmService = new LlmService("RandomApiKey!");
        Assignment assignment = new Assignment();
        assignment.setName("Check In");
        assignment.setInstructionText("Complete the check in.");

        String prompt = llmService.buildPrompt(assignment);

        assertTrue(prompt.contains("No Attachments"));
        assertFalse(prompt.contains("Attachment Name:"));
    }

    @Test
    public void testSyncAiResponseToModelValidJson() {
        LlmService llmService = new LlmService("RandomApiKey!");
        Assignment assignment = new Assignment();
        assignment.setName("Backend Integration Lab");
        
        String fakeGoogleResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"priority\\": 3, \\"reasoning\\": \\"It is a complex lab.\\", \\"llmSummary\\": \\"Complete the API integration.\\"}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        llmService.syncAiResponseToModel(fakeGoogleResponse, assignment);

        assertEquals(3, assignment.getPriority());
        assertEquals("It is a complex lab.", assignment.getReasoning());
        assertEquals("Complete the API integration.", assignment.getLlmSummary());
    }

    @Test
    public void testSyncAiResponseToModelMissingPriorityDefaultsToZero() {
        LlmService llmService = new LlmService("RandomApiKey!");
        Assignment assignment = new Assignment();
        
        String fakeResponse = """
            {
              "candidates": [ { "content": { "parts": [ {
                "text": "{\\"reasoning\\": \\"Test reasoning.\\", \\"llmSummary\\": \\"Test summary.\\"}"
              } ] } } ]
            }
            """;

        llmService.syncAiResponseToModel(fakeResponse, assignment);

        assertEquals(0, assignment.getPriority()); 
    }   
}