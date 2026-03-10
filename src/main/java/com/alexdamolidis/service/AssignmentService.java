package com.alexdamolidis.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import com.alexdamolidis.model.BrightspaceWrapper;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.parser.StringParser;
import com.alexdamolidis.repository.SemesterRepository;
import com.alexdamolidis.util.EndpointBuilder;
import com.alexdamolidis.util.AttachmentProcessingException;
import com.alexdamolidis.util.BrightspaceClient;
import com.alexdamolidis.util.ContentExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class AssignmentService {
    private final ObjectMapper mapper;
    private final BrightspaceClient scraper;
    private final ContentExtractor extractor;

    public AssignmentService(BrightspaceClient scraper) {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.scraper = scraper;
        this.extractor = ContentExtractor.getInstance();
    }

    /**
     * accept a semester name from the user for the semester object.
     * 
     * @return name
     */
    public String createSemesterName() {
        Scanner scan = new Scanner(System.in);
        System.out.print("Please enter the semester you are in: ");
        String name = scan.nextLine();
        scan.close();
        return name;
    }
    /**
     * fetch, extract, and assign course data from Brightspace API for a specified semester.
     * 
     * @param semester semester object 
     * 
     * @return semester object hydrated with course data
     * 
     * @throws RuntimeException If the JSON respose cannot be mapped to the Course model.
    */
    public Semester fetchAndMapCourses(Semester semester){
        String coursesJson = scraper.sendGetRequest(EndpointBuilder.buildMyEnrollmentsUrl());
        try{
            BrightspaceWrapper<Course> wrapper = mapper.readValue(coursesJson,
                    new TypeReference<BrightspaceWrapper<Course>>() {});
            for (Course course : wrapper.getItems()) {
                semester.addCourse(course);
            }
            return semester;

            }catch(IOException e){
                throw new RuntimeException("Failed to parse Course data", e);
            }
        }

    /**
     * fetch, extract, and assign assignment data from Brightspace API for a
     * specified course.
     * 
     * @param course course object
     * 
     * @throws RuntimeException If the JSON respose cannot be mapped to the Assignment model.
     */
    public void hydrateCourseWithAssignments(Course course){

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
            throw new RuntimeException( course.getName() + " failed to sync. ", e);
        }
    }

    /**
     * fetch, extract, and assign attachment data from Brightspace API, if an
     * attachment is larger than 10MB it will be skipped to prevent excesive memory usage or video downloads.
     * 
     * @param assignment assignment object containing attachments.
     * 
     * @param orgUnitId unique course identifier.
     * 
     * @throws AttachmentProcessingException if attachment download or text extraction fails
     * 
     */
    public Assignment processAttachments(Assignment assignment, String orgUnitId) {
        for (Attachment attachment : assignment.getAttachments()) {

            if (attachment.getFileSize() > 10 * 1024 * 1024) {
                System.out.println("Skipping large file: " + attachment.getFileName());
                continue;
            }
            try {
                String url = EndpointBuilder.buildAttachmentUrl(orgUnitId, assignment.getFolderId(),
                        attachment.getFileId());

                byte[] attachmentByteArray = scraper.downloadAttachment(url);
                if (attachmentByteArray != null) {
                    String extractedText = extractor.extractTextFromBytes(attachmentByteArray);
                    String cleanExtractedText = StringParser.cleanHtml(extractedText);

                    attachment.setAttachmentText(cleanExtractedText);
                }
            } catch (AttachmentProcessingException e) {
                throw new RuntimeException("Failed to process file " + attachment.getFileName() + ": " + e.getMessage());
            }
        }
        return assignment;
    }

    /**
     * saves all processed data to a json, if the data directory does not exist, it
     * will create it.
     * 
     * @param semester semester object
     * 
     * @throws RuntimeException If the semester object cannot be saved to a JSON file.
     * 
     */
    public void saveSemesterToFile(Semester semester) {
        try {
            File directory = new File("data");
            if (!directory.exists()) {
                directory.mkdir();
            }
            File file = new File(directory, semester.getName() + "_data.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, semester);
            System.out.println("Data saved to :" + file.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save semester data: " + e);
        }
    }

    public Semester runFullSync(String name) {
        Semester semester = new Semester(name);
        fetchAndMapCourses(semester);
        for (Course course : semester.getCourses()) {
            if (course.getIsWorthCredits()) {
                hydrateCourseWithAssignments(course);
            }
        }
        // System.out.println(semester);
        return semester;
    }

    public Semester runSmartSync(String name) {
        SemesterRepository loadData = new SemesterRepository();
        Semester semester = loadData.loadCollectedData("data/" + name + "_data.json");
        checkForNewCourses(semester);
        checkForNewAssignments(semester);
        // System.out.println(semester);

        return semester;
    }

    public Semester sync(){
        String name = createSemesterName();

        if (!new File("data/" + name + "_data.json").exists()) {
            System.out.println("No local data detected, running full sync");
            return runFullSync(name);
        } else {
            System.out.println("Local data detected, running smart sync");
            return runSmartSync(name);
        }

    }

    /**
     * checks exsisting getOrgUnitId's against new getOrgUnitId's and adds any newly
     * discovered courses to the semester.
     * 
     * @param semester Semester object
     * 
     * @throws RuntimeException If the JSON respose cannot be mapped to the Course
     * model.
     */
    public void checkForNewCourses(Semester semester) {
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
            throw new RuntimeException("Failed to parse Course data", e);
        }
    }

    /**
     * checks exsisting assignment folderIds against new assignment folderIds and adds
     * any newly discovered assignments to the course. Also checks existing assignment 
     * names and dates against new assignment data, updates if new data is different.
     * 
     * @param semester Semester object
     * 
     * @throws RuntimeException If the JSON response cannot be mapped to the
     * Assignment model.
     */
    public void checkForNewAssignments(Semester semester) {
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
                        course.addAssignment(newAssignmentData);

                        if (newAssignmentData.getAttachments() != null){
                            processAttachments(newAssignmentData, course.getOrgUnitId());
                        }
                            
                    }else{
                        Assignment existingAssignment = existingAssignmentsMap.get(newAssignmentData.getFolderId());
                        if (existingAssignment.updateNameAndDateProperties(newAssignmentData)){
                            System.out.println("Detected change in: " + existingAssignment.getName());
                        }
                    }
                }
            }catch (IOException e){
                throw new RuntimeException( course.getName() + " failed to sync. ", e);
            }
        }
    }

    /**
     * Collects a count of assignments from courses worth credits from semester object.
     * @param semester Semester object with assignments to be counted.
     * @return count Count of assignments worth credits.
     */
    public int countAssignmentsWorthCredits(Semester semester){
        int count = 0;

        for(Course course : semester.getCourses()){
            if(course.getIsWorthCredits() && course.getAssignments() != null){
                count += course.getAssignments().size();
            }

        }

        return count;
    }
}
