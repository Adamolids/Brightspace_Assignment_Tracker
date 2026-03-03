package com.alexdamolidis.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SemesterRepositoryTest {

	@TempDir
    Path tempDir; 

    private final SemesterRepository dataManager = new SemesterRepository(); 

    @Test
    void loadCollectedData_HandlesMissingDueDateGracefully() throws Exception {

        String jsonWithNoDate = """
            {
                "name": "Winter2026",
                "courses": [
                    {
                        "name": "Enterprise Java",
                        "isWorthCredits": true,
                        "assignments": [
                            {
                                "Id": "101",
                                "Name": "Project 1"
                            }
                        ]
                    }
                ]
            }
            """;
            
        Path testFile = tempDir.resolve("missing_date.json");
        Files.writeString(testFile, jsonWithNoDate);

        Semester result = dataManager.loadCollectedData(testFile.toString());

        Assignment parsedAssignment = result.getCourses().get(0).getAssignments().get(0);
        
        assertEquals("101", parsedAssignment.getFolderId());
        assertNull(parsedAssignment.getDueDate(), "The due date should safely default to null");
    }

    @Test
    void loadCollectedData_HandlesEmptyAssignmentsArray() throws Exception {
        String jsonEmptyAssignments = """
            {
                "name": "Winter2026",
                "courses": [
                    {
                        "name": "Network Engineering",
                        "isWorthCredits": true,
                        "assignments": [] 
                    }
                ]
            }
            """;
            
        Path testFile = tempDir.resolve("empty_assignments.json");
        Files.writeString(testFile, jsonEmptyAssignments);

        Semester result = dataManager.loadCollectedData(testFile.toString());

        Course parsedCourse = result.getCourses().get(0);
        
        assertTrue(parsedCourse.getIsWorthCredits());
        assertNotNull(parsedCourse.getAssignments(), "The list should be instantiated, not null");
        assertTrue(parsedCourse.getAssignments().isEmpty(), "The list should be completely empty");
    }
}