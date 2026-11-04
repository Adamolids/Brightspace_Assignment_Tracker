package com.alexdamolidis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.AssignmentKind;
import com.alexdamolidis.model.Quiz;

/**
 * The names used here follow the patterns seen in real quiz lists: proctoring requirements appended
 * to the name with inconsistent spacing, tests stored as quizzes, and placeholders without dates.
 */
public class QuizMapperTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-03-12T14:00:00Z");
    private static final OffsetDateTime DUE   = OffsetDateTime.parse("2026-03-12T15:50:00Z");
    private static final OffsetDateTime END   = OffsetDateTime.parse("2026-03-12T16:00:00Z");

    private static Quiz quiz(String name, OffsetDateTime due, OffsetDateTime end){
        return new Quiz(4400123, name, true, START, due, end,
                        new Quiz.TimeLimit(true, 110), new Quiz.Attempts(false, 1), 7700456L);
    }

    @Test
    public void proctoringRequirementIsMovedOutOfTheName(){
        assertEquals("Quiz 1",         QuizMapper.cleanName("Quiz 1 - Requires Respondus LockDown Browser + Webcam"));
        assertEquals("Quiz 2",         QuizMapper.cleanName("Quiz 2  - Requires Respondus LockDown Browser + Webcam"));
        assertEquals("Quiz-2",         QuizMapper.cleanName("Quiz-2- Requires Respondus LockDown Browser"));
        assertEquals("Midterm PART A", QuizMapper.cleanName("Midterm PART A- Requires Respondus LockDown Browser"));
        assertEquals("Quiz #1",        QuizMapper.cleanName("Quiz #1"));

        assertEquals("Requires Respondus LockDown Browser + Webcam",
                     QuizMapper.accessRequirementOf("Test1 - Requires Respondus LockDown Browser + Webcam"));
        assertEquals("Requires Respondus LockDown Browser", QuizMapper.accessRequirementOf("Quiz-3- Requires Respondus LockDown Browser"));
        assertNull(QuizMapper.accessRequirementOf("Quiz #1"));
    }

    @Test
    public void testsMidtermsAndExamsAreTests(){
        for(String name : new String[]{"Test1", "Test 2 Dec 4 Placeholder", "Midterm", "Midterm PART A", "Mid Term Exam", "Final Exam"}){
            assertEquals(AssignmentKind.TEST, QuizMapper.kindOf(name), name);
        }
        for(String name : new String[]{"Quiz #1", "Quiz-1", "Diagnostic Quiz", "Browser Setup Quiz", "warm up practice 2026", "Contest Entry Quiz"}){
            assertEquals(AssignmentKind.QUIZ, QuizMapper.kindOf(name), name);
        }
    }

    @Test
    public void quizBecomesATrackedItem(){
        Assignment item = QuizMapper.toAssignment(quiz("Test1 - Requires Respondus LockDown Browser + Webcam", DUE, END));

        assertEquals("quiz:4400123", item.getFolderId());
        assertEquals("Test1", item.getName());
        assertEquals(AssignmentKind.TEST, item.getKind());
        assertEquals(DUE, item.getDueDate());
        assertEquals(START, item.getOpenDate());
        assertEquals(END, item.getCloseDate());
        assertEquals(110, item.getTimeLimitMinutes());
        assertEquals(1, item.getAttemptsAllowed());
        assertEquals("7700456", item.getGradeItemId());
        assertEquals("Requires Respondus LockDown Browser + Webcam", item.getAccessRequirement());
    }

    @Test
    public void quizWithoutDueDateIsDueWhenItCloses(){
        Assignment item = QuizMapper.toAssignment(quiz("Quiz 3", null, END));

        assertEquals(END, item.getDueDate());
    }

    @Test
    public void unlimitedAttemptsAndUnenforcedLimitsAreLeftEmpty(){
        Quiz open = new Quiz(1, "Practice Quiz", true, null, DUE, null,
                             new Quiz.TimeLimit(false, 0), new Quiz.Attempts(true, null), null);
        Assignment item = QuizMapper.toAssignment(open);

        assertNull(item.getTimeLimitMinutes());
        assertNull(item.getAttemptsAllowed());
        assertNull(item.getGradeItemId());
    }

    @Test
    public void placeholdersAndInactiveQuizzesAreSkipped(){
        assertNull(QuizMapper.toAssignment(quiz("SAMPLE QUIZ", null, null)));

        Quiz inactive = new Quiz(2, "Quiz 9", false, START, DUE, END, null, null, null);
        assertNull(QuizMapper.toAssignment(inactive));
    }
}
