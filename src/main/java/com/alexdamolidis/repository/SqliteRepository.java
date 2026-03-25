package com.alexdamolidis.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.DatabaseConnectionException;
import com.alexdamolidis.exception.DatabaseTransactionException;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;

public class SqliteRepository {
    private static final Logger logger = LoggerFactory.getLogger(SqliteRepository.class);
    private final String dbUrl;

    public SqliteRepository(){
        this.dbUrl = "jdbc:sqlite:tracker.db";
        initDatabase();
    }

    //constructor for tests/demo with dependency injection
    public SqliteRepository(String dbUrl){
        this.dbUrl = dbUrl;
        initDatabase();
    }

    /**
     * Initializes the repository by creating the semester, course, assignment, and attachments tables. 
     * invoked by SqliteRepository constructor.
    */
    private void initDatabase(){
        try(Connection connection = DriverManager.getConnection(dbUrl);
            Statement statement = connection.createStatement()){
            logger.info("Initializing database at path = {}", dbUrl);

            String createSemester= """
                CREATE TABLE IF NOT EXISTS semester(
                name TEXT PRIMARY KEY);
            """;

            String createCourse = """
                CREATE TABLE IF NOT EXISTS course(
                orgUnitId TEXT PRIMARY KEY,
                semesterName TEXT,
                name TEXT,
                isWorthCredits INTEGER,
                FOREIGN KEY(semesterName) REFERENCES semester(name));
            """;

            String createAssignment = """
                CREATE TABLE IF NOT EXISTS assignment(
                folderId TEXT PRIMARY KEY,
                orgUnitId TEXT,
                name TEXT,
                dueDate TEXT,
                instructionText TEXT,
                llmSummary TEXT,
                priority INTEGER,
                reasoning TEXT,
                calendarEventId TEXT,
                FOREIGN KEY(orgUnitId) REFERENCES course(orgUnitId));
            """;

            String createAttachment = """
                CREATE TABLE IF NOT EXISTS attachment(
                fileId TEXT PRIMARY KEY,
                folderId TEXT,
                fileName TEXT,
                fileSize INTEGER,
                attachmentText TEXT,
                FOREIGN KEY(folderId) REFERENCES assignment(folderId));
            """;

            statement.execute(createSemester);
            statement.execute(createCourse);
            statement.execute(createAssignment);
            statement.execute(createAttachment);

        }catch(SQLException e){
            throw new DatabaseConnectionException("Failed to initialize the SQLite database. " + e);
        }
    }
    /**
     * Saves all semester data to the Sqlite database, does not update the semester name on conflict.
     * 
     * @param semester object to be saved to the database.
     * @throws DatabaseTransactionException If the trascation fails, rolls back before throwing
     * @throws DatabaseConnectionException If a connection error ocurrs 
     */
    public void saveSemester(Semester semester){
        String insertSemester = """
                INSERT INTO semester(name) VALUES(?)
                ON CONFLICT(name) DO NOTHING;
                """;
        try(Connection connection = DriverManager.getConnection(dbUrl)){
            connection.setAutoCommit(false);
            try{
                try(PreparedStatement semesterInsertStmt = connection.prepareStatement(insertSemester)){
                    semesterInsertStmt.setString(1, semester.getName());
                    semesterInsertStmt.executeUpdate();
                }
                if(semester.getCourses() != null){
                    for(Course course : semester.getCourses()){
                        saveCourse(connection, course, semester.getName());
                    }
                }
                logger.info("Saving semester: {} with {} courses.", semester.getName(), semester.getCourses().size());
                connection.commit();

            }catch(SQLException e){
                connection.rollback();
                throw new DatabaseTransactionException("Transaction failed, rolling back. " + e);
            }
        }catch(SQLException e){
            throw new DatabaseConnectionException("Database connection error: " + e);
        }
    }

    /**
     * Saves a course object to the database. Brightspace should not update course data in the middle of 
     * a semester, so data is not updated on conflict.
     * 
     * @param connection Database connection opened by saveSemester.
     * @param course Course object to be saved.
     * @param semesterName Crrent semester name (semester table primary key)
     * @throws SQLException If a database access error occurs. This exception is 
     * bubbled up to the caller to trigger a transaction rollback.
     */
    private void saveCourse(Connection connection, Course course, String semesterName)throws SQLException{
        String insertCourse = """
                INSERT INTO course(orgUnitId, semesterName, name, isWorthCredits)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(orgUnitId) DO NOTHING;
                """;
        try(PreparedStatement courseInsertStmt = connection.prepareStatement(insertCourse)){
            courseInsertStmt.setString (1, course.getOrgUnitId());
            courseInsertStmt.setString (2, semesterName);
            courseInsertStmt.setString (3, course.getName());
            courseInsertStmt.setBoolean(4, course.getIsWorthCredits());
            courseInsertStmt.executeUpdate();
        }
        if(course.getAssignments() != null && !course.getAssignments().isEmpty()){
            for(Assignment assignment : course.getAssignments()){
                saveAssignment(connection, assignment, course.getOrgUnitId());
            }
        }
    }

