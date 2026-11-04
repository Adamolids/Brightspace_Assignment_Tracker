package com.alexdamolidis.client;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import com.alexdamolidis.util.ResourceLoader;

public class DemoBrightspaceClient implements BrightspaceDataSource{
    
    // Folder id of the demo assignment shown as already submitted ("Fruit Survey").
    private static final String SUBMITTED_FOLDER_ID = "1053177";

    private static final Pattern RELATIVE_DATE = Pattern.compile("\\{now([+-]\\d+)h\\}");

    @Override
    public String sendGetRequest(String url){
        try{
            // Submission, Content, quiz, and grade URLs also contain the course id, so they are matched before the course checks below.
            if (url.contains("mysubmissions")){
                return url.contains("/" + SUBMITTED_FOLDER_ID + "/") ? ResourceLoader.readString("demo/mysubmissions_submitted.json") : "[]";

            }else if(url.contains("content/toc")){
                if (url.contains("323431")) return ResourceLoader.readString("demo/content_toc_C2.json");
                if (url.contains("987654")) return ResourceLoader.readString("demo/content_toc_C3.json");
                return "{\"Modules\": []}";

            }else if(url.contains("/quizzes/")){
                return url.contains("987654") ? injectRelativeDates(ResourceLoader.readString("demo/quizzes_C3.json"))
                                              : "{\"Objects\": [], \"Next\": null}";

            }else if(url.contains("myGradeValues")){
                return url.contains("987654") ? ResourceLoader.readString("demo/grades_C3.json") : "[]";

            }else if (url.contains("myenrollments")){
                return ResourceLoader.readString("demo/enrollments.json");

            } else if(url.contains("323431")){
                return injectDueDates(ResourceLoader.readString("demo/assignments_C2.json"), List.of(-10, 1, 3));

            }else if(url.contains("987654")){
                return injectDueDates(ResourceLoader.readString("demo/assignments_C3.json"), List.of(6, 12, 25));

            }else if(url.contains("654321")){
                return ResourceLoader.readString("demo/assignments_C1.json");
            }
            
            throw new RuntimeException("No demo data found for URL: " + url);
        
        }catch(IOException e){
            throw new RuntimeException("IOException encountered: ", e);
        }
    }

    //Mocks attachment and Content file downloads: the Comparative Fruit Taxonomy Report attachment, and the
    //two Content section files matched to the Hypertrophy report and the Fruit Observation Assignment by name
    @Override
    public byte[] downloadAttachment(String url){
        try{
            if(url.contains("/content/topics/7002/")){
                return ResourceLoader.readBytes("demo/content_taxonomy_report_instructions.html");
            }
            if(url.contains("/content/topics/8001/")){
                return ResourceLoader.readBytes("demo/content_fruit_spec_sheet_template.html");
            }
            if(url.contains("12345678")){
                return ResourceLoader.readBytes("demo/Comparative_Fruit_Taxonomy.pdf");
            }
        }catch(IOException e){
            throw new RuntimeException("Error occurred trying to extract bytes from path: " + url, e);
        }

        throw new RuntimeException("Unexpected logic error occurred trying to fetch demo attachment.");
    }

    // Replaces placeholders like "{now+72h}" with a date that many hours from now, on the hour.
    private String injectRelativeDates(String json) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        return RELATIVE_DATE.matcher(json).replaceAll(match -> now.plusHours(Long.parseLong(match.group(1))).toString());
    }

    private String injectDueDates(String json, List<Integer> daysOffsets) {
        for (int i = 0; i < daysOffsets.size(); i++) {
            String date = OffsetDateTime.now().plusDays(daysOffsets.get(i)).toString();
            json = json.replaceFirst("\"DueDate\": null", "\"DueDate\": \"" + date + "\"");
        }
        return json;
    }
}