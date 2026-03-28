package com.alexdamolidis.calendar;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;

public class DemoGoogleCalendarService implements CalendarDataSource{
    private static final Logger logger = LoggerFactory.getLogger(DemoGoogleCalendarService.class);

  
    @Override
    public void syncAssignments(List<Course> courses){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

        logger.info("\n=== Demo Google Calendar Sync ===\n");

        for(Course course : courses){
            if (!course.getIsWorthCredits()) continue;

            for(Assignment assignment : course.getAssignments()){
                if(assignment.getDueDate() == null) continue;

                String eventName = course.getName() + ": " + assignment.getName();

                int daysUntilDue = (int) assignment.getDaysUntilDue();

                String calendarColor;
                if      (daysUntilDue < -5)  calendarColor = "Graphite";
                else if (daysUntilDue <= 2)  calendarColor = "Tomato";
                else if (daysUntilDue <= 3)  calendarColor = "Tangerine";
                else if (daysUntilDue <= 7)  calendarColor = "Banana"; 
                else    {calendarColor = "Sage"; }    

                String formatted = assignment.getDueDate().atZoneSameInstant(ZoneId.systemDefault()).format(formatter);

                logger.info(String.format(
                        "Title: %s %n" +
                        "Due Date: %s %n" +
                        "Priority: %s %n" +
                        "Summary: %s %n" +
                        "Calendar Color: %s %n",
                        eventName,
                        formatted,
                        assignment.getPriority(),
                        assignment.getLlmSummary(),
                        calendarColor               
                )); 
            }
        }
    
    }
}
