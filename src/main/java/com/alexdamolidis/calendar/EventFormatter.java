package com.alexdamolidis.calendar;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.AssignmentKind;
import com.alexdamolidis.model.EstimateConfidence;

/**
 * Builds the color, description, and start time of an item's calendar event. Shared by the Google Calendar
 * sync and demo mode so both show the same thing, and by change detection so it compares against
 * exactly what would be written.
 *
 * Assignment colors follow priority, which is recalculated every run from the effort estimate and the time
 * left. Quizzes and tests are not estimated, so each keeps one color until it is done or closed.
 */
public class EventFormatter {

    // Indexed by priority: 0 (submitted or closed) to 4 (most urgent).
    private static final String[] COLOR_IDS   = {"8",        "2",    "5",      "6",         "11"};
    private static final String[] COLOR_NAMES = {"Graphite", "Sage", "Banana", "Tangerine", "Tomato"};

    private static final String QUIZ_COLOR_ID = "7", QUIZ_COLOR_NAME = "Peacock";
    private static final String TEST_COLOR_ID = "3", TEST_COLOR_NAME = "Grape";

    // A quiz that opens at most this long before it is due is written in one sitting, so the event covers the sitting.
    static final Duration MAX_SITTING = Duration.ofHours(6);

    private static final DateTimeFormatter OPENS_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);

    private EventFormatter(){}

    /**
     * @param assignment the item the event represents
     * @return the Google Calendar color id for the event
     */
    public static String colorId(Assignment assignment){
        if(isOpenQuizOrTest(assignment)){
            return assignment.getKind() == AssignmentKind.TEST ? TEST_COLOR_ID : QUIZ_COLOR_ID;
        }
        return COLOR_IDS[colorIndex(assignment)];
    }

    /**
     * @param assignment the item the event represents
     * @return the Google Calendar color name for the event, used by demo mode
     */
    public static String colorName(Assignment assignment){
        if(isOpenQuizOrTest(assignment)){
            return assignment.getKind() == AssignmentKind.TEST ? TEST_COLOR_NAME : QUIZ_COLOR_NAME;
        }
        return COLOR_NAMES[colorIndex(assignment)];
    }

    private static int colorIndex(Assignment assignment){
        if(assignment.isSubmitted()) return 0;
        return Math.min(Math.max(assignment.getPriority(), 0), 4);
    }

    // Priority 0 means closed (or no due date), see PriorityCalculator.
    private static boolean isOpenQuizOrTest(Assignment assignment){
        return assignment.isQuizOrTest() && !assignment.isSubmitted() && assignment.getPriority() > 0;
    }

    /**
     * Finds when the event starts. A quiz written in one sitting (it opens at most {@link #MAX_SITTING}
     * before it is due) is shown as that sitting. Everything else ends at the due date and starts an hour
     * before it, as a deadline marker.
     *
     * @param assignment the item the event represents, with a due date
     * @return the event's start time
     */
    public static OffsetDateTime start(Assignment assignment){
        return isSitting(assignment) ? assignment.getOpenDate() : assignment.getDueDate().minusHours(1);
    }

    private static boolean isSitting(Assignment assignment){
        OffsetDateTime open = assignment.getOpenDate();
        OffsetDateTime due  = assignment.getDueDate();

        return assignment.isQuizOrTest() && open != null && open.isBefore(due) && !open.isBefore(due.minus(MAX_SITTING));
    }

    /**
     * Builds the event description. Assignments show their status or priority, the effort estimate, and
     * the LLM summary. Quizzes and tests show their status and how they are written.
     *
     * @param assignment the item being described
     * @return the event description
     */
    public static String description(Assignment assignment){
        return description(assignment, ZoneId.systemDefault());
    }

    static String description(Assignment assignment, ZoneId zone){
        return assignment.isQuizOrTest() ? quizDescription(assignment, zone) : assignmentDescription(assignment);
    }

    private static String assignmentDescription(Assignment assignment){
        StringBuilder description = new StringBuilder();

        if(assignment.isSubmitted()){
            description.append("Status: Submitted\n\n");
        }else if(assignment.getPriority() == 0){
            description.append("Status: Closed, no longer accepting submissions\n\n");
        }else{
            description.append("Priority: ").append(assignment.getPriority()).append("/4\n\n");
        }

        if(assignment.getEstimatedHours() != null){
            description.append("Estimated effort: ").append(formatHours(assignment.getEstimatedHours()));
            if(assignment.getDifficulty() != null){
                description.append(", difficulty ").append(assignment.getDifficulty()).append("/5");
            }
            description.append("\n");

            if(assignment.getConfidence() == EstimateConfidence.LOW){
                description.append("Little information was posted for this assignment, so this is a rough guess.\n");
            }
            description.append("\n");
        }

        String summary = assignment.getLlmSummary() != null
                ? assignment.getLlmSummary()
                : "Not generated yet, it will be added on the next successful run.";

        description.append("Summary: ").append(summary).append("\n\n");
        return description.toString();
    }

    private static String quizDescription(Assignment quiz, ZoneId zone){
        StringBuilder description = new StringBuilder();

        description.append("Type: ").append(quiz.getKind() == AssignmentKind.TEST ? "Test" : "Quiz").append("\n");
        if(quiz.isSubmitted()){
            description.append("Status: Done, a grade has been posted\n");
        }else if(quiz.getPriority() == 0){
            description.append("Status: Closed\n");
        }
        description.append("\n");

        // A sitting's event already shows when it opens; a longer window only shows its deadline.
        if(quiz.getOpenDate() != null && !isSitting(quiz)){
            description.append("Opens: ").append(quiz.getOpenDate().atZoneSameInstant(zone).format(OPENS_FORMAT)).append("\n");
        }
        if(quiz.getTimeLimitMinutes() != null){
            description.append("Time limit: ").append(formatMinutes(quiz.getTimeLimitMinutes())).append("\n");
        }
        description.append("Attempts allowed: ")
                   .append(quiz.getAttemptsAllowed() != null ? quiz.getAttemptsAllowed() : "unlimited").append("\n");

        if(quiz.getAccessRequirement() != null){
            description.append(quiz.getAccessRequirement()).append("\n");
        }
        return description.toString();
    }

    /**
     * Rounds an estimate to something readable: the nearest 5 minutes under an hour, otherwise the nearest half hour.
     */
    static String formatHours(double hours){
        long minutes = Math.round(hours * 60 / 5.0) * 5;
        if(minutes < 60){
            return "about " + Math.max(minutes, 5) + " minutes";
        }
        double rounded = Math.round(hours * 2) / 2.0;
        String amount  = rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);

        return "about " + amount + (rounded == 1 ? " hour" : " hours");
    }

    /**
     * Formats a time limit exactly, e.g. "25 minutes", "2 hours", or "1 hour 30 minutes".
     */
    static String formatMinutes(int totalMinutes){
        int hours   = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        String hourPart   = hours + (hours == 1 ? " hour" : " hours");
        String minutePart = minutes + (minutes == 1 ? " minute" : " minutes");

        if(hours == 0)   return minutePart;
        if(minutes == 0) return hourPart;
        return hourPart + " " + minutePart;
    }
}
