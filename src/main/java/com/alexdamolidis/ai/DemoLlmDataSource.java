package com.alexdamolidis.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DemoLlmDataSource implements LlmDataSource{

    @Override
    public String getRawApiResponse(String prompt){
        try{
            if(prompt.contains("Biomechanical Form Analysis")){
                return Files.readString(Path.of("src/main/resources/demo/courseTwoLlmResponse1.json"));

            }else if(prompt.contains("Macro Cycle Programming Logic")){
                return Files.readString(Path.of("src/main/resources/demo/courseTwoLlmResponse2.json"));

            }else if(prompt.contains("Hypertrophy vs. Strength Taxonomy Report")){
                return Files.readString(Path.of("src/main/resources/demo/courseTwoLlmResponse3.json"));

            }else if(prompt.contains("Fruit Survey")){
                return Files.readString(Path.of("src/main/resources/demo/courseThreeLlmResponse1.json"));

            }else if(prompt.contains("Fruit Observation Assignment")){
                return Files.readString(Path.of("src/main/resources/demo/courseThreeLlmResponse2.json"));

            }else if(prompt.contains("Comparative Fruit Taxonomy Report")){
                return Files.readString(Path.of("src/main/resources/demo/courseThreeLlmResponse3.json"));

            }

            throw new RuntimeException("No demo data found for assignment");

        }catch(IOException e){
            throw new RuntimeException("Failed to read demo LLM response file.", e);
        }
    }
}