package com.alexdamolidis.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A quiz from Brightspace's quiz list (/quizzes/). Only the fields the tracker uses are mapped.
 * Brightspace returns the quizzes a student can see, but not their own attempts, so whether a quiz
 * is done comes from the student's released grades instead (see GradeItemId).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Quiz(
    @JsonProperty("QuizId")              long           quizId,
    @JsonProperty("Name")                String         name,
    @JsonProperty("IsActive")            boolean        isActive,
    @JsonProperty("StartDate")           OffsetDateTime startDate,
    @JsonProperty("DueDate")             OffsetDateTime dueDate,
    @JsonProperty("EndDate")             OffsetDateTime endDate,
    @JsonProperty("SubmissionTimeLimit") TimeLimit      timeLimit,
    @JsonProperty("AttemptsAllowed")     Attempts       attemptsAllowed,
    @JsonProperty("GradeItemId")         Long           gradeItemId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeLimit(
        @JsonProperty("IsEnforced")     boolean isEnforced,
        @JsonProperty("TimeLimitValue") int     minutes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attempts(
        @JsonProperty("IsUnlimited")             boolean isUnlimited,
        @JsonProperty("NumberOfAttemptsAllowed") Integer number
    ) {}

    /**
     * One page of the quiz list. Next is the URL of the following page, or null on the last one.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
        @JsonProperty("Objects") List<Quiz> quizzes,
        @JsonProperty("Next")    String     next
    ) {
        public Page {
            quizzes = quizzes != null ? quizzes : List.of();
        }
    }
}
