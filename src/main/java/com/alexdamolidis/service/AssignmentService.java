package com.alexdamolidis.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.client.BrightspaceDataSource;
import com.alexdamolidis.exception.AttachmentProcessingException;
import com.alexdamolidis.exception.BrightspaceSyncException;
import com.alexdamolidis.exception.TrackerApiException;
import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.*;
import com.alexdamolidis.model.ContentToc.ContentTopic;
import com.alexdamolidis.parser.StringParser;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class AssignmentService {
    private static final Logger logger = LoggerFactory.getLogger(AssignmentService.class);

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    // Content section files can be long lecture decks or data sheets, so each one is capped before it is sent to Claude.
    static final int MAX_CONTENT_CHARS = 20_000;

    private final ObjectMapper mapper;
    private final BrightspaceDataSource scraper;
    private final ContentExtractor extractor;
    private final SqliteRepository repo;

    public AssignmentService(BrightspaceDataSource scraper, SqliteRepository repo) {
        this.mapper = new ObjectMapper();
        this.repo = repo;
        this.scraper = scraper;
        this.extractor = ContentExtractor.getInstance();

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * fetch, extract, and assign course data from Brightspace API for a specified semester.
     *
     * @param semester semester object
     * @return semester object hydrated with course data
     * @throws TrackerException If the JSON respose cannot be mapped to the Course model.
    */
    private Semester fetchAndMapCourses(Semester semester){
        String coursesJson = scraper.sendGetRequest(EndpointBuilder.buildMyEnrollmentsUrl());
        try{
            BrightspaceWrapper<Course> wrapper = mapper.readValue(coursesJson,
                    new TypeReference<BrightspaceWrapper<Course>>() {});
            for (Course course : wrapper.getItems()) {
                semester.addCourse(course);
            }
            return semester;

            }catch(IOException e){
                throw new TrackerException("Failed to parse Course data", e);
            }
        }

    /**
     * fetch, extract, and assign assignment data from Brightspace API for a
     * specified course.
     *
     * @param course course object
     * @throws TrackerException If the JSON respose cannot be mapped to the Assignment model.
     */
    private void hydrateCourseWithAssignments(Course course){

        String assignmentsJson = scraper.sendGetRequest(EndpointBuilder.buildAllAssignmentsUrl(course.getOrgUnitId()));

        try{
            List<Assignment> assignments = mapper.readValue(assignmentsJson, new TypeReference<List<Assignment>>() {});
            for (Assignment assign : assignments) {
                cleanInstructions(assign);
                if (assign.getAttachments() == null)
                    continue;
                processAttachments(assign, course.getOrgUnitId());
            }
            course.setAssignments(assignments);
        }catch(IOException e){
            throw new TrackerException( course.getName() + " failed to sync. ", e);
        }
    }

    /**
     * Strips HTML from instructions. Applied the same way on full and smart syncs, so the text
     * sent to Claude does not change between runs unless the professor changed it.
     */
    private void cleanInstructions(Assignment assignment){
        if (assignment.getInstructionText() != null) {
            assignment.setInstructionText(StringParser.cleanHtml(assignment.getInstructionText()));
        }
    }

    /**
     * fetch, extract, and assign attachment data from Brightspace API, if an
     * attachment is larger than 10MB it will be skipped to prevent excesive memory usage or video downloads.
     * An attachment that fails to download or extract is skipped with a warning, leaving its text null,
     * so one unreadable file does not abort the whole sync.
     *
     * @param assignment assignment object containing attachments.
     * @param orgUnitId unique course identifier.
     * @throws RateLimitReachedException if Brightspace is still rate limiting after 3 retries
     */
    Assignment processAttachments(Assignment assignment, String orgUnitId) {
        for (Attachment attachment : assignment.getAttachments()) {
            readAttachment(attachment, assignment, orgUnitId);
        }
        return assignment;
    }

    private void readAttachment(Attachment attachment, Assignment assignment, String orgUnitId){
        if (attachment.getFileSize() > MAX_FILE_BYTES) {
            logger.warn("Skipping large file: '{}'", attachment.getFileName());
            return;
        }
        String url = EndpointBuilder.buildAttachmentUrl(orgUnitId, assignment.getFolderId(), attachment.getFileId());
        try {
            attachment.setAttachmentText(downloadText(url));

        } catch (AttachmentProcessingException | TrackerApiException e) {
            logger.warn("Skipping attachment '{}' on '{}', it could not be read: {}",
                        attachment.getFileName(), assignment.getName(), e.getMessage());
        }
    }

    /**
     * Downloads a file and extracts its text.
     *
     * @param url the file's download URL
     * @return the cleaned text, or null if nothing was downloaded
     * @throws AttachmentProcessingException if the download fails or the text cannot be extracted
     * @throws TrackerApiException if Brightspace rejects the request
     */
    private String downloadText(String url){
        byte[] bytes = scraper.downloadAttachment(url);
        if (bytes == null) return null;

        if (bytes.length > MAX_FILE_BYTES) {
            throw new AttachmentProcessingException("File is larger than 10MB");
        }
        return StringParser.cleanHtml(extractor.extractTextFromBytes(bytes));
    }

    private Semester runFullSync(Semester semester) {
        fetchAndMapCourses(semester);
        for (Course course : semester.getCourses()) {
            if (course.getIsWorthCredits()) {
                hydrateCourseWithAssignments(course);
            }
        }
        return semester;
    }

    private Semester runSmartSync(Semester semester) {
        checkForNewCourses(semester);
        checkForNewAssignments(semester);
        return semester;
    }

    public Semester sync(String semesterName){
        Semester existingSemester = repo.loadSemester(semesterName);
        Semester semester;

        if(existingSemester == null){
            logger.info("No local data detected, running full sync");
            semester = new Semester(semesterName);
            runFullSync(semester);
        }else{
            logger.info("Local data detected, running smart sync");
            semester = existingSemester;
            runSmartSync(semester);
        }

        for (Course course : semester.getCourses()) {
            if (!course.getIsWorthCredits() || course.getAssignments() == null) continue;

            attachCourseContent(course);
            updateSubmissionStatus(course);
        }
        return semester;
    }

    /**
     * checks exsisting getOrgUnitId's against new getOrgUnitId's and adds any newly
     * discovered courses to the semester.
     *
     * @param semester Semester object.
     * @throws TrackerException If the JSON respose cannot be mapped to the Course
     * model.
     */
    void checkForNewCourses(Semester semester) {
        Set<String> existingCourseIds = semester.getCourses().stream()
                                                .map(Course::getOrgUnitId)
                                                .collect(Collectors.toSet());

        String coursesJson = scraper.sendGetRequest(EndpointBuilder.buildMyEnrollmentsUrl());
        try {
            BrightspaceWrapper<Course> wrapper = mapper.readValue(coursesJson,
                    new TypeReference<BrightspaceWrapper<Course>>() {});

            for (Course newCourseData : wrapper.getItems()) {
                if (!existingCourseIds.contains(newCourseData.getOrgUnitId())) {
                    semester.addCourse(newCourseData);
                }
            }
        } catch (IOException e) {
            throw new TrackerException("Failed to parse Course data", e);
        }
    }

    /**
     * checks exsisting assignment folderIds against new assignment folderIds and adds
     * any newly discovered assignments to the course. Existing assignments pick up changes to their
     * name, dates, and instructions, and any attachments added since the last run are downloaded.
     *
     * @param semester object to be checked
     * @throws TrackerException If the JSON response cannot be mapped to the
     * Assignment model.
     */
    void checkForNewAssignments(Semester semester) {
        for (Course course : semester.getCourses()) {
            if (!course.getIsWorthCredits())
                continue;
            String assignmentsJson = scraper.sendGetRequest(EndpointBuilder.buildAllAssignmentsUrl(course.getOrgUnitId()));
            try{
                List<Assignment> assignmentsFromApi = mapper.readValue(assignmentsJson, new TypeReference<List<Assignment>>(){});

                Map<String, Assignment> existingAssignmentsMap = course.getAssignments().stream()
                                                                       .collect(Collectors.toMap(Assignment::getFolderId, a -> a));
                for (Assignment newAssignmentData : assignmentsFromApi) {
                    cleanInstructions(newAssignmentData);

                    if (!existingAssignmentsMap.containsKey(newAssignmentData.getFolderId())){

                        if (newAssignmentData.getAttachments() != null){
                            processAttachments(newAssignmentData, course.getOrgUnitId());
                        }
                        course.addAssignment(newAssignmentData);

                    }else{
                        Assignment existingAssignment = existingAssignmentsMap.get(newAssignmentData.getFolderId());
                        if (existingAssignment.updateFromApi(newAssignmentData)){
                            logger.info("Detected change in: '{}'", existingAssignment.getName());
                        }
                        addNewAttachments(existingAssignment, newAssignmentData, course.getOrgUnitId());
                    }
                }
            }catch (IOException e){
                throw new TrackerException( course.getName() + " failed to sync. ", e);
            }
        }
    }

    private void addNewAttachments(Assignment existing, Assignment fromApi, String orgUnitId){
        if (fromApi.getAttachments() == null) return;
        if (existing.getAttachments() == null) existing.setAttachments(new ArrayList<>());

        Set<String> knownFileIds = existing.getAttachments().stream()
                                           .map(Attachment::getFileId)
                                           .collect(Collectors.toSet());

        for (Attachment attachment : fromApi.getAttachments()) {
            if (knownFileIds.contains(attachment.getFileId())) continue;

            readAttachment(attachment, existing, orgUnitId);
            existing.addAttachment(attachment);
            logger.info("New attachment '{}' found on '{}'", attachment.getFileName(), existing.getName());
        }
    }

    /**
     * Links files from the course's Content section to each assignment by name (see {@link ContentMatcher}),
     * since professors often post instructions there instead of in the assignment folder. Files already
     * downloaded are reused unless Brightspace reports they were modified. If the Content section cannot be
     * read, the course keeps its previously linked files and the sync continues.
     *
     * @param course credit bearing course whose assignments should be linked
     * @throws RateLimitReachedException if Brightspace is still rate limiting after 3 retries
     */
    void attachCourseContent(Course course) {
        ContentToc toc;
        try {
            String tocJson = scraper.sendGetRequest(EndpointBuilder.buildContentTocUrl(course.getOrgUnitId()));
            toc = mapper.readValue(tocJson, ContentToc.class);

        } catch (IOException | TrackerApiException | BrightspaceSyncException e) {
            logger.warn("Could not read the Content section for '{}', continuing without it: {}", course.getName(), e.getMessage());
            return;
        }

        for (Assignment assignment : course.getAssignments()) {
            List<ContentTopic> matches = ContentMatcher.findMatches(toc, assignment.getName());
            linkContentFiles(assignment, matches, course.getOrgUnitId());
        }
    }

    private void linkContentFiles(Assignment assignment, List<ContentTopic> matches, String orgUnitId){
        if (assignment.getAttachments() == null) assignment.setAttachments(new ArrayList<>());

        Map<String, Attachment> alreadyLinked = assignment.getAttachments().stream()
                                                          .filter(Attachment::isCourseContent)
                                                          .collect(Collectors.toMap(Attachment::getFileId, a -> a));
        List<Attachment> linked = new ArrayList<>();

        for (ContentTopic topic : matches) {
            String fileId = Attachment.courseContentId(assignment.getFolderId(), topic.topicId(), topic.lastModifiedDate());
            Attachment existing = alreadyLinked.get(fileId);

            if (existing != null) {
                linked.add(existing);
                continue;
            }
            Attachment downloaded = downloadContentFile(topic, fileId, assignment, orgUnitId);
            if (downloaded != null) {
                linked.add(downloaded);
                logger.info("Linked course content '{}' to '{}'", topic.title(), assignment.getName());
            }
        }
        assignment.getAttachments().removeIf(Attachment::isCourseContent);
        assignment.getAttachments().addAll(linked);
    }

    /**
     * Downloads a Content section file and wraps it as an attachment of the assignment.
     *
     * @return the attachment, or null if the file could not be read, so it is tried again next run
     */
    private Attachment downloadContentFile(ContentTopic topic, String fileId, Assignment assignment, String orgUnitId){
        try {
            String text = downloadText(EndpointBuilder.buildContentFileUrl(orgUnitId, topic.topicId()));
            if (text == null) return null;

            if (text.length() > MAX_CONTENT_CHARS) {
                text = text.substring(0, MAX_CONTENT_CHARS) + " [truncated]";
            }
            Attachment attachment = new Attachment();
            attachment.setFileId(fileId);
            attachment.setFileName(topic.title());
            attachment.setFileSize(text.length());
            attachment.setAttachmentText(text);
            return attachment;

        } catch (AttachmentProcessingException | TrackerApiException e) {
            logger.warn("Skipping course content '{}' for '{}', it could not be read: {}",
                        topic.title(), assignment.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Marks assignments as submitted once Brightspace shows a submission from the current user.
     * Brightspace only answers while a folder is open, so folders outside their availability window
     * are skipped, and once an assignment is marked submitted it is not checked again.
     *
     * @param course credit bearing course whose assignments should be checked
     * @throws RateLimitReachedException if Brightspace is still rate limiting after 3 retries
     */
    void updateSubmissionStatus(Course course) {
        OffsetDateTime now = OffsetDateTime.now();

        for (Assignment assignment : course.getAssignments()) {
            if (assignment.isSubmitted() || assignment.getDueDate() == null || !assignment.isOpenForSubmission(now)) continue;

            try {
                String json = scraper.sendGetRequest(EndpointBuilder.buildMySubmissionsUrl(course.getOrgUnitId(), assignment.getFolderId()));
                if (hasSubmission(json)) {
                    assignment.setSubmitted(true);
                    logger.info("'{}' has been submitted", assignment.getName());
                }
            } catch (IOException | TrackerApiException | BrightspaceSyncException e) {
                logger.debug("Could not check submission status for '{}': {}", assignment.getName(), e.getMessage());
            }
        }
    }

    private boolean hasSubmission(String submissionsJson) throws IOException {
        for (JsonNode entity : mapper.readTree(submissionsJson)) {
            JsonNode submissions = entity.get("Submissions");
            if (submissions != null && !submissions.isEmpty()) return true;
        }
        return false;
    }
}