    /**
     * Saves an Assignment object to the database. Updates all fields except folderId on conflict.
     * 
     * @param connection Database connection passed by saveCourse.
     * @param assignment Assignment object to be saved.
     * @param orgUnitId Course's orgUnitId that this assignment belongs to (Course primary key).
     * @throws SQLException If a database access error occurs. This exception is 
     * bubbled up to the caller to trigger a transaction rollback.
     */
    private void saveAssignment(Connection connection, Assignment assignment, String orgUnitId)throws SQLException{
        String insertAssignment = """
                INSERT INTO assignment(folderId, orgUnitId, name, dueDate, instructionText, llmSummary, priority, reasoning, calendarEventId)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(folderId) DO UPDATE SET
                    name               = excluded.name,
                    dueDate            = excluded.dueDate,
                    instructionText    = excluded.instructionText,
                    llmSummary         = excluded.llmSummary,
                    priority           = excluded.priority,
                    reasoning          = excluded.reasoning,
                    calendarEventId    = excluded.calendarEventId;
                """;
        try(PreparedStatement assignmentInsertStmt = connection.prepareStatement(insertAssignment)){
            assignmentInsertStmt.setString (1, assignment.getFolderId());
            assignmentInsertStmt.setString (2, orgUnitId);
            assignmentInsertStmt.setString (3, assignment.getName());
            assignmentInsertStmt.setString (4, assignment.getDueDate() != null ? assignment.getDueDate().toString() : null);
            assignmentInsertStmt.setString (5, assignment.getInstructionText());
            assignmentInsertStmt.setString (6, assignment.getLlmSummary());
            assignmentInsertStmt.setInt    (7, assignment.getPriority());
            assignmentInsertStmt.setString (8, assignment.getReasoning());
            assignmentInsertStmt.setString (9, assignment.getCalendarEventId());
            assignmentInsertStmt.executeUpdate();
        }
        if(assignment.getAttachments() != null && !assignment.getAttachments().isEmpty()){
            for(Attachment attachment : assignment.getAttachments()){
                saveAttachment(connection, attachment, assignment.getFolderId());
            }
        }
    }

    /**
     * Saves Attachment to database. Updates all fields but fileId on conflict.
     * 
     * @param connection Database connection passes by saveAssignment.
     * @param attachment Attachment object to be saved.
     * @param folderId Assignment's folderId this attachment belongs to (Assignment primary key).
     * @throws SQLException If a database access error occurs. This exception is 
     * bubbled up to the caller to trigger a transaction rollback.
     */
    private void saveAttachment(Connection connection, Attachment attachment, String folderId)throws SQLException{
        String insertAttachment = """
                INSERT INTO attachment(fileId, folderId, fileName, fileSize, attachmentText)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(fileId) DO UPDATE SET
                    fileName       = excluded.fileName,
                    fileSize       = excluded.fileSize,
                    attachmentText = excluded.attachmentText;
                """;
        try(PreparedStatement attachmentInsertStmt = connection.prepareStatement(insertAttachment)){
            attachmentInsertStmt.setString(1, attachment.getFileId());
            attachmentInsertStmt.setString(2, folderId);
            attachmentInsertStmt.setString(3, attachment.getFileName());
            attachmentInsertStmt.setInt   (4, attachment.getFileSize());
            attachmentInsertStmt.setString(5, attachment.getAttachmentText());
            attachmentInsertStmt.executeUpdate();
        }
    }

    /**
     * Loads all semester data from repository .
     * 
     * @param semesterName The name of the semester to load.
     * @return The populated Semester object, or null if not found.
     * @throws DatabaseConnectionException If a database error occurs during the selection or 
     * rehydration of the Semester object.
     */
    public Semester loadSemester(String semesterName){
        String selectSemesterSql = "SELECT * FROM semester WHERE name = ?";

        try(Connection connection = DriverManager.getConnection(dbUrl);
            PreparedStatement selectSemesterStmt = connection.prepareStatement(selectSemesterSql)){

            selectSemesterStmt.setString(1, semesterName);

            try(ResultSet semesterResult = selectSemesterStmt.executeQuery()){
                if(!semesterResult.next()){
                    return null;
                }
                Semester semester = new Semester(semesterResult.getString("name"));

                semester.setCourses(getCoursesForSemester(connection, semesterName));

                logger.info("loading the semester data from {}", dbUrl);
                return semester;
            }
        }catch(SQLException e){
            throw new DatabaseConnectionException("Error loading semester: " + e);
        }
    }

