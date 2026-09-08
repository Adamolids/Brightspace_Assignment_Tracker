package com.alexdamolidis.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DemoLlmDataSource implements LlmDataSource{

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Enrichment enrich(String prompt){
        String fileName;

        if(prompt.contains("Biomechanical Form Analysis")){
            fileName = "courseTwoLlmResponse1.json";

        }else if(prompt.contains("Macro Cycle Programming Logic")){
            fileName = "courseTwoLlmResponse2.json";

        }else if(prompt.contains("Hypertrophy vs. Strength Taxonomy Report")){
            fileName = "courseTwoLlmResponse3.json";

        }else if(prompt.contains("Fruit Survey")){
            fileName = "courseThreeLlmResponse1.json";

        }else if(prompt.contains("Fruit Observation Assignment")){
            fileName = "courseThreeLlmResponse2.json";

        }else if(prompt.contains("Comparative Fruit Taxonomy Report")){
            fileName = "courseThreeLlmResponse3.json";

        }else{
            throw new RuntimeException("No demo data found for assignment");
        }

        try{
            return mapper.readValue(Files.readString(Path.of("src/main/resources/demo/" + fileName)), Enrichment.class);

        }catch(IOException e){
            throw new RuntimeException("Failed to read demo LLM response file.", e);
        }
    }
}
