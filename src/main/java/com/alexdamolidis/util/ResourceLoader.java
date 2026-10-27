package com.alexdamolidis.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ResourceLoader {

    private ResourceLoader(){}

    /**
     * Reads a bundled resource from the classpath, so it resolves the same way whether the
     * application is run from the project root, another directory, or a packaged jar.
     *
     * @param path resource path relative to src/main/resources, e.g. "demo/enrollments.json"
     * @return the resource contents as an array of bytes
     * @throws IOException if the resource is missing or cannot be read
     */
    public static byte[] readBytes(String path) throws IOException{
        try(InputStream stream = ResourceLoader.class.getClassLoader().getResourceAsStream(path)){
            if(stream == null){
                throw new IOException("Resource not found on classpath: " + path);
            }
            return stream.readAllBytes();
        }
    }

    /**
     * Reads a bundled text resource from the classpath as UTF-8.
     *
     * @param path resource path relative to src/main/resources, e.g. "demo/enrollments.json"
     * @return the resource contents as a String
     * @throws IOException if the resource is missing or cannot be read
     */
    public static String readString(String path) throws IOException{
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }
}
