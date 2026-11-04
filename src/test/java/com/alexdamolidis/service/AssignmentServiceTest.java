package com.alexdamolidis.service;

import com.alexdamolidis.client.BrightspaceDataSource;
import com.alexdamolidis.exception.AttachmentProcessingException;
import com.alexdamolidis.exception.TrackerApiException;
import com.alexdamolidis.model.*;
import com.alexdamolidis.repository.SqliteRepository;

import java.time.OffsetDateTime;
import java.util.List;

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

    /**
     * Verifies that one unreadable attachment is skipped rather than aborting the sync,
     * and that the remaining attachments on the same assignment are still processed.
    */
    @Test
    void processAttachmentsSkipsAttachmentThatCannotBeRead() {
        Assignment assignment = new Assignment();
        assignment.setFolderId("FolderA");
        assignment.setName("Lab 1");

        Attachment broken = new Attachment();
        broken.setFileId("broken");
        broken.setFileName("broken.pdf");
        assignment.addAttachment(broken);

        Attachment readable = new Attachment();
        readable.setFileId("readable");
        readable.setFileName("rubric.txt");
        assignment.addAttachment(readable);

        when(scraper.downloadAttachment(contains("broken")))
            .thenThrow(new AttachmentProcessingException("Network error during attachment download"));
        when(scraper.downloadAttachment(contains("readable"))).thenReturn("Grading rubric".getBytes());

        assignmentService.processAttachments(assignment, "101");

        assertNull(broken.getAttachmentText());
        assertEquals("Grading rubric", readable.getAttachmentText());
    }

    /**
     * Existing assignments should pick up instructions a professor adds or edits after the first run.
    */
    @Test
    void checkForNewAssignmentsPicksUpChangedInstructions() {
        Course course = createCreditCourse();
        Assignment existing = createAssignment("FolderA", "Assignment 1");
        existing.setInstructionText("See the course content.");
        course.addAssignment(existing);
        semester.addCourse(course);

        String json = """
            [ { "Id": "FolderA", "Name": "Assignment 1", "CustomInstructions": { "Text": "<p>Submit a 2 page report.</p>" } } ]
            """;
        when(scraper.sendGetRequest(contains("dropbox/folders"))).thenReturn(json);

        assignmentService.checkForNewAssignments(semester);

        assertEquals("Submit a 2 page report.", existing.getInstructionText());
    }

    /**
     * Content files named after an assignment are linked to it, videos are skipped, and files
     * already linked are not downloaded again on the next run.
    */
    @Test
    void attachCourseContentLinksMatchingFilesOnce() {
        Course course = createCreditCourse();
        Assignment assignment = createAssignment("FolderA", "Assignment 2");
        course.addAssignment(assignment);

        when(scraper.sendGetRequest(contains("content/toc"))).thenReturn(tocJson("2026-09-02T15:00:00.000Z"));
        when(scraper.downloadAttachment(contains("/content/topics/55/"))).thenReturn("Calculate the macronutrients".getBytes());

        assignmentService.attachCourseContent(course);
        assignmentService.attachCourseContent(course);

        verify(scraper, times(1)).downloadAttachment(anyString());
        assertEquals(1, assignment.getAttachments().size());
        assertTrue(assignment.getAttachments().get(0).isCourseContent());
        assertEquals("Calculate the macronutrients", assignment.getAttachments().get(0).getAttachmentText());
    }

    /**
     * A Content file edited by the professor is downloaded again and replaces the old version.
    */
    @Test
    void attachCourseContentRedownloadsEditedFiles() {
        Course course = createCreditCourse();
        Assignment assignment = createAssignment("FolderA", "Assignment 2");
        course.addAssignment(assignment);

        when(scraper.sendGetRequest(contains("content/toc")))
            .thenReturn(tocJson("2026-09-02T15:00:00.000Z"), tocJson("2026-09-20T09:00:00.000Z"));
        when(scraper.downloadAttachment(contains("/content/topics/55/")))
            .thenReturn("First draft".getBytes(), "Updated instructions".getBytes());

        assignmentService.attachCourseContent(course);
        assignmentService.attachCourseContent(course);

        assertEquals(1, assignment.getAttachments().size());
        assertEquals("Updated instructions", assignment.getAttachments().get(0).getAttachmentText());
    }

    /**
     * If the Content section cannot be read, the sync continues and previously linked files are kept.
    */
    @Test
    void attachCourseContentContinuesWhenContentCannotBeRead() {
        Course course = createCreditCourse();
        Assignment assignment = createAssignment("FolderA", "Assignment 2");
        Attachment linked = new Attachment();
        linked.setFileId(Attachment.courseContentId("FolderA", 55, "2026-09-02T15:00:00.000Z"));
        assignment.addAttachment(linked);
        course.addAssignment(assignment);

        when(scraper.sendGetRequest(contains("content/toc")))
            .thenThrow(new TrackerApiException("Brightspace API Error: 403 Forbidden."));

        assertDoesNotThrow(() -> assignmentService.attachCourseContent(course));
        assertEquals(List.of(linked), assignment.getAttachments());
    }

    /**
     * Open folders with a submission are marked submitted. Closed folders and assignments already
     * marked submitted are not checked, since Brightspace refuses requests outside the open window.
    */
    @Test
    void updateSubmissionStatusChecksOnlyOpenUnsubmittedFolders() {
        Course course = createCreditCourse();

        Assignment open = createAssignment("OpenFolder", "Assignment 1");
        Assignment closed = createAssignment("ClosedFolder", "Assignment 2");
        closed.setCloseDate(OffsetDateTime.now().minusDays(1));
        Assignment alreadySubmitted = createAssignment("SubmittedFolder", "Assignment 3");
        alreadySubmitted.setSubmitted(true);

        course.addAssignment(open);
        course.addAssignment(closed);
        course.addAssignment(alreadySubmitted);

        String submissions = """
            [ { "Status": 1, "Submissions": [ { "Id": 501, "SubmissionDate": "2026-01-15T12:00:00.000Z" } ] } ]
            """;
        when(scraper.sendGetRequest(contains("/OpenFolder/submissions/mysubmissions/"))).thenReturn(submissions);

        assignmentService.updateSubmissionStatus(course);

        assertTrue(open.isSubmitted());
        assertFalse(closed.isSubmitted());
        verify(scraper, times(1)).sendGetRequest(anyString());
    }

    @Test
    void updateSubmissionStatusLeavesUnsubmittedFoldersAlone() {
        Course course = createCreditCourse();
        Assignment open = createAssignment("OpenFolder", "Assignment 1");
        course.addAssignment(open);

        when(scraper.sendGetRequest(contains("mysubmissions"))).thenReturn("[]");

        assignmentService.updateSubmissionStatus(course);

        assertFalse(open.isSubmitted());
    }

    /**
     * New quizzes are added with a "quiz:" id and a clean name, tracked ones pick up changes without
     * losing their calendar event, placeholders without dates are left out, and every page is read.
     */
    @Test
    void syncQuizzesAddsNewQuizzesAndUpdatesTrackedOnes() {
        Course course = createCreditCourse();
        course.addAssignment(createAssignment("OpenFolder", "Assignment 1"));

        Assignment tracked = createQuiz("quiz:902", "7002");
        tracked.setName("Quiz 2");
        tracked.setCalendarEventId("event-902");
        course.addAssignment(tracked);

        String firstPage = """
            { "Objects": [
                { "QuizId": 901, "Name": "Midterm  - Requires Respondus LockDown Browser + Webcam", "IsActive": true,
                  "StartDate": "2026-03-12T14:00:00.000Z", "DueDate": "2026-03-12T16:00:00.000Z", "EndDate": "2026-03-12T16:00:00.000Z",
                  "SubmissionTimeLimit": { "IsEnforced": true, "TimeLimitValue": 110 },
                  "AttemptsAllowed": { "IsUnlimited": false, "NumberOfAttemptsAllowed": 1 }, "GradeItemId": 7001 },
                { "QuizId": 903, "Name": "SAMPLE QUIZ", "IsActive": true, "StartDate": null, "DueDate": null, "EndDate": null }
              ],
              "Next": "/d2l/api/le/1.82/101/quizzes/?bookmark=903" }
            """;
        String secondPage = """
            { "Objects": [
                { "QuizId": 902, "Name": "Quiz 2", "IsActive": true, "StartDate": null, "DueDate": null, "EndDate": "2026-03-20T03:59:59.000Z",
                  "SubmissionTimeLimit": { "IsEnforced": true, "TimeLimitValue": 25 },
                  "AttemptsAllowed": { "IsUnlimited": false, "NumberOfAttemptsAllowed": 1 }, "GradeItemId": 7002 }
              ],
              "Next": null }
            """;
        when(scraper.sendGetRequest(endsWith("/101/quizzes/"))).thenReturn(firstPage);
        when(scraper.sendGetRequest(contains("bookmark=903"))).thenReturn(secondPage);

        assignmentService.syncQuizzes(course);

        assertEquals(3, course.getAssignments().size());
        Assignment midterm = course.getAssignments().stream().filter(a -> a.getFolderId().equals("quiz:901")).findFirst().orElseThrow();
        assertEquals("Midterm", midterm.getName());
        assertEquals(AssignmentKind.TEST, midterm.getKind());
        assertEquals("Requires Respondus LockDown Browser + Webcam", midterm.getAccessRequirement());

        assertEquals(OffsetDateTime.parse("2026-03-20T03:59:59Z"), tracked.getDueDate());
        assertEquals(25, tracked.getTimeLimitMinutes());
        assertEquals("event-902", tracked.getCalendarEventId());
    }

    @Test
    void syncQuizzesKeepsTrackedQuizzesWhenTheListCannotBeRead() {
        Course course = createCreditCourse();
        Assignment tracked = createQuiz("quiz:902", "7002");
        course.addAssignment(tracked);

        when(scraper.sendGetRequest(contains("/quizzes/"))).thenThrow(new TrackerApiException("Brightspace API Error: 403 Forbidden."));

        assignmentService.syncQuizzes(course);

        assertEquals(List.of(tracked), course.getAssignments());
    }

    /**
     * A quiz is done once its grade shows up in the student's grades. Quizzes whose grade is not
     * released yet, or that have no grade item, are left as they are.
     */
    @Test
    void updateQuizStatusMarksGradedQuizzesDone() {
        Course course = createCreditCourse();
        Assignment graded       = createQuiz("quiz:1", "7001");
        Assignment notGradedYet = createQuiz("quiz:2", "7002");
        Assignment noGradeItem  = createQuiz("quiz:3", null);
        course.addAssignment(graded);
        course.addAssignment(notGradedYet);
        course.addAssignment(noGradeItem);

        when(scraper.sendGetRequest(contains("myGradeValues"))).thenReturn("""
            [ { "GradeObjectIdentifier": "7001", "GradeObjectName": "Quiz 1" },
              { "GradeObjectIdentifier": "6000", "GradeObjectName": "Assignment 1" } ]
            """);

        assignmentService.updateQuizStatus(course);

        assertTrue(graded.isSubmitted());
        assertFalse(notGradedYet.isSubmitted());
        assertFalse(noGradeItem.isSubmitted());
    }

    @Test
    void updateQuizStatusSkipsGradesWhenNoQuizIsWaitingOnOne() {
        Course course = createCreditCourse();
        course.addAssignment(createAssignment("OpenFolder", "Assignment 1"));
        Assignment done = createQuiz("quiz:1", "7001");
        done.setSubmitted(true);
        course.addAssignment(done);

        assignmentService.updateQuizStatus(course);

        verifyNoInteractions(scraper);
    }

    /**
     * Quizzes have no dropbox folder, and are not sent to Claude, so the Content and submission steps
     * leave them alone even when a Content module matches their name.
     */
    @Test
    void dropboxStepsSkipQuizzes() {
        Course course = createCreditCourse();
        Assignment quiz = createQuiz("quiz:2", "7002");
        quiz.setName("Assignment 2 Quiz");
        course.addAssignment(quiz);

        when(scraper.sendGetRequest(contains("content/toc"))).thenReturn(tocJson("2026-09-02T15:00:00.000Z"));

        assignmentService.attachCourseContent(course);
        assignmentService.updateSubmissionStatus(course);

        assertTrue(quiz.getAttachments().isEmpty());
        verify(scraper, never()).downloadAttachment(anyString());
        verify(scraper, never()).sendGetRequest(contains("mysubmissions"));
    }

    private Assignment createQuiz(String id, String gradeItemId) {
        Assignment quiz = createAssignment(id, "Quiz");
        quiz.setKind(AssignmentKind.QUIZ);
        quiz.setGradeItemId(gradeItemId);
        return quiz;
    }

    private Course createCreditCourse() {
        Course course = new Course();
        course.setOrgUnitId("101");
        course.setName("Nutrition");
        course.setIsWorthCredits(true);
        return course;
    }

    private Assignment createAssignment(String folderId, String name) {
        Assignment assignment = new Assignment();
        assignment.setFolderId(folderId);
        assignment.setName(name);
        assignment.setDueDate(OffsetDateTime.now().plusDays(3));
        return assignment;
    }

    // A Content section with a module named after "Assignment 2", holding one document and one video.
    private String tocJson(String lastModified) {
        return """
            { "Modules": [ { "ModuleId": 1, "Title": "Assignment#2", "Modules": [], "Topics": [
                { "TopicId": 55, "Title": "Assignment_2_Instructions", "TypeIdentifier": "File",
                  "Url": "/content/enforced/101/instructions.docx", "IsHidden": false, "IsBroken": false, "LastModifiedDate": "%s" },
                { "TopicId": 56, "Title": "Assignment Video Explanation", "TypeIdentifier": "ContentService",
                  "Url": "/d2l/le/contentservice/56", "IsHidden": false, "IsBroken": false, "LastModifiedDate": "%s" }
            ] } ] }
            """.formatted(lastModified, lastModified);
    }
}