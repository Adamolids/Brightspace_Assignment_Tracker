package com.alexdamolidis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.EstimateConfidence;
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
        assignment.setCalendarEventId("101202");

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

    /**
     * Ensures the effort ranking fields survive a save and load, and that a missing estimate stays
     * null rather than becoming 0.
     */
    @Test
    public void effortFieldsRoundTrip(){
        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        Semester testSemester = createTestSemester();
        Assignment assignment = testSemester.getCourses().get(0).getAssignments().get(0);
        assignment.setOpenDate(OffsetDateTime.parse("2011-11-20T10:00:00Z"));
        assignment.setCloseDate(OffsetDateTime.parse("2011-12-08T10:00:00Z"));
        assignment.setEstimatedHours(6.5);
        assignment.setDifficulty(3);
        assignment.setConfidence(EstimateConfidence.MEDIUM);
        assignment.setMaterialHash("abc123");
        assignment.setSubmitted(true);

        Assignment unestimated = new Assignment();
        unestimated.setFolderId("405060");
        unestimated.setName("Not Estimated Yet");
        testSemester.getCourses().get(0).addAssignment(unestimated);

        repo.saveSemester(testSemester);
        List<Assignment> loaded = repo.loadSemester("Winter2026Test").getCourses().get(0).getAssignments();

        Assignment loadedAssignment  = loaded.stream().filter(a -> a.getFolderId().equals("102030")).findFirst().orElseThrow();
        Assignment loadedUnestimated = loaded.stream().filter(a -> a.getFolderId().equals("405060")).findFirst().orElseThrow();

        assertEquals(OffsetDateTime.parse("2011-11-20T10:00:00Z"), loadedAssignment.getOpenDate());
        assertEquals(OffsetDateTime.parse("2011-12-08T10:00:00Z"), loadedAssignment.getCloseDate());
        assertEquals(6.5, loadedAssignment.getEstimatedHours());
        assertEquals(3, loadedAssignment.getDifficulty());
        assertEquals(EstimateConfidence.MEDIUM, loadedAssignment.getConfidence());
        assertEquals("abc123", loadedAssignment.getMaterialHash());
        assertTrue(loadedAssignment.isSubmitted());

        assertNull(loadedUnestimated.getEstimatedHours());
        assertNull(loadedUnestimated.getDifficulty());
        assertNull(loadedUnestimated.getConfidence());
        assertFalse(loadedUnestimated.isSubmitted());
    }

    /**
     * Ensures a database created before effort ranking existed is upgraded in place and keeps its data.
     */
    @Test
    public void migratesDatabaseFromEarlierVersion() throws SQLException{
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:test.db");
            Statement statement = connection.createStatement()){
            statement.execute("CREATE TABLE semester(name TEXT PRIMARY KEY)");
            statement.execute("CREATE TABLE course(orgUnitId TEXT PRIMARY KEY, semesterName TEXT, name TEXT, isWorthCredits INTEGER)");
            statement.execute("""
                CREATE TABLE assignment(folderId TEXT PRIMARY KEY, orgUnitId TEXT, name TEXT, dueDate TEXT, instructionText TEXT,
                                        llmSummary TEXT, priority INTEGER, reasoning TEXT, calendarEventId TEXT)
                """);
            statement.execute("CREATE TABLE attachment(fileId TEXT PRIMARY KEY, folderId TEXT, fileName TEXT, fileSize INTEGER, attachmentText TEXT)");
            statement.execute("INSERT INTO semester VALUES('Winter2026Test')");
            statement.execute("INSERT INTO course VALUES('12345', 'Winter2026Test', 'courseOne', 1)");
            statement.execute("INSERT INTO assignment VALUES('102030', '12345', 'AssignmentOne', '2011-12-03T10:15:30Z', 'Text', 'Old summary', 3, 'Old reasoning', '101202')");
        }

        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        Semester migrated = repo.loadSemester("Winter2026Test");
        Assignment assignment = migrated.getCourses().get(0).getAssignments().get(0);

        assertEquals("Old summary", assignment.getLlmSummary());
        assertEquals("101202", assignment.getCalendarEventId());
        assertNull(assignment.getEstimatedHours());
        assertFalse(assignment.isSubmitted());

        assignment.setEstimatedHours(2.0);
        repo.saveSemester(migrated);
        assertEquals(2.0, repo.loadSemester("Winter2026Test").getCourses().get(0).getAssignments().get(0).getEstimatedHours());
    }

    /**
     * Ensures Content section files that no longer match are removed on save, while the
     * assignment's own attachments are kept.
     */
    @Test
    public void replacesLinkedCourseContentOnSave(){
        SqliteRepository repo = new SqliteRepository("jdbc:sqlite:test.db");
        Semester testSemester = createTestSemester();
        Assignment assignment = testSemester.getCourses().get(0).getAssignments().get(0);

        Attachment oldVersion = new Attachment();
        oldVersion.setFileId(Attachment.courseContentId("102030", 55, "2026-09-01T00:00:00.000Z"));
        oldVersion.setFileName("Instructions");
        oldVersion.setAttachmentText("Old instructions");
        assignment.addAttachment(oldVersion);
        repo.saveSemester(testSemester);

        Attachment newVersion = new Attachment();
        newVersion.setFileId(Attachment.courseContentId("102030", 55, "2026-09-15T00:00:00.000Z"));
        newVersion.setFileName("Instructions");
        newVersion.setAttachmentText("Updated instructions");
        assignment.getAttachments().remove(oldVersion);
        assignment.addAttachment(newVersion);
        repo.saveSemester(testSemester);

        List<Attachment> loaded = repo.loadSemester("Winter2026Test").getCourses().get(0).getAssignments().get(0).getAttachments();
        List<String> texts = loaded.stream().map(Attachment::getAttachmentText).toList();

        assertEquals(2, loaded.size());
        assertTrue(texts.contains("Attachment test text here"));
        assertTrue(texts.contains("Updated instructions"));
    }
}