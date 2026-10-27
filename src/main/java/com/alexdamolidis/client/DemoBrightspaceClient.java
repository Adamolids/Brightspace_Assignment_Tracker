package com.alexdamolidis.client;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import com.alexdamolidis.util.ResourceLoader;

public class DemoBrightspaceClient implements BrightspaceDataSource{
    
    @Override
    public String sendGetRequest(String url){
        try{
            if (url.contains("myenrollments")){
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

    //Mocks a attachment api call, should only be called for the assignment Comparative Fruit Taxonomy Report
    @Override
    public byte[] downloadAttachment(String url){
        try{
            if(url.contains("12345678")){
                return ResourceLoader.readBytes("demo/Comparative_Fruit_Taxonomy.pdf");
            }
        }catch(IOException e){
            throw new RuntimeException("Error occurred trying to extract bytes from path: " + url, e);
        }

        throw new RuntimeException("Unexpected logic error occurred trying to fetch demo attachment.");
    }

    private String injectDueDates(String json, List<Integer> daysOffsets) {
        for (int i = 0; i < daysOffsets.size(); i++) {
            String date = OffsetDateTime.now().plusDays(daysOffsets.get(i)).toString();
            json = json.replaceFirst("\"DueDate\": null", "\"DueDate\": \"" + date + "\"");
        }
        return json;
    }
}