    /**
     * Retrieves all courses associated with a specific semester.
     * 
     * @param connection The active database connection.
     * @param semesterName The name of the semester.
     * @return A list of Course objects.
     * @throws SQLException If a database access error occurs.
     */
    private List<Course> getCoursesForSemester(Connection connection, String semesterName)throws SQLException{
        List<Course> courses    = new ArrayList<>();
        String selectCoursesSql = "SELECT * FROM course WHERE semesterName = ?"; 

        try(PreparedStatement selectCourseStmt = connection.prepareStatement(selectCoursesSql)){
            selectCourseStmt.setString(1, semesterName);
            try(ResultSet courseResult = selectCourseStmt.executeQuery()){
                while(courseResult.next()){
                    Course course = new Course();
                    course.setOrgUnitId     (courseResult.getString("orgUnitId"));
                    course.setName          (courseResult.getString("name"));
                    course.setIsWorthCredits(courseResult.getInt   ("isWorthCredits") == 1);

                    course.setAssignments(getAssignmentsForCourse(connection, course.getOrgUnitId()));
                    courses.add(course);
                }
            }
        }
        return courses;
    }

    /**
     * Retrieves all Assignemnts associated with a specific Course.
     * 
     * @param connection The active database connection.
     * @param orgUnitId Unit Id of course these assignments belong to.
     * @return List of assignments.
     * @throws SQLException If a database access error occurs.
     */
    private List<Assignment> getAssignmentsForCourse(Connection connection, String orgUnitId)throws SQLException{
        List<Assignment> assignments = new ArrayList<>();
        String selectAssignmentsSql  = "SELECT * FROM assignment WHERE orgUnitId = ?";

        try(PreparedStatement selectAssignmentStmt = connection.prepareStatement(selectAssignmentsSql)){
            selectAssignmentStmt.setString(1, orgUnitId);
            try(ResultSet assignmentResult = selectAssignmentStmt.executeQuery()){
                while(assignmentResult.next()){
                    Assignment assignment = new Assignment();
                    assignment.setFolderId          (assignmentResult.getString("folderId"));
                    assignment.setName              (assignmentResult.getString("name"));
                    
                    String date = assignmentResult.getString("dueDate");
                    if(date != null){
                        assignment.setDueDate(OffsetDateTime.parse(date));
                    }
                    assignment.setInstructionText   (assignmentResult.getString("instructionText"));
                    assignment.setLlmSummary        (assignmentResult.getString("llmSummary"));
                    assignment.setPriority          (assignmentResult.getInt("priority"));
                    assignment.setReasoning         (assignmentResult.getString("reasoning"));
                    assignment.setCalendarEventId   (assignmentResult.getString("calendarEventId"));

                    assignment.setAttachments(getAttachmentsForAssignment(connection, assignment.getFolderId()));
                    assignments.add(assignment);
                }
            }
        }
        return assignments;
    }

    /**
     * Retrieves all Attachments associated with a specific Assignment.
     * 
     * @param connection The active database connection.
     * @param folderId Folder Id of Assignment that these Attachments belong to.
     * @return List of Attachments.
     * @throws SQLException If a database access error occurs.
     */
    private List<Attachment> getAttachmentsForAssignment(Connection connection, String folderId)throws SQLException{
        List<Attachment> attachments = new ArrayList<>();
        String selectAttachmentsSql = "SELECT * FROM attachment WHERE folderId = ?";

        try(PreparedStatement selectAttachmentStmt = connection.prepareStatement(selectAttachmentsSql)){
            selectAttachmentStmt.setString(1, folderId);
            try(ResultSet attachmentResult = selectAttachmentStmt.executeQuery()){
                while(attachmentResult.next()){
                    Attachment attachment = new Attachment();
                    attachment.setFileId(attachmentResult.getString        ("fileId"));
                    attachment.setFileName(attachmentResult.getString      ("fileName"));
                    attachment.setFileSize(attachmentResult.getInt         ("fileSize"));
                    attachment.setAttachmentText(attachmentResult.getString("attachmentText"));

                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    /**
     * Batch updates calendarEventId for multiple assignments in a single transaction.
     * Called after Google Calendar sync to avoid redundant connection overhead.
     * 
     * @param eventIds Map of folderId to calendarEventId pairs to be saved.
     * @throws DatabaseTransactionException If the trascation fails, rolls back before throwing
     * @throws DatabaseConnectionException If a connection error ocurrs 
     */
    public void saveCalendarEventIds(Map<String, String> eventIds){
        if(eventIds == null || eventIds.isEmpty())return;

        String updateCalendarEventId = """
                UPDATE assignment
                SET calendarEventId = ?
                WHERE folderId = ?;
                """;

        try(Connection connection = DriverManager.getConnection(dbUrl)){
            connection.setAutoCommit(false);
            try(PreparedStatement updateEventIdStmt = connection.prepareStatement(updateCalendarEventId)){
                for(Map.Entry<String, String> entry : eventIds.entrySet()){
                    updateEventIdStmt.setString(1, entry.getValue());
                    updateEventIdStmt.setString(2, entry.getKey());
                    updateEventIdStmt.addBatch();
                }
                updateEventIdStmt.executeBatch();
                connection.commit();
                
            }catch(SQLException e){
                connection.rollback();
                throw new DatabaseTransactionException("Calendar event ID batch update failed, rolling back.", e);
            }
        }catch(SQLException e){
            throw new DatabaseConnectionException("Database connection error during calendar event ID save: ", e);
        }
    }
}