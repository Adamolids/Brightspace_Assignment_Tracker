package com.alexdamolidis.ai;

import com.alexdamolidis.exception.RateLimitException;
import com.alexdamolidis.exception.TrackerApiException;
import com.alexdamolidis.util.Config;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;

public class ClaudeLlmDataSource implements LlmDataSource{

    private static final String MODEL      = "claude-opus-5";
    private static final long   MAX_TOKENS = 16000L;

    private final AnthropicClient client;

    private static final String SYSTEM_INSTRUCTIONS = """
        You are an academic assistant. Analyze the assignment and score it.

        1. Eligibility and Restrictions:
        - Scan the text for specific audience restrictions (e.g. 'Only for transfer students', 'Requires work permit', 'International students only').
        - IF a restriction exists, it MUST be the very first sentence of the summary.
        - IF NO restriction exists, do NOT mention eligibility, start the summary directly with the assignment goals.
        - IF the due date is 5 or more days before today's date:
          - Priority MUST be 0
          - Summary MUST clearly state the assignment can no longer be submitted.
        - First determine a complexity score (1-3), then determine priority.

        2. Complexity Signals (1-3):
        - High   (3): Keywords like 'Final', 'Implementation', 'Group Work', or very long instructions.
        - Medium (2): Keywords like 'Lab', 'Report', 'Documentation', 'Case Study'.
        - Low    (1): Keywords like 'Quiz', 'Discussion', 'Reflection', 'Check in', 'Contract'.

        3. Priority Logic (0-4, evaluate in order, first match wins):
        - 0: Days Until Due <= -5
        - 4: Days Until Due <= 2 days
        - 3: Days Until Due <= 7 days OR Complexity == 3.
        - 2: Days Until Due <= 14 days OR Complexity == 2.
        - 1: Everything else.
        """;

    public ClaudeLlmDataSource(String apiKey){
        // maxRetries(0) leaves RetryUtility as the single owner of backoff, matching how
        // the previous raw HttpClient implementation behaved.
        this.client = AnthropicOkHttpClient.builder()
                          .apiKey(apiKey)
                          .maxRetries(0)
                          .build();
    }

    public ClaudeLlmDataSource(){
        this(Config.getRequired("ANTHROPIC_API_KEY"));
    }

    /**
     * Sends the assignment prompt to Claude and returns the schema validated enrichment.
     *
     * @param prompt The formatted assignment data String
     * @return The parsed enrichment, or null if the response carried no content
     * @throws RateLimitException  if the API is rate limited or overloaded, so RetryUtility backs off
     * @throws TrackerApiException if the request fails for any non transient reason
     */
    @Override
    public Enrichment enrich(String prompt){
        StructuredMessageCreateParams<Enrichment> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_INSTRUCTIONS)
                .outputConfig(Enrichment.class)
                .addUserMessage(prompt)
                .build();

        try{
            return client.messages().create(params).content().stream()
                       .flatMap(block -> block.text().stream())
                       .map(block -> block.text())
                       .findFirst()
                       .orElse(null);

        }catch(com.anthropic.errors.RateLimitException e){
            throw new RateLimitException("Claude AI Error: 429 Rate Limit. Slowing down requests.", e);

        }catch(AnthropicServiceException e){
            if(isOverloaded(e)){
                throw new RateLimitException("Claude AI Error: service overloaded. Slowing down requests.", e);
            }
            throw new TrackerApiException("Claude AI Error: " + e.getMessage(), e);
        }
    }

    /**
     * A 529 overloaded response is transient, so it is surfaced as a RateLimitException
     * to reuse the existing exponential backoff rather than failing the whole sync.
     */
    private boolean isOverloaded(AnthropicServiceException e){
        return e.errorType()
                .map(type -> type.toString().contains("OVERLOADED"))
                .orElse(false);
    }
}
