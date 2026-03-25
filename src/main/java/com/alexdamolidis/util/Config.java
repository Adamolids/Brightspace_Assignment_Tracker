package com.alexdamolidis.util;

import java.io.FileInputStream;
import java.util.Properties;

import com.alexdamolidis.exception.TrackerConfigException;

public class Config {
    private static Properties properties = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(".env")) {            
            properties.load(fis);
        }catch(Exception e){
            throw new TrackerConfigException("Could not load .env file. Please ensure it exists in the root directory.", e);
        }
    }

    public static String get(String key){
        return properties.getProperty(key);
    }
    
    public static String getRequired(String key){
        String value = get(key);
        if(value == null || value.trim().isEmpty()){
            throw new TrackerConfigException("Missing required config property: " + key +
             "please check the readme and ensure your .env file is set up correctly.");
        }
        return value.trim();
    }
}