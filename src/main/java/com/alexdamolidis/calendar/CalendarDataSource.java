package com.alexdamolidis.calendar;

import java.util.List;

import com.alexdamolidis.model.Course;

public interface CalendarDataSource {
    
public void syncAssignments(List<Course> courses);

}
