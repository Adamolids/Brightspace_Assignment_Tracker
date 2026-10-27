package com.alexdamolidis.service;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;

/**
 * Ranks assignments from 0 to 4 by how much work they need compared to the time left.
 *
 * Claude estimates the hours an assignment takes once; this ranking is recalculated on every run,
 * so priorities stay current as deadlines approach without sending anything to Claude again.
 *
 * - 0: nothing left to do (submitted, closed, or no due date)
 * - 4: due within a day, or past due while the folder still accepts submissions
 * - Otherwise, estimated hours divided by days left gives the hours per day needed, mapped onto 1-4.
 *   Anything due within 2 days is at least a 3, so short tasks are not buried.
 */
public class PriorityCalculator {

    static final double HIGH_HOURS_PER_DAY   = 2.0;  // priority 4
    static final double MEDIUM_HOURS_PER_DAY = 1.0;  // priority 3
    static final double LOW_HOURS_PER_DAY    = 0.5;  // priority 2

    static final double DUE_NOW_DAYS  = 1.0;
    static final double DUE_SOON_DAYS = 2.0;

    private PriorityCalculator(){}

    /**
     * Recalculates the priority of every assignment in the semester's credit bearing courses.
     *
     * @param semester semester to rank
     * @param now the current time
     */
    public static void rankSemester(Semester semester, OffsetDateTime now){
        for(Course course : semester.getCourses()){
            if(!course.getIsWorthCredits() || course.getAssignments() == null) continue;

            for(Assignment assignment : course.getAssignments()){
                assignment.setPriority(calculatePriority(assignment, now));
            }
        }
    }

    /**
     * @param assignment assignment to rank
     * @param now the current time
     * @return priority from 0 (nothing to do) to 4 (most urgent)
     */
    public static int calculatePriority(Assignment assignment, OffsetDateTime now){
        if(assignment.getDueDate() == null || assignment.isSubmitted() || assignment.isClosed(now)){
            return 0;
        }
        double daysLeft = Duration.between(now, assignment.getDueDate()).toMinutes() / (60.0 * 24);

        if(daysLeft <= DUE_NOW_DAYS){
            return 4;
        }
        int priority = assignment.getEstimatedHours() != null
                     ? byWorkload(assignment.getEstimatedHours(), daysLeft)
                     : byDeadline(daysLeft);

        return daysLeft <= DUE_SOON_DAYS ? Math.max(priority, 3) : priority;
    }

    private static int byWorkload(double estimatedHours, double daysLeft){
        double hoursPerDay = estimatedHours / daysLeft;

        if(hoursPerDay >= HIGH_HOURS_PER_DAY)   return 4;
        if(hoursPerDay >= MEDIUM_HOURS_PER_DAY) return 3;
        if(hoursPerDay >= LOW_HOURS_PER_DAY)    return 2;
        return 1;
    }

    // Used until Claude has estimated the work, e.g. when enrichment failed on this run.
    private static int byDeadline(double daysLeft){
        if(daysLeft <= 7)  return 3;
        if(daysLeft <= 14) return 2;
        return 1;
    }
}
