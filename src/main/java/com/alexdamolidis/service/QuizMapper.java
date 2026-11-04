package com.alexdamolidis.service;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.AssignmentKind;
import com.alexdamolidis.model.Quiz;

/**
 * Turns Brightspace quizzes into tracked items. They are stored alongside assignments with a "quiz:" id,
 * so saving, ranking, and the calendar sync handle them like assignments, while Claude enrichment and
 * the dropbox specific steps skip them.
 */
public class QuizMapper {

    // Quiz ids and dropbox folder ids are separate number ranges, so the prefix keeps them from colliding.
    static final String ID_PREFIX = "quiz:";

    // Professors put the proctoring requirement in the quiz name, e.g. "Quiz 1 - Requires Respondus LockDown Browser + Webcam".
    private static final Pattern REQUIREMENT_SUFFIX = Pattern.compile(
            "\\s*-?\\s*(requires\\s+respondus\\s+lockdown\\s+browser(?:\\s*\\+\\s*webcam)?)\\s*$", Pattern.CASE_INSENSITIVE);

    // Matched against the normalized name, so "Test1", "Mid Term", and "Midterm PART A" are all recognized.
    private static final Pattern TEST_NAME = Pattern.compile("\\b(?:tests?|exams?|midterms?|mid term|final)\\b");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QuizMapper(){}

    /**
     * Converts a quiz into a tracked item. Many quizzes have no due date, only a window that closes at
     * their end date, so the end date is used as the due date when there is none.
     *
     * @param quiz quiz from the Brightspace quiz list
     * @return the tracked item, or null for inactive quizzes and placeholders with no dates (e.g. "SAMPLE QUIZ")
     */
    static Assignment toAssignment(Quiz quiz){
        OffsetDateTime due = quiz.dueDate() != null ? quiz.dueDate() : quiz.endDate();
        if(!quiz.isActive() || due == null) return null;

        Assignment item = new Assignment();
        item.setFolderId(ID_PREFIX + quiz.quizId());
        item.setName(cleanName(quiz.name()));
        item.setKind(kindOf(item.getName()));
        item.setDueDate(due);
        item.setOpenDate(quiz.startDate());
        item.setCloseDate(quiz.endDate());
        item.setAccessRequirement(accessRequirementOf(quiz.name()));

        if(quiz.timeLimit() != null && quiz.timeLimit().isEnforced()){
            item.setTimeLimitMinutes(quiz.timeLimit().minutes());
        }
        if(quiz.attemptsAllowed() != null && !quiz.attemptsAllowed().isUnlimited()){
            item.setAttemptsAllowed(quiz.attemptsAllowed().number());
        }
        if(quiz.gradeItemId() != null){
            item.setGradeItemId(String.valueOf(quiz.gradeItemId()));
        }
        return item;
    }

    /**
     * Removes the proctoring requirement from a quiz name and evens out its spacing, so
     * "Quiz 2  - Requires Respondus LockDown Browser" becomes "Quiz 2" and "Test 1  Fall" becomes "Test 1 Fall".
     */
    static String cleanName(String name){
        Matcher suffix = REQUIREMENT_SUFFIX.matcher(name);
        String cleaned = suffix.find() ? name.substring(0, suffix.start()) : name;

        return WHITESPACE.matcher((cleaned.isBlank() ? name : cleaned).strip()).replaceAll(" ");
    }

    /**
     * @return the proctoring requirement written in the quiz name, e.g. "Requires Respondus LockDown Browser + Webcam", or null
     */
    static String accessRequirementOf(String name){
        Matcher suffix = REQUIREMENT_SUFFIX.matcher(name);
        return suffix.find() ? WHITESPACE.matcher(suffix.group(1)).replaceAll(" ") : null;
    }

    /**
     * @return TEST for tests, midterms, exams, and finals, otherwise QUIZ
     */
    static AssignmentKind kindOf(String name){
        return TEST_NAME.matcher(ContentMatcher.normalize(name)).find() ? AssignmentKind.TEST : AssignmentKind.QUIZ;
    }
}
