package tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.alexdamolidis.util.BrightspaceClient;
import com.alexdamolidis.util.ContentExtractor;
import com.alexdamolidis.util.EndpointBuilder;

public class BrightspaceDevUtils {

    private BrightspaceClient client;

    public BrightspaceDevUtils(){
        client = new BrightspaceClient();
    }
    
	/**
	 * This method is to obtain a JSON of all courses from Brightspace
	 * and save them to a json file in the resources/demo directory.
	 * <br>
	 * Valid cookies in the cookies.txt file are required.
	 *
	 */
	public void getCoursesJsonForSample()throws IOException{
		String courses = client.sendGetRequest(EndpointBuilder.buildMyEnrollmentsUrl());

		Files.writeString(Path.of("src/main/resources/demo/enrollmentsTest.json"), courses);
	}

	/**
	 * This method is to obtain a JSON of a single course's assignments
	 * and save them to a json file in the resources/demo directory.
	 * <br/>
	 * Requires valid cookies.
	 * <br>
	 * Before running this method, run {@link #getCoursesJsonForSample()}.
	 * Open the generated file found at {@code src/main/resources/demo/enrollmentsTest.json.json},
	 * find the course you want, and copy its orgUnitId.
	 * <br>
	 * <pre>
	 * {"Items": [
	 * 		{"OrgUnit": 
	 * 			{"Id": 654321, <-- copy this value
	 * 			 ...
	 * 			}
	 * 		}
	 * ]}
	 * </pre>
	*/
	public void getAssignmentsJsonForSample(String orgUnitId) throws IOException, InterruptedException {
		String url = EndpointBuilder.buildAllAssignmentsUrl(orgUnitId);

		String assignment = client.sendGetRequest(url);

		Files.writeString(Path.of("src/main/resources/demo/assignments_C1_Test.json"), assignment);
	}


	/**
	 * This method is to obtain a .txt file of a single assignment's attachment test.
	 * the text is extracted from the file received from Brightspace, cleaned, and 
	 * sent to a new .txt file in the resources/demo directory.
	 * <br>
	 * Requires valid cookies. Please note there is not safeguard for file size,
	 * if you download an attachment that is too large, it could crash your environment.
	 * <br>
	 * {@link EndpointBuilder#buildAttachmentUrl()} requires an orgUnitId(Courses Id),
	 * a folderId (Assignment Id), and a fileId(Attachment Id found in Assignment JSON)
	 * 
	 * <pre>
	 * {
	 *	"Id": 1234567 <-- Assignment Id
	 *	...
	 *        "Attachments": [
	 *            {
	 *
	 *                "FileId": 88877766, <-- Attachment Id
	 *                "FileName": "example.pdf",
	 *                "Size": 11000
	 *            }
	 * 		  ]
	 *	...
	 *}
	 * </pre>
	 */
	public void getAttachmentForSample(String orgUnitId, String folderId, String fileId) throws IOException, InterruptedException{
		String url = EndpointBuilder.buildAttachmentUrl(orgUnitId, folderId, fileId);
		byte[] attachment = client.downloadAttachment(url);

		ContentExtractor tika = ContentExtractor.getInstance();
		String attachmentText = tika.extractTextFromBytes(attachment);

		Files.writeString(Path.of("src/main/resources/demo/attachment_Test.txt"), attachmentText);
	}   
}