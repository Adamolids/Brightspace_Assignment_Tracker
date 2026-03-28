package com.alexdamolidis.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.alexdamolidis.util.Config;
import com.alexdamolidis.util.HttpValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeminiLlmDataSource implements LlmDataSource{

    private final String apiKey;
    private final ObjectMapper mapper;
    private final HttpClient client; 

    private static final String SYSTEM_INSTRUCTIONS = """
        You are an academic assistant. Analyze the assignment and return a JSON object.\n\n
        1. Eligibility and Restrictions: \n
        - Scan the text for specific audience restrictions (e.g., 'Only for transfer students', 'Requires work permit', 'International students only').\n
        - IF a restriction exists, it MUST be the very first sentence of the summary.\n
        - IF NO restriction exists, do NOT mention eligibility, start the summary directly with the assignment goals.\n
        - IF the due date is 5 or more days before today's date: \n
        - Priority MUST be 0 \n
        - Summary MUST clearly state the assignment can no longer be submitted. \n
        - First determine a complexity score (1–3), then determine priority.\n\n
        2. Complexity Signals (1-3): \n
        - High   (3): Keywords like 'Final', 'Implementation', 'Group Work', or very long instructions. \n
        - Medium (2): Keywords like 'Lab', 'Report', 'Documentation', 'Case Study'. \n
        - Low    (1): Keywords like 'Quiz', 'Discussion', 'Reflection', 'Check in', 'Contract'. \n\n
        3. Priority Logic (0-4, evaluate in order, first match wins): \n
        - 0: Days Until Due <= -5 \n
        - 4: Days Until Due <= 2 days \n
        - 3: Days Until Due <= 7 days OR Complexity == 3.\n
        - 2: Days Until Due <= 14 days OR Complexity == 2.\n
        - 1: Everything else. \n\n
        4. Output Format: \n
        Return ONLY: {\"priority\": int, \"reasoning\": string, \"llmSummary\": string} \n\n""";

    public GeminiLlmDataSource(String apiKey){
        this.apiKey = apiKey;
        this.mapper = new ObjectMapper();
        this.client = HttpClient.newHttpClient();
    }
    public GeminiLlmDataSource(){
        this(Config.getRequired("API_KEY"));
    }

    /**
     * Excecutes the POST request to the Gemini API, packaging the system instructions 
     * and user prompt into a valid JSON payload.
     * 
     * @param prompt The formatted assignment data String
     * @return The raw HTTP response from the Google server
     * @throws IOException If a netwrok error occurs during the request
     * @throws InterruptedException If the request thread is interrupted
     */
    private HttpResponse<String> postToGeminiApi(String prompt)throws IOException, InterruptedException{
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.set("system_instruction", mapper.createObjectNode()
                .set("parts", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("text", SYSTEM_INSTRUCTIONS))));

        rootNode.set("contents", mapper.createArrayNode()
                .add(mapper.createObjectNode()
                .put("role", "user")
                .set("parts", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("text", prompt)))));

        rootNode.set("generationConfig", mapper.createObjectNode()
                .put("responseMimeType", "application/json"));

        HttpRequest request = HttpRequest.newBuilder()
                                  .uri(URI.create(url))
                                  .header("Content-Type", "application/json")
                                  .POST(HttpRequest.BodyPublishers.ofString(rootNode.toString()))
                                  .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response;
    }

    public String getRawApiResponse(String prompt) throws IOException, InterruptedException{
        HttpResponse<String> response = postToGeminiApi(prompt);
        HttpValidator.validate(response, "Gemini AI");

        return response.body();
    }
}