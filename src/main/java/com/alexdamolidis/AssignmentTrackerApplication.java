package com.alexdamolidis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.ai.LlmService;
import com.alexdamolidis.calendar.GoogleCalendarService;
import com.alexdamolidis.exception.TrackerException;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.service.AssignmentService;
import com.alexdamolidis.util.BrightspaceClient;
import com.alexdamolidis.util.Config;

// import tools.BrightspaceDevUtils;

public class AssignmentTrackerApplication {
	private static final Logger logger = LoggerFactory.getLogger(AssignmentTrackerApplication.class);

	public void start(){
		logger.info("Assignment Tracker sync started");
		BrightspaceClient     sharedClient = new BrightspaceClient();
		SqliteRepository      repo         = new SqliteRepository();
		AssignmentService     service      = new AssignmentService(sharedClient , repo);
		LlmService 		      llmService   = new LlmService();
		GoogleCalendarService calendar     = new GoogleCalendarService(repo);

		String semesterName = Config.getRequired("SEMESTER_NAME");
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