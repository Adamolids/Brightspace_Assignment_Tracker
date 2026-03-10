package com.alexdamolidis.util;

import java.io.FileInputStream;
import java.util.Properties;

public class Config {
    private static Properties properties = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(".env")) {            
            properties.load(fis);
        }catch(Exception e){
            throw new RuntimeException("Could not load .env file. Please ensure it exists in the root directory.");
        }
    }

    public static String get(String key){
        return properties.getProperty(key);
    }
    
}
