package com.alexdamolidis.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.model.Attachment;
import com.alexdamolidis.util.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LlmService {
    private final HttpClient client = HttpClient.newHttpClient();   
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey = Config.get("API_KEY");

    private final String SYSTEM_INSTRUCTIONS = 
        "You are an academic assistant. Analyze the assignment and return a JSON object.\n\n" +
        "1. Eligibility and Restrictions: \n" +
        "- Scan the text for specific audience restrictions (e.g., 'Only for transfer students', 'Requires work permit', 'International students only').\n" +
        "- IF a restriction exists, it MUST be the very first sentence of the summary.\n" +
        "- IF NO restriction exists, do NOT mention eligibility, start the summary directly with the assignment goals.\n" +   
        "- IF the due date is 5 or more days before today's date: \n" +
        "- Priority MUST be 0 \n" +
        "- Summary MUST clearly state the assignment can no longer be submitted. \n" +
        "- First determine a complexity score (1–3), then determine priority.\n\n"  +   
        "2. Complexity Signals (1-3): \n" + 
        "- High   (3): Keywords like 'Final', 'Implementation', 'Group Work', or very long instructions. \n" +
        "- Medium (2): Keywords like 'Lab', 'Report', 'Documentation', 'Case Study. \n' " +
        "- Low    (1): Keywords like 'Quiz', 'Discussion', 'Reflection', 'Check in', 'Contract'. \n\n" +
        "3. Priority Logic (0-4, evaluate in order, first match wins): \n" +
        "- 0: Days Until Due <= -5 \n" +
        "- 4: Days Until Due <= 2 days \n" +
        "- 3: Days Until Due <= 7 days OR Complexity == 3.\n" +
        "- 2: Days Until Due <= 14 days OR Complexity == 2.\n" + 
        "- 1: Everything else. \n\n" +
        "4. Output Format: \n" +
        "Return ONLY: {\"priority\": int, \"reasoning\": string, \"llmSummary\": string} \n\n";


    /**
     * Constructs a formatted text prompt containing the assignment details and attachments.
     * @param assignment assignment The assignment object containing raw data from Brightspace.
     * @return A formatted string ready to be sent to the LLM.
     */
    public String buildPrompt(Assignment assignment){
        StringBuilder attachmentsBuilder = new StringBuilder();

        if(assignment.getAttachments() != null){
            for(Attachment attachment : assignment.getAttachments()){
                attachmentsBuilder.append("\n Attachment Name: ")
                                  .append(attachment.getFileName())
                                  .append("\n")
                                  .append(attachment.getAttachmentText())
                                  .append("\n");
            }
        }
        return String.format(
            "Days Until Due: %s \n" +
            "Assignment Name: %s \n" +
            "Instructions: %s \n" +
            "Attachment Data: \n %s",
            assignment.getDaysUntilDue(),
            assignment.getName(),
            assignment.getInstructionText(),
          (!attachmentsBuilder.toString().isEmpty() ? attachmentsBuilder.toString() : "No Attachments")
        );
    }

    /**
     * Excecutes the POST request to the Gemini API, packaging the system instructions 
     * and user prompt into a valid JSON payload
     * @param prompt The formatted assignment data String.
     * @return The raw HTTP response from the Google server.
     * @throws IOException If a netwrok error occurs during the request.
     * @throws InterruptedException If the request thread is interrupted.
     */
    public HttpResponse<String> postToGeminiApi(String prompt)throws IOException, InterruptedException{
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

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Manages the synchronization of AI generated metadata into an assignment object.
     * Implements a fail soft retry loop to handle rate limits and transient network issues.
     * @param assignment The target assignment to be enriched with AI data.
     */
    public void populateAiFields(Assignment assignment){
        if(assignment.getLlmSummary() != null && !assignment.getLlmSummary().isEmpty()) return;
       
        try{
            int retryCount = 0;
            boolean success = false;
            String prompt = buildPrompt(assignment);

            while(!success && retryCount < 3){
                try{
                    HttpResponse<String> response = postToGeminiApi(prompt);

                    if(response.statusCode() == 200){
                        syncAiResponseToModel(response.body(), assignment);
                        success = true;

                    }else if(response.statusCode() == 429){
                        retryCount++;
                        System.out.println("Got status code 429, retrying in: " + 2 * retryCount + "seconds");
                        Thread.sleep(2000 * retryCount);

                    }else{
                        throw new RuntimeException("Gemini 1.5 API returned error status: " + response.statusCode());
                    }
                }catch(IOException e){
                    retryCount++;
                    if(retryCount >= 3){
                        System.out.println("Skipping '" + assignment.getName() + "' due to API error: " + e.getMessage());
                        break;
                    }
                }catch(RuntimeException e){
                    System.out.println("Skipping '" + assignment.getName() + "' due to API error: " + e.getMessage());
                    break;
                }
            }
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI Thread was interrupted. Stopping all processing.", e);
        }
    }

    /**
     * Parses the nested JSON response from the LLM and maps the generated priority,
     * reasoning, and summary into the local assignment model.
     * @param jsonResponse The raw JSON String returned by the API.
     * @param assignment The assignment object to be updated.
     */
    public void syncAiResponseToModel(String jsonResponse, Assignment assignment){
        try{
            JsonNode root = mapper.readTree(jsonResponse);

            JsonNode parts = root.path("candidates").path(0).path("content").path("parts").path(0);
            String contentText = parts.path("text").asText();

            if(contentText.isEmpty()){
                throw new RuntimeException("Gemini returned an empty response body.");
            }

            JsonNode aiResult = mapper.readTree(contentText);

            assignment.setPriority(aiResult.path("priority").asInt(0));
            assignment.setReasoning(aiResult.path("reasoning").asText("No reasoning provided."));
            assignment.setLlmSummary(aiResult.path("llmSummary").asText("No summary generated."));
        
        }catch(JsonProcessingException e){
            System.err.println("AI sent malformed JSON for: " + assignment.getName());
            System.err.println("Error details: " + e.getMessage());
        }
    }
}