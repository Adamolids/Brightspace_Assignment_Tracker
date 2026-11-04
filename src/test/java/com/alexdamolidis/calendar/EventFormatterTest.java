package com.alexdamolidis.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.AssignmentKind;
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

    private static final OffsetDateTime QUIZ_DUE = OffsetDateTime.parse("2026-03-12T16:00:00Z");
    private static final ZoneId         TORONTO  = ZoneId.of("America/Toronto");

    private Assignment quiz(AssignmentKind kind, int priority){
        Assignment quiz = new Assignment();
        quiz.setKind(kind);
        quiz.setPriority(priority);
        quiz.setDueDate(QUIZ_DUE);
        return quiz;
    }

    @Test
    public void quizzesAndTestsKeepTheirOwnColorUntilDoneOrClosed(){
        assertEquals("7",     EventFormatter.colorId(quiz(AssignmentKind.QUIZ, 4)));
        assertEquals("3",     EventFormatter.colorId(quiz(AssignmentKind.TEST, 1)));
        assertEquals("Grape", EventFormatter.colorName(quiz(AssignmentKind.TEST, 2)));

        Assignment graded = quiz(AssignmentKind.QUIZ, 0);
        graded.setSubmitted(true);
        assertEquals("8", EventFormatter.colorId(graded));
        assertEquals("8", EventFormatter.colorId(quiz(AssignmentKind.TEST, 0)));
    }

    @Test
    public void quizDescriptionShowsHowItIsWritten(){
        Assignment test = quiz(AssignmentKind.TEST, 3);
        test.setTimeLimitMinutes(110);
        test.setAttemptsAllowed(1);
        test.setAccessRequirement("Requires Respondus LockDown Browser + Webcam");

        assertEquals("""
            Type: Test

            Time limit: 1 hour 50 minutes
            Attempts allowed: 1
            Requires Respondus LockDown Browser + Webcam
            """, EventFormatter.description(test, TORONTO));
    }

    @Test
    public void quizDescriptionShowsStatusAndWhenALongWindowOpens(){
        Assignment graded = quiz(AssignmentKind.QUIZ, 0);
        graded.setSubmitted(true);
        assertTrue(EventFormatter.description(graded, TORONTO).contains("Status: Done, a grade has been posted"));
        assertTrue(EventFormatter.description(quiz(AssignmentKind.QUIZ, 0), TORONTO).contains("Status: Closed"));

        Assignment weekLong = quiz(AssignmentKind.QUIZ, 2);
        weekLong.setOpenDate(OffsetDateTime.parse("2026-03-05T05:01:00Z"));
        String description = EventFormatter.description(weekLong, TORONTO);

        assertTrue(description.contains("Opens: Thu, Mar 5 at 12:01 AM"));
        assertTrue(description.contains("Attempts allowed: unlimited"));
        assertFalse(description.contains("Time limit"));
        assertFalse(description.contains("Priority"));
        assertFalse(description.contains("Summary"));
    }

    @Test
    public void quizWrittenInOneSittingCoversTheSitting(){
        Assignment sitting = quiz(AssignmentKind.TEST, 3);
        sitting.setOpenDate(QUIZ_DUE.minusMinutes(110));
        assertEquals(sitting.getOpenDate(), EventFormatter.start(sitting));
        assertFalse(EventFormatter.description(sitting, TORONTO).contains("Opens"));

        Assignment weekLong = quiz(AssignmentKind.QUIZ, 2);
        weekLong.setOpenDate(QUIZ_DUE.minusDays(7));
        assertEquals(QUIZ_DUE.minusHours(1), EventFormatter.start(weekLong));

        Assignment assignment = new Assignment();
        assignment.setDueDate(QUIZ_DUE);
        assignment.setOpenDate(QUIZ_DUE.minusHours(2));
        assertEquals(QUIZ_DUE.minusHours(1), EventFormatter.start(assignment));
    }

    @Test
    public void timeLimitsAreShownExactly(){
        assertEquals("25 minutes",        EventFormatter.formatMinutes(25));
        assertEquals("2 hours",           EventFormatter.formatMinutes(120));
        assertEquals("1 hour 30 minutes", EventFormatter.formatMinutes(90));
    }
}
