package com.alexdamolidis.calendar;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.exception.CalendarAuthException;
import com.alexdamolidis.exception.CalendarSyncException;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.util.Config;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String APP_NAME = "brightspace-tracker";
    private final Calendar calendarService;
    private final SqliteRepository repository;
    private final String calendarId;

    public GoogleCalendarService(SqliteRepository repository){
        this.repository = repository;
        this.calendarId = Config.getRequired("GOOGLE_CALENDAR_ID");
        this.calendarService = buildCalendarService();
    }
    
     // Constructor used for dependency injection in tests.
    public GoogleCalendarService(SqliteRepository repository, Calendar calendarService, String calendarId){
        this.repository = repository;
        this.calendarId = calendarId;
        this.calendarService = calendarService;
    }

    private Calendar buildCalendarService(){
        try{
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            return new Calendar.Builder(
                transport,
                jsonFactory,
                CalendarAuthHelper.authorize(transport, jsonFactory))
                .setApplicationName(APP_NAME)
                .build();

        }catch(IOException | GeneralSecurityException e){
            throw new CalendarAuthException("Failed to build Google Calendar service.", e);
        }
    }

    /**
     * Synchronizes a list of courses and their assignments with Google Calendar.
     * For each assignment, this method updates an existing calendar event, inserts a new one, 
     * or marks orphaned events as [OLD] if they no longer exist in Brightspace.
     * 
     * @param courses list of courses to sync
     * @throws CalendarSyncException if a Google Calendar API request fails
     */
    public void syncAssignments(List<Course> courses) {
        try {
            Map<String, Event> remoteEvents = fetchTrackedAssignmentEvents();
            Map<String, String> eventIdsToSave = new HashMap<>();

            for (Course course : courses) {
                if (!shouldSyncCourse(course)) continue;

                for (Assignment assignment : course.getAssignments()) {
                    if(assignment.getDueDate() == null){
                        logger.warn("{} has no due date, skipping calendar sync.", assignment.getName());
                        continue;
                    }
                    logger.info("{} is being synced to Google Calendar", assignment.getName());
                    syncSingleAssignment(assignment, course.getName(), remoteEvents, eventIdsToSave);
                }
            }

            markOrphanedEvents(remoteEvents);

            if (!eventIdsToSave.isEmpty()) {
                repository.saveCalendarEventIds(eventIdsToSave);
                logger.info("Successfully updated {} calendar event IDs in the database.", eventIdsToSave.size());
            }

        } catch(TokenResponseException e){
            throw new CalendarAuthException("Google OAuth token is expired or invalid." +
                "Try deleting the file at tokens/StoredCredential and run the application again.");

        } catch(IOException e) {
            throw new CalendarSyncException("Google Calendar API request failed.", e);
        }
    }

    /**
     * Handles syncing a single assignment to Google Calendar.
     * If a matching event exists in the calendar, updates it, otherwise inserts a new event.
     * Removes the assignment from the remoteEvents map if a match is found.
     * 
     * @param assignment the assignment to sync
     * @param courseName the course name associated with the assignment
     * @param remoteEvents map of folderId to existing Google Calendar events
     * @param eventIdsToSave map to record newly created or updated event IDs for persistence
     * @throws IOException if a Google Calendar API request fails
     */
    private void syncSingleAssignment(Assignment assignment, String courseName, 
                                       Map<String, Event> remoteEvents, 
                                       Map<String, String> eventIdsToSave) throws IOException {
                                    
        String folderId = assignment.getFolderId();
        String eventId  = assignment.getCalendarEventId();

        Event remoteEvent = remoteEvents.remove(folderId);

        if (eventId == null && remoteEvent != null) { // If google has an event Id not in the db, recouple
            eventId = remoteEvent.getId();
            logger.debug("linked assignment '{}' to existing Google Event ID: {}", assignment.getName(), eventId);
        }
        if (eventId != null) {

            if (remoteEvent == null) {
                logger.warn("Event ID found in DB but missing from Google Calendar for: '{}'. Re-inserting...", assignment.getName());
                insertEvent(assignment, courseName, eventIdsToSave);
                return;
            }
            if (!isUpdateRequired(remoteEvent, assignment, courseName)) {
                logger.info("'{}' event data is already up to date.", assignment.getName());
                return;
            }
            processExistingEvent(eventId, assignment, courseName, eventIdsToSave);

        } else {
            insertEvent(assignment, courseName, eventIdsToSave);
        }
    }

    /**
     * Updates an existing calendar event, or recreates it if it was manually deleted.
     * 
     * @param eventId the Google Calendar event ID
     * @param assignment assignment associated with this event
     * @param courseName the course name associated with the assignment
     * @param eventIdsToSave map to record newly created or updated event IDs for persistence
     * @throws IOException if a Google Calendar API request fails
     * @throws CalendarSyncException if the Google Calendar API request fails
     */
    private void processExistingEvent(String eventId, Assignment assignment, String courseName, 
                                      Map<String, String> eventIdsToSave) throws IOException {
        try {
            updateEvent(eventId, assignment, courseName);

            if (assignment.getCalendarEventId() == null) {
                assignment.setCalendarEventId(eventId);
                eventIdsToSave.put(assignment.getFolderId(), eventId);
            }
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                logger.warn("Event ID not found on Google. Re-inserting: {}", assignment.getName());
                insertEvent(assignment, courseName, eventIdsToSave);
            } else {
                throw new CalendarSyncException("Google Calendar API request failed.", e);
            }
        }
    }

    /**
     * Marks events that no longer correspond to any assignments as [OLD].
     * 
     * @param orphanedEvents map of events to mark as old
     * @throws IOException if a Google Calendar API request fails
     */
    private void markOrphanedEvents(Map<String, Event> orphanedEvents) throws IOException {
        for (Event orphan : orphanedEvents.values()) {
            String summary = orphan.getSummary();

            if (summary != null && !summary.contains("[OLD]")) {
                Event patch = new Event().setSummary("[OLD] " + summary);
                calendarService.events().patch(calendarId, orphan.getId(), patch).execute();
                logger.info("Tombstoned orphaned event: {}", summary);
            }
        }
    }

    /**
     * Determines whether a course should be synced. Only credit bearing courses with 
     * at least one assignment are eligible.
     * 
     * @param course course to be checked
     * @return true if the course should be synced; false otherwise
     */
    private boolean shouldSyncCourse(Course course) {
        return course.getIsWorthCredits() && 
               course.getAssignments() != null && 
               !course.getAssignments().isEmpty();
    }

    /**
     * Inserts a new Google Calendar event for the assignment and records its ID.
     * 
     * @param assignment assignment to insert
     * @param courseName the course name associated with the assignment
     * @param eventIdsToSave map for recording folderId and eventId
     * @throws IOException if the Google Calendar API request fails
     */
    private void insertEvent(Assignment assignment, String courseName, Map<String, String> eventIdsToSave) throws IOException{
        Event event = buildEvent(assignment, courseName);
        Event created = calendarService.events().insert(calendarId, event).execute();

        assignment.setCalendarEventId(created.getId());
        eventIdsToSave.put(assignment.getFolderId(), created.getId());
        logger.info("Inserted: {}: {}" , courseName, assignment.getName());
    }

    /**
     * Updates existing event with the latest assignment details. 
     * 
     * @param eventId the ID of the event to update
     * @param assignment assignment associated with this event
     * @param courseName the course name associated with the assignment
     * @throws IOException if the Google Calendar API request fails
     */
    private void updateEvent(String eventId, Assignment assignment, String courseName) throws IOException{
        Event event = buildEvent(assignment, courseName);
        calendarService.events()
                       .patch(calendarId, eventId, event)
                       .execute();
        logger.info("Updated: {}: {}", courseName, assignment.getName());
    }

    /**
     * Builds a Google Calendar event from an assignment.
     *
     * The event summary is formatted as "CourseName: AssignmentName". The description
     * includes the assignment priority and LLM generated summary. Event start and end
     * times are set to one hour before the due date and the due date itself.
     *
     * A private extended property "folderId" is also attached to the event to
     * uniquely associate the calendar event with the Brightspace assignment folder.
     *
     * @param assignment the assignment being converted into a calendar event
     * @param courseName the course name associated with the assignment
     * @return a fully constructed event ready to be sent to the Google Calendar API
     */
    private Event buildEvent(Assignment assignment, String courseName){
        Event event = new Event();

        event.setSummary(courseName + ": " + assignment.getName());
        event.setDescription(
            "Priority: " + assignment.getPriority() + "/4\n\n" +
            "Summary: "  + assignment.getLlmSummary() + "\n\n");
        event.setColorId(daysUntilDueToColorId(assignment.getDaysUntilDue()));

        DateTime end   = new DateTime(assignment.getDueDate().toInstant().toEpochMilli());
        DateTime start = new DateTime(assignment.getDueDate().minusHours(1).toInstant().toEpochMilli());

        event.setStart(new EventDateTime().setDateTime(start));
        event.setEnd(new EventDateTime().setDateTime(end));

        Event.ExtendedProperties extendedProperties = new Event.ExtendedProperties();
        extendedProperties.setPrivate(Map.of("folderId", assignment.getFolderId()));
        event.setExtendedProperties(extendedProperties);

        return event;
    }

    /**
     * Fetches all Google Calendar events from the last 4 months.
     * Iterates the results and collects only the events with the 
     * private extended property "folderId".
     * 
     * @return map associating assignment folderIds with their Google Calendar event
     * @throws IOException if the Google Calendar API request fails
     */
    private Map<String, Event> fetchTrackedAssignmentEvents() throws IOException{
        DateTime minTime = new DateTime(OffsetDateTime.now().minusMonths(4).toInstant().toEpochMilli());

        Events events = calendarService.events().list(calendarId)
                                       .setTimeMin(minTime)
                                       .setFields("items(id,summary,description,start,end,colorId,extendedProperties)")
                                       .setMaxResults(2500)
                                       .execute();
                                       
        Map<String, Event> folderToEventMap = new HashMap<>();

        if(events.getItems() != null){
            for(Event event : events.getItems()){
                Event.ExtendedProperties properties = event.getExtendedProperties();
                if(properties != null && properties.getPrivate() != null){
                    String folderId = properties.getPrivate().get("folderId");
                    if(folderId != null){
                        folderToEventMap.put(folderId, event);
                    }
                }
            }
        }
        return folderToEventMap;
    }

    /**
     * Determines the Google Calendar event color ID based on the number of days until an assignment is due.
     * Possible colors: Graphite, Tomato, Tangerine, Banana, and Sage
     * 
     * @param daysUntilDue days remaining before assignment's due date
     * @return string representing the numeric color ID
     */
    private String daysUntilDueToColorId(long daysUntilDue){
        if (daysUntilDue < -5)  return "8";  // Graphite 
        if (daysUntilDue <= 2)  return "11"; // Tomato
        if (daysUntilDue <= 3)  return "6";  // Tangerine
        if (daysUntilDue <= 7)  return "5";  // Banana
        return "2";                          // Sage
    }

    private boolean isUpdateRequired(Event remote, Assignment assignment, String courseName){

        String expectedColorId = daysUntilDueToColorId(assignment.getDaysUntilDue());
        if (!Objects.equals(remote.getColorId(), expectedColorId)) return true;

        String expectedSummary = courseName + ": " + assignment.getName();
        if (!Objects.equals(remote.getSummary(), expectedSummary)) return true;

        String expectedDescription = "Priority: " + assignment.getPriority() + "/4\n\n" +
                                      "Summary: "  + assignment.getLlmSummary() + "\n\n";
        if (!Objects.equals(expectedDescription, remote.getDescription())) return true;

        if (remote.getStart().getDateTime() == null || remote.getEnd().getDateTime() == null) {
            logger.debug("Remote event '{}' is missing dateTime info. Forcing update.", assignment.getName());
            return true; 
        }                
        
        long remoteStart = remote.getStart().getDateTime().getValue();
        long remoteEnd = remote.getEnd().getDateTime().getValue();

        long expectedStart = assignment.getDueDate().minusHours(1).toInstant().toEpochMilli();
        long expectedEnd = assignment.getDueDate().toInstant().toEpochMilli();

        if (remoteStart != expectedStart || remoteEnd != expectedEnd) {
            return true;
        }
        return false;
    }
}