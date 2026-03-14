package com.alexdamolidis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;

public class SqliteRepositoryTest {

    @BeforeEach
    public void setup() throws IOException {
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("test.db"));
    }
 
    public Semester createTestSemester(){
        
        Semester semester = new Semester("Winter2026Test");

        List<Course> courses = new ArrayList<>();
        Course course = new Course();
        course.setName("courseOne");
        course.setOrgUnitId("12345");
        course.setIsWorthCredits(true);

        courses.add(course);
        semester.setCourses(courses);

        List<Assignment> assignments = new ArrayList<>();
        Assignment assignment = new Assignment();
        assignment.setFolderId("102030");
        assignment.setName("AssignmentOne");
        OffsetDateTime time = OffsetDateTime.parse("2011-12-03T10:15:30Z");
        assignment.setDueDate(time);
        assignment.setInstructionText("TextForTheAssignment");
        assignment.setLlmSummary("ThisIsASummary");
        assignment.setPriority(4);
        assignment.setReasoning("its hard");
        assignment.setIsSyncedToCalendar(false);

        assignments.add(assignment);
        course.setAssignments(assignments);

        Attachment attachment = new Attachment();
        List<Attachment> attachments = new ArrayList<>();
        attachment.setFileId("67890");
        attachment.setFileName("AttachmentTestName");
        attachment.setFileSize(2000);
        attachment.setAttachmentText("Attachment test text here");

        attachments.add(attachment);
        assignment.setAttachments(attachments);

        return semester;
    }
    
    public void validateSemester(Semester semester) {
        assertNotNull(semester);
        assertEquals(semester.getName(), "Winter2026Test");
        assertNotNull(semester.getCourses());

        for (Course course : semester.getCourses()) {
            assertNotNull(course.getName());
            assertEquals(course.getOrgUnitId(), "12345");
            assertEquals(course.getIsWorthCredits(), true);
            
            for (Assignment assignment : course.getAssignments()) {
                assertNotNull(assignment.getName());
                assertEquals(assignment.getDueDate(), OffsetDateTime.parse("2011-12-03T10:15:30Z"));
                assertEquals(assignment.getInstructionText(), "TextForTheAssignment");

                for (Attachment attachment : assignment.getAttachments()) {
                    assertNotNull(attachment.getFileName());
                    assertEquals(attachment.getFileId(), "67890");
                    assertEquals(attachment.getAttachmentText(), "Attachment test text here");
                }
            }
        }
    }

    /**
     * Ensures that semester data can be saved to database.
     */
    @Test
    public void saveSemesterTest(){
        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        repo.saveSemester(createTestSemester());
    } 

    /**
     * Ensures that semester object can be rehydrated with data from repository.
     */
    @Test
    public void loadSemesterTest(){
        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        Semester testSemester = createTestSemester();
        repo.saveSemester(testSemester);

        Semester loadedTestSemester = repo.loadSemester("Winter2026Test");
        validateSemester(loadedTestSemester);
    }

    /**
     * Ensures that ON CONFLICT logic is working as intended.
     */
    @Test
    public void updateOnConflict(){
        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        Semester testSemester = createTestSemester();
        repo.saveSemester(testSemester);

        String newName = "newAssignmentTestName";
        testSemester.getCourses().get(0).getAssignments().get(0).setName(newName);

        repo.saveSemester(testSemester);

        Semester alteredSemester = repo.loadSemester("Winter2026Test");
        String alteredAssignmentName = alteredSemester.getCourses().get(0).getAssignments().get(0).getName();

        assertEquals(newName, alteredAssignmentName);
    }
}