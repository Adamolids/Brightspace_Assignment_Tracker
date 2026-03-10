package com.alexdamolidis;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import com.alexdamolidis.ai.LlmService;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Course;
import com.alexdamolidis.model.Semester;
import com.alexdamolidis.service.AssignmentService;
import com.alexdamolidis.util.BrightspaceClient;

public class AssignmentTrackerApplication {
	// private static final Logger logger = LoggerFactory.getLogger(AssignmentTrackerApplication.class);
	public static void main(String[] args) {
		// logger.info("Assignment Tracker sync started");
		try{

			BrightspaceClient sharedClient = new BrightspaceClient();
			AssignmentService service      = new AssignmentService(sharedClient);
			LlmService 		  llmService   = new LlmService();

			Semester semester = service.sync();
			service.saveSemesterToFile(semester);
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

			service.saveSemesterToFile(semester);
			System.out.println("Sync and AI enrichment complete.");
			
		}catch(RuntimeException e){
			System.err.println("System Failed: " + e.getMessage());

			System.err.println("Stack trace: ");
			e.printStackTrace();

			// logger.error("A critical error occurred during the sync process: {}", e.getMessage(), e);
			System.exit(1);

		}
	}
}