package com.alexdamolidis.service;

import com.alexdamolidis.client.BrightspaceDataSource;
import com.alexdamolidis.model.*;
import com.alexdamolidis.repository.SqliteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private BrightspaceDataSource scraper;

    @Mock
    private SqliteRepository mockRepo;

    @InjectMocks
    private AssignmentService assignmentService;

    private Semester semester;

    @BeforeEach
    void setUp() {
        semester = new Semester("Winter 2026");
    }

    /**
    * Ensures taht the sync logic correctly identifies new courses from the 
    * API response and only adds unique ones to the semester object.
    */
    @Test
    void checkForNewCoursesAddsOnlyNewCourses() {
        Course existingCourse = new Course();
        existingCourse.setOrgUnitId("101");
        semester.addCourse(existingCourse);

        String validJson = """
            {
                "Items": [
                    {
                        "OrgUnit": {
                            "Id": "101",
                            "Name": "Course101",
                            "Code": "12345678"
                        }
                    },                          
                    {                           
                        "OrgUnit": {
                            "Id": "202",        
                            "Name": "Course202",
                            "Code": "87654321"  
                        }
                    }
                ]
            }
            """;

        when(scraper.sendGetRequest(anyString())).thenReturn(validJson);

        assignmentService.checkForNewCourses(semester);

        assertEquals(2, semester.getCourses().size());
        assertTrue(semester.getCourses().stream().anyMatch(c -> c.getOrgUnitId().equals("202")));
    }

    /**
     * Ensures that if the API respone contains courses that are already present in the semester object,
     * the sync logic does not add duplicates and maintains the integrity of the course list.
    */
    @Test
    void checkForNewCoursesDoesNotAddDuplicates() {
        Course existingCourse = new Course();
        existingCourse.setOrgUnitId("101");
        semester.addCourse(existingCourse);

        String validJson = """
            {
                "Items": [
                    {
                        "OrgUnit": {
                            "Id": 101,
                            "Code": "Course101",
                            "Name": "Course Offering"
                        }
                    }
                ]
            }
            """;
        when(scraper.sendGetRequest(anyString())).thenReturn(validJson);

        assignmentService.checkForNewCourses(semester);

        assertEquals(1, semester.getCourses().size());
    }

    /**
     * Tests the assignment level sync logic. It verifies that when a courses 
     * is processed, existing folderIds are respected and only new discovered 
     * assignments are appended to the course List.
     */
    @Test
    void checkForNewAssignmentsAddsOnlyNewAssignmentsToCorrectCourse() {

        Course course = new Course();
        course.setOrgUnitId("101");
        course.setIsWorthCredits(true);
        
        Assignment existingAssign = new Assignment();
        existingAssign.setFolderId("FolderA");
        course.addAssignment(existingAssign);
        semester.addCourse(course);
        String validJson = """
            [
                { "Id": "FolderA" },
                { "Id": "FolderB" }
            ]
            """;
        when(scraper.sendGetRequest(contains("101"))).thenReturn(validJson);

        assignmentService.checkForNewAssignments(semester);

        assertEquals(2, course.getAssignments().size());
        assertTrue(course.getAssignments().stream().anyMatch(a -> a.getFolderId().equals("FolderB")));
    }

    /**
     * Confirms that the sync process respects the credit status of courses.
     * Courses marked as non credit should not trigger a request for assignments.
    */
    @Test
    void checkForNewAssignmentsSkipsNonCreditCourses() {
        Course auditCourse = new Course();
        auditCourse.setOrgUnitId("999");
        auditCourse.setIsWorthCredits(false);
        semester.addCourse(auditCourse);

        assignmentService.checkForNewAssignments(semester);

        verify(scraper, never()).sendGetRequest(anyString());
    }
}