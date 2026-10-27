package com.alexdamolidis.calendar;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.EstimateConfidence;

/**
 * Builds the color and description of an assignment's calendar event. Shared by the Google Calendar
 * sync and demo mode so both show the same thing, and by change detection so it compares against
 * exactly what would be written.
 *
 * Color follows priority, which is recalculated every run from the effort estimate and the time left.
 */
public class EventFormatter {

    // Indexed by priority: 0 (submitted or closed) to 4 (most urgent).
    private static final String[] COLOR_IDS   = {"8",        "2",    "5",      "6",         "11"};
    private static final String[] COLOR_NAMES = {"Graphite", "Sage", "Banana", "Tangerine", "Tomato"};

    private EventFormatter(){}

    /**
     * @param assignment the assignment the event represents
     * @return the Google Calendar color id for the event
     */
    public static String colorId(Assignment assignment){
        return COLOR_IDS[colorIndex(assignment)];
    }

    /**
     * @param assignment the assignment the event represents
     * @return the Google Calendar color name for the event, used by demo mode
     */
    public static String colorName(Assignment assignment){
        return COLOR_NAMES[colorIndex(assignment)];
    }

    private static int colorIndex(Assignment assignment){
        if(assignment.isSubmitted()) return 0;
        return Math.min(Math.max(assignment.getPriority(), 0), 4);
    }

    /**
     * Builds the event description: status or priority, the effort estimate, and the LLM summary.
     *
     * @param assignment the assignment being described
     * @return the event description
     */
    public static String description(Assignment assignment){
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
}
