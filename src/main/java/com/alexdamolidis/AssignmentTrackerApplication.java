package com.alexdamolidis;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import com.alexdamolidis.service.AssignmentService;
import com.alexdamolidis.util.BrightspaceClient;

public class AssignmentTrackerApplication {
	// private static final Logger logger = LoggerFactory.getLogger(AssignmentTrackerApplication.class);
	public static void main(String[] args) {
		// logger.info("Assignment Tracker sync started");
		try{

			BrightspaceClient sharedClient = new BrightspaceClient();
			AssignmentService service      = new AssignmentService(sharedClient);
			service.sync();
			// logger.info("Sync was successful");

		}catch(RuntimeException e){
			System.err.println("System Failed: " + e.getMessage());

			System.err.println("Stack trace: ");
			e.printStackTrace();

			// logger.error("A critical error occurred during the sync process: {}", e.getMessage(), e);
			System.exit(1);

		}
	}
}