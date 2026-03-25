package com.alexdamolidis.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.*;
import com.alexdamolidis.parser.StringParser;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.util.*;    
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class AssignmentService {
    private static final Logger logger = LoggerFactory.getLogger(AssignmentService.class);
    private final ObjectMapper mapper;
    private final BrightspaceClient scraper;
    private final ContentExtractor extractor;
    private final SqliteRepository repo;

    public AssignmentService(BrightspaceClient scraper, SqliteRepository repo) {
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
                if (assign.getInstructionText() != null) {
                    String cleanText = StringParser.cleanHtml(assign.getInstructionText());
                    assign.setInstructionText(cleanText);
                }
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
     * fetch, extract, and assign attachment data from Brightspace API, if an
     * attachment is larger than 10MB it will be skipped to prevent excesive memory usage or video downloads.
     * 
     * @param assignment assignment object containing attachments.
     * @param orgUnitId unique course identifier.
     */
    private Assignment processAttachments(Assignment assignment, String orgUnitId) {
        for (Attachment attachment : assignment.getAttachments()) {

            if (attachment.getFileSize() > 10 * 1024 * 1024) {
                logger.warn("Skipping large file: '{}'", attachment.getFileName());
                continue;
            }
                String url = EndpointBuilder.buildAttachmentUrl(orgUnitId, assignment.getFolderId(),
                        attachment.getFileId());

                byte[] attachmentByteArray = scraper.downloadAttachment(url);
                if (attachmentByteArray != null) {
                    String extractedText = extractor.extractTextFromBytes(attachmentByteArray);
                    String cleanExtractedText = StringParser.cleanHtml(extractedText);

                    attachment.setAttachmentText(cleanExtractedText);
                }
        }
        return assignment;
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

        if(existingSemester == null){
            logger.info("No local data detected, running full sync");
            Semester semester = new Semester(semesterName);
            runFullSync(semester);
            return semester;
        }else{
            logger.info("Local data detected, running smart sync");
            runSmartSync(existingSemester);
            return existingSemester;
        }
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
                    new TypeReference<BrightspaceWrapper<Course>>() {
                    });

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
     * any newly discovered assignments to the course. Also checks existing assignment 
     * names and dates against new assignment data, updates if new data is different.
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
                    if (!existingAssignmentsMap.containsKey(newAssignmentData.getFolderId())){

                        if (newAssignmentData.getAttachments() != null){
                            processAttachments(newAssignmentData, course.getOrgUnitId());
                        }
                        course.addAssignment(newAssignmentData);

                    }else{
                        Assignment existingAssignment = existingAssignmentsMap.get(newAssignmentData.getFolderId());
                        if (existingAssignment.updateNameAndDateProperties(newAssignmentData)){
                            logger.info("Detected change in: '{}'", existingAssignment.getName());
                        }
                    }
                }
            }catch (IOException e){
                throw new TrackerException( course.getName() + " failed to sync. ", e);
            }
        }
    }
}