package com.alexdamolidis.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.EstimateConfidence;

public class EventFormatterTest {

    private Assignment estimatedAssignment(int priority){
        Assignment assignment = new Assignment();
        assignment.setPriority(priority);
        assignment.setEstimatedHours(6.0);
        assignment.setDifficulty(3);
        assignment.setConfidence(EstimateConfidence.HIGH);
        assignment.setLlmSummary("Write the report.");
        return assignment;
    }

    @Test
    public void colorFollowsPriority(){
        assertEquals("11", EventFormatter.colorId(estimatedAssignment(4)));
        assertEquals("6",  EventFormatter.colorId(estimatedAssignment(3)));
        assertEquals("5",  EventFormatter.colorId(estimatedAssignment(2)));
        assertEquals("2",  EventFormatter.colorId(estimatedAssignment(1)));
        assertEquals("8",  EventFormatter.colorId(estimatedAssignment(0)));
        assertEquals("Tomato", EventFormatter.colorName(estimatedAssignment(4)));
    }

    @Test
    public void submittedAssignmentIsGrey(){
        Assignment submitted = estimatedAssignment(0);
        submitted.setSubmitted(true);

        assertEquals("8", EventFormatter.colorId(submitted));
        assertTrue(EventFormatter.description(submitted).startsWith("Status: Submitted"));
    }

    @Test
    public void descriptionShowsPriorityAndEffort(){
        String description = EventFormatter.description(estimatedAssignment(3));

        assertTrue(description.startsWith("Priority: 3/4"));
        assertTrue(description.contains("Estimated effort: about 6 hours, difficulty 3/5"));
        assertTrue(description.contains("Summary: Write the report."));
        assertFalse(description.contains("rough guess"));
    }

    @Test
    public void lowConfidenceEstimateIsFlagged(){
        Assignment guessed = estimatedAssignment(2);
        guessed.setConfidence(EstimateConfidence.LOW);

        assertTrue(EventFormatter.description(guessed).contains("rough guess"));
    }

    @Test
    public void closedAssignmentSaysSo(){
        assertTrue(EventFormatter.description(estimatedAssignment(0)).startsWith("Status: Closed"));
    }

    @Test
    public void unenrichedAssignmentShowsPlaceholder(){
        Assignment pending = new Assignment();
        pending.setPriority(3);

        String description = EventFormatter.description(pending);

        assertFalse(description.contains("Estimated effort"));
        assertTrue(description.contains("Not generated yet"));
    }

    @Test
    public void hoursAreRoundedToReadableAmounts(){
        assertEquals("about 30 minutes", EventFormatter.formatHours(0.5));
        assertEquals("about 5 minutes",  EventFormatter.formatHours(0.01));
        assertEquals("about 1 hour",     EventFormatter.formatHours(0.99));
        assertEquals("about 2.5 hours",  EventFormatter.formatHours(2.4));
        assertEquals("about 12 hours",   EventFormatter.formatHours(12));
    }
}
