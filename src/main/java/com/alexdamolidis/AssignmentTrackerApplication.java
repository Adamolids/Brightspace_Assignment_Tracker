package com.alexdamolidis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.ai.DemoLlmDataSource;
import com.alexdamolidis.ai.GeminiLlmDataSource;
import com.alexdamolidis.ai.LlmDataSource;
import com.alexdamolidis.ai.LlmService;
import com.alexdamolidis.calendar.CalendarDataSource;
import com.alexdamolidis.calendar.DemoGoogleCalendarService;
import com.alexdamolidis.calendar.GoogleCalendarService;
import com.alexdamolidis.client.BrightspaceClient;
import com.alexdamolidis.client.BrightspaceDataSource;
import com.alexdamolidis.client.DemoBrightspaceClient;
import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.service.AssignmentService;
import com.alexdamolidis.util.Config;

public class AssignmentTrackerApplication {
	private static final Logger logger = LoggerFactory.getLogger(AssignmentTrackerApplication.class);

	public void start(){
		boolean demoMode = Boolean.parseBoolean(System.getProperty("DEMO_MODE", "false"));
		logger.info("Assignment Tracker sync started");
		
		BrightspaceDataSource sharedClient  = demoMode ? new DemoBrightspaceClient() : new BrightspaceClient();
		SqliteRepository      repo          = demoMode ? new SqliteRepository("jdbc:sqlite:tracker_demo.db") : new SqliteRepository();
		AssignmentService     service       = new AssignmentService(sharedClient , repo);
		LlmDataSource         llmDataSource = demoMode ? new DemoLlmDataSource() : new GeminiLlmDataSource();
		LlmService            llmService    = new LlmService(llmDataSource);
		CalendarDataSource    calendar      = demoMode ? new DemoGoogleCalendarService() : new GoogleCalendarService(repo);

		String semesterName = demoMode ? "DemoSemester" : Config.getRequired("SEMESTER_NAME");
		Semester semester = service.sync(semesterName);
		logger.info("Sync was successful");

		logger.info("Starting AI enrichment for {}...", semester.getName());
		llmService.enrichSemester(semester);

		logger.info("Sync and AI enrichment complete.");

		repo.saveSemester(semester);
		logger.debug("Semester saved to database");

		calendar.syncAssignments(semester.getCourses());
		logger.info("Synced assignments to Google Calendar.");
	}

	public static void main(String[] args) {
		try{
			new AssignmentTrackerApplication().start();

		}catch(TrackerException e){
			logger.error("Critical application error occurred during the sync process.", e);
			System.exit(1);

		}catch(RuntimeException e){
			logger.error("An unexpected system or logic error occurred.", e);
			System.exit(1);

		}catch(Throwable t){
			logger.error("Fatal: the application encountered an unrecoverable crash.", t);
			System.exit(1);
		}
	}
}