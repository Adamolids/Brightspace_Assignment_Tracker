package com.alexdamolidis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;

public class PriorityCalculatorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-27T12:00:00Z");

    private Assignment dueIn(double days, Double estimatedHours){
        Assignment assignment = new Assignment();
        assignment.setName("Test Assignment");
        assignment.setDueDate(NOW.plusMinutes(Math.round(days * 24 * 60)));
        assignment.setEstimatedHours(estimatedHours);
        return assignment;
    }

    @Test
    public void heavyWorkloadIsTopPriority(){
        // 10 hours over 5 days is 2 hours a day.
        assertEquals(4, PriorityCalculator.calculatePriority(dueIn(5, 10.0), NOW));
    }

    @Test
    public void workloadThresholdsMapToPriorities(){
        assertEquals(3, PriorityCalculator.calculatePriority(dueIn(10, 10.0), NOW)); // 1 hour a day
        assertEquals(2, PriorityCalculator.calculatePriority(dueIn(14, 10.0), NOW)); // ~0.7 hours a day
        assertEquals(1, PriorityCalculator.calculatePriority(dueIn(21, 3.0), NOW));  // ~0.14 hours a day
    }

    @Test
    public void bigProjectFarAwayStillRanksAboveSmallTask(){
        Assignment project = dueIn(21, 30.0);
        Assignment quiz    = dueIn(21, 0.5);

        assertEquals(3, PriorityCalculator.calculatePriority(project, NOW));
        assertEquals(1, PriorityCalculator.calculatePriority(quiz, NOW));
    }

    @Test
    public void shortTaskDueSoonIsNotBuried(){
        // 30 minutes of work alone would be priority 1, but it is due in under 2 days.
        assertEquals(3, PriorityCalculator.calculatePriority(dueIn(1.5, 0.5), NOW));
    }

    @Test
    public void anythingDueWithinADayIsTopPriority(){
        assertEquals(4, PriorityCalculator.calculatePriority(dueIn(0.5, 0.25), NOW));
    }

    @Test
    public void pastDueButStillOpenIsTopPriority(){
        Assignment lateButOpen = dueIn(-1, 2.0);
        lateButOpen.setCloseDate(NOW.plusDays(2));

        assertEquals(4, PriorityCalculator.calculatePriority(lateButOpen, NOW));
    }

    @Test
    public void submittedAssignmentHasNothingLeftToDo(){
        Assignment submitted = dueIn(3, 10.0);
        submitted.setSubmitted(true);

        assertEquals(0, PriorityCalculator.calculatePriority(submitted, NOW));
    }

    @Test
    public void closedAssignmentHasNothingLeftToDo(){
        Assignment closedByWindow = dueIn(-1, 2.0);
        closedByWindow.setCloseDate(NOW.minusHours(1));

        assertEquals(0, PriorityCalculator.calculatePriority(closedByWindow, NOW));
        // Without an availability window, an assignment closes 5 days after it is due.
        assertEquals(0, PriorityCalculator.calculatePriority(dueIn(-6, 2.0), NOW));
    }

    @Test
    public void missingEstimateFallsBackToDeadline(){
        assertEquals(3, PriorityCalculator.calculatePriority(dueIn(6, null), NOW));
        assertEquals(2, PriorityCalculator.calculatePriority(dueIn(12, null), NOW));
        assertEquals(1, PriorityCalculator.calculatePriority(dueIn(30, null), NOW));
    }

    @Test
    public void noDueDateHasNoPriority(){
        Assignment undated = new Assignment();
        undated.setEstimatedHours(5.0);

        assertEquals(0, PriorityCalculator.calculatePriority(undated, NOW));
    }
}
