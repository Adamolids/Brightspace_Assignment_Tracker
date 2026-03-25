package com.alexdamolidis.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.repository.SqliteRepository;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock private SqliteRepository repo;
    @Mock private Calendar calendarService;
    @Mock private Calendar.Events eventsService;
    @Mock private Calendar.Events.List listRequest;
    @Mock private Calendar.Events.Patch patchRequest;
    @Mock private Calendar.Events.Insert insertRequest;

    private GoogleCalendarService service;
    private final String CALENDAR_ID = "primary";

    @BeforeEach
    void setUp() throws IOException {
        when(calendarService.events()).thenReturn(eventsService);
        service = new GoogleCalendarService(repo, calendarService, CALENDAR_ID);
    }

    /**
     * Verifies the self healing logic. If the local database is lost but the event 
     * exists on Google Calendar, the system should reconcile the folderId, 
     * link the existing event, and perform an update.
     *
     */
    @Test
    void syncAssignments_shouldRecoverMissingEventIdFromGoogle() throws IOException {

        List<Course> courses = createTestSemester();
        Assignment assignment = courses.get(0).getAssignments().get(0);
        assignment.setCalendarEventId(null); 
        String folderId = assignment.getFolderId(); 
        
        Event remoteEvent = new Event()
                .setId("recovered_id_123")
                .setSummary("AssignmentOne")
                .setExtendedProperties(new Event.ExtendedProperties()
                        .setPrivate(Map.of("folderId", folderId)));
    
        mockListResponse(List.of(remoteEvent));
        when(eventsService.patch(anyString(), eq("recovered_id_123"), any())).thenReturn(patchRequest);
    
        service.syncAssignments(courses);
    
        verify(eventsService).patch(eq(CALENDAR_ID), eq("recovered_id_123"), any());
        verify(eventsService, never()).insert(anyString(), any());
        assertEquals("recovered_id_123", assignment.getCalendarEventId());
        verify(repo).saveCalendarEventIds(any());
    }

    /**
     * Verifies error resilience. If an event is tracked in the database but 
     * results in a 404 during an update attempt, the system should gracefully 
     * recover by inserting a fresh event.
     * 
     */
    @Test
    void syncAssignments_shouldReinsertIfUpdateReturns404() throws IOException {

        List<Course> courses = createTestSemester();
        Assignment assignment = courses.get(0).getAssignments().get(0);
        String deadId = assignment.getCalendarEventId();
        String folderId = "102030";

        Event existingEvent = new Event()
                .setId(deadId)
                .setExtendedProperties(new Event.ExtendedProperties()
                .setPrivate(Collections.singletonMap("folderId", folderId)));
        
        mockListResponse(Collections.singletonList(existingEvent));
        
        when(eventsService.patch(anyString(), eq(deadId), any())).thenReturn(patchRequest);

        GoogleJsonResponseException exception404 = mock(GoogleJsonResponseException.class);
        when(exception404.getStatusCode()).thenReturn(404);
        when(patchRequest.execute()).thenThrow(exception404);

        when(eventsService.insert(anyString(), any())).thenReturn(insertRequest);
        when(insertRequest.execute()).thenReturn(new Event().setId("new_id_789"));

        service.syncAssignments(courses);

        verify(eventsService).patch(anyString(), eq(deadId), any());
        verify(eventsService).insert(eq(CALENDAR_ID), any());
        assertEquals("new_id_789", assignment.getCalendarEventId());
    }

    /**
     * Verifies tombstoning logic for orphaned data. Events found on the 
     * calendar that are no longer present in Brightspace should be marked 
     * with "[OLD]" to inform the user of their removal.
     *
     */
    @Test
    void syncAssignments_shouldTombstoneOrphanedEvents() throws IOException {

        List<Course> emptyCourses = new ArrayList<>();

        Event orphan = new Event()
                .setId("orphan123")
                .setSummary("super old assignment")
                .setExtendedProperties(new Event.ExtendedProperties()
                        .setPrivate(Map.of("folderId", "ext_999")));

        mockListResponse(List.of(orphan));
        when(eventsService.patch(anyString(), eq("orphan123"), any())).thenReturn(patchRequest);

        service.syncAssignments(emptyCourses);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventsService).patch(eq(CALENDAR_ID), eq("orphan123"), eventCaptor.capture());
        
        assertTrue(eventCaptor.getValue().getSummary().startsWith("[OLD]"));
    }

    /**
     * Verifies the first run flow. For a brand new assignment with 
     * no prior tracking in the database or calendar, the system should 
     * create a new event and persist the generated ID.
     * 
     */
    @Test
    void syncAssignments_shouldInsertBrandNewAssignment() throws IOException {

        List<Course> courses = createTestSemester();
        Assignment assignment = courses.get(0).getAssignments().get(0);
        assignment.setCalendarEventId(null); 

        mockListResponse(Collections.emptyList());

        when(eventsService.insert(eq(CALENDAR_ID), any())).thenReturn(insertRequest);
        when(insertRequest.execute()).thenReturn(new Event().setId("new_google_id_999"));

        service.syncAssignments(courses);

        verify(eventsService).insert(eq(CALENDAR_ID), any());
        verify(eventsService, never()).patch(anyString(), anyString(), any());
        assertEquals("new_google_id_999", assignment.getCalendarEventId());
        verify(repo).saveCalendarEventIds(any());
    }

    /**
     * Verifies system recovery when a user manually deletes a tracked event.
     * The system should detect the absence from Google and recreate the 
     * event to maintain calendar sync.
     * 
     */
    @Test
    void syncAssignments_shouldRecreateEventIfManuallyDeleted() throws IOException {
        List<Course> courses = createTestSemester();
        Assignment assignment = courses.get(0).getAssignments().get(0);

        mockListResponse(Collections.emptyList());

        when(eventsService.insert(eq(CALENDAR_ID), any())).thenReturn(insertRequest);
        when(insertRequest.execute()).thenReturn(new Event().setId("recreated_id_000"));

        service.syncAssignments(courses);

        verify(eventsService).list(eq(CALENDAR_ID));
        verify(eventsService).insert(eq(CALENDAR_ID), any());

        assertEquals("recreated_id_000", assignment.getCalendarEventId());
    }

    // --- Helpers ---

    private void mockListResponse(List<Event> items) throws IOException {
        when(eventsService.list(anyString())).thenReturn(listRequest);
        when(listRequest.setTimeMin(any())).thenReturn(listRequest);
        when(listRequest.setFields(anyString())).thenReturn(listRequest);
        when(listRequest.setMaxResults(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(new Events().setItems(items));
    }

    //list of fake courses for test purposes
    public List<Course> createTestSemester(){
        List<Course> courses = new ArrayList<>();
        Course course = new Course();

        course.setName("courseOne");
        course.setOrgUnitId("12345");
        course.setIsWorthCredits(true);
        courses.add(course);

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

        return courses;
    }    
}