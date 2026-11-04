package com.alexdamolidis.model;

/**
 * What a tracked item is. Assignments come from dropbox folders and are estimated by Claude.
 * Quizzes and tests come from the course's quiz list and are tracked by their dates alone.
 */
public enum AssignmentKind {
    ASSIGNMENT,
    QUIZ,
    TEST
}
