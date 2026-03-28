package com.alexdamolidis.ai;

import java.io.IOException;

public interface LlmDataSource {

    public String getRawApiResponse(String prompt) throws IOException, InterruptedException;
}
