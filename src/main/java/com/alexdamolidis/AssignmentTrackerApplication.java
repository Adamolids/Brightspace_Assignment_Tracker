package com.alexdamolidis;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import com.alexdamolidis.ai.LlmService;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.repository.SqliteRepository;
import com.alexdamolidis.service.AssignmentService;
import com.alexdamolidis.util.BrightspaceClient;

public class AssignmentTrackerApplication {
	// private static final Logger logger = LoggerFactory.getLogger(AssignmentTrackerApplication.class);

	public void start() throws RuntimeException{
		// logger.info("Assignment Tracker sync started");
		BrightspaceClient sharedClient = new BrightspaceClient();
		SqliteRepository  repo         = new SqliteRepository();
		AssignmentService service      = new AssignmentService(sharedClient , repo);
		LlmService 		  llmService   = new LlmService();

		Semester semester = service.sync();
		// logger.info("Sync was successful");
		System.out.println("Starting AI enrichment for " + semester.getName() +"...");

		int totalAssignments = service.countAssignmentsWorthCredits(semester);
		int currentCount     = 0;
		System.out.println("Generating priorities, reasoning, and summaries for " + totalAssignments + " assignments...");

		for (Course course : semester.getCourses()) {
            if (course.getIsWorthCredits() && course.getAssignments() != null) {
                for (Assignment assignment : course.getAssignments()) {
					currentCount++;
					System.out.print(String.format("[%d/%d] %-40s", currentCount, totalAssignments, assignment.getName()));
                    llmService.populateAiFields(assignment);
					System.out.println("- [Synced]");
                }
            }
        }
		repo.saveSemester(semester);
		System.out.println("Sync and AI enrichment complete.");
	}

	public static void main(String[] args) {
		try{
			new AssignmentTrackerApplication().start();

		}catch(RuntimeException e){
			System.err.println("System Failed: " + e.getMessage());

			System.err.println("Stack trace: ");
			e.printStackTrace();

			// logger.error("A critical error occurred during the sync process: {}", e.getMessage(), e);
			System.exit(1);
		}
	}
}