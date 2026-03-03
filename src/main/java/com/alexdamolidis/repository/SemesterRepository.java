package com.alexdamolidis.repository;

import java.io.File;
import java.io.IOException;

import com.alexdamolidis.model.Semester;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class SemesterRepository {

    /**
     * Used to hydrate the application with data collected from the last session, 
     * allowing data verification and reducing redundant API calls to Brightspace.
     * 
     * @param path The file path to the persisted JSON data.
     * 
     * @return semester semester object hydrated with old data.
     * 
     * @throws RuntimeException if the file is missing (IOException) 
     * or if the JSON is malformed (JsonParseException / JsonMappingException).
     */
    public Semester loadCollectedData(String path) {
        ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .registerModule(new JavaTimeModule());

        File file = new File(path);

        try {
            return mapper.readValue(file, Semester.class);

        } catch (JsonParseException | JsonMappingException e) {
            throw new RuntimeException(
                    "Local data file may be corrupted. Try deleting " + path + " and running a full sync.", e);
        } catch (IOException e) {
            throw new RuntimeException("FileSystem error: Could not read " + path, e);
        }
    }
}