package com.alexdamolidis.util;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import com.alexdamolidis.exception.AttachmentProcessingException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;

public class ContentExtractor {
    private static final ContentExtractor instance = new ContentExtractor();
    private final Tika tika;

    private ContentExtractor(){
        this.tika = new Tika();
    }

    public static ContentExtractor getInstance(){
        return instance;
    }

    /**
     * utilizes tika to transform raw byte array into a string.
     *
     * @param rawData Array of bytes 
     * @return String representation of the extracted text
     * @throws AttachmentProcessingException if tika fails to parse the byte array
     */
    public String extractTextFromBytes(byte[] rawData){
        if(rawData == null || rawData.length == 0){
            return "";
        }

        try(ByteArrayInputStream bis = new ByteArrayInputStream(rawData)){
            return tika.parseToString(bis).trim();
        }
        catch(TikaException | IOException e){
            throw new AttachmentProcessingException("Extraction error: ", e);
        }
    }

    /**
     * extracts the first line from a file.
     *
     * @param cookieTxtPath  path to locally stored cookies
     * @return first line from txt file as a String
     * @throws AttachmentProcessingException if there is an issue reading the file or the file is empty
     */
    public static String readFirstLine(String cookieTxtPath){
        try (BufferedReader reader = new BufferedReader(new FileReader(cookieTxtPath))) {
            return reader.readLine();
        }
        catch(IOException e){
            throw new AttachmentProcessingException("Failed to read first line from file: " + cookieTxtPath, e);
        }
    }
}
