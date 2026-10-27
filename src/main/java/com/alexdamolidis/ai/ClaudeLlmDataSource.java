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

    // Deadlines are deliberately left out: priority is calculated in code on every run from this
    // estimate and the time left, so the estimate only needs to describe the work itself.
    private static final String SYSTEM_INSTRUCTIONS = """
        You are an academic assistant helping a college student plan their workload.
        Summarize the assignment and estimate how much work it takes.

        1. Sources:
        - The assignment may include instructions, attached files, and related course content. Related course
          content was found in the course's Content section by matching the assignment's name, because professors
          often post instructions there instead of in the assignment folder.
        - Any of these can be missing. Related course content can also include supporting material (examples,
          sample data, video descriptions); use it to understand the assignment, not as extra deliverables.

        2. Summary:
        - Scan the text for specific audience restrictions (e.g. 'Only for transfer students', 'Requires work permit', 'International students only').
        - IF a restriction exists, it MUST be the very first sentence of the summary.
        - IF NO restriction exists, do NOT mention eligibility, start the summary directly with the assignment goals.
        - Describe what must be produced and submitted in 2 to 4 sentences.
        - IF the material is too thin to know what is required, say so plainly rather than inventing details.

        3. Effort estimate:
        - estimatedHours: total hours a typical student in this course needs to finish the assignment, including
          reading the instructions, research or studying, doing the work, and preparing the submission.
          Reference points: quiz or check in 0.5-1, discussion post or reflection 1-2, lab 2-5,
          report or case study 5-12, major project or final 12-30 or more. Group work adds coordination time.
          For a timed test or exam, estimate the study time needed to prepare for it.
        - difficulty (1-5): how demanding the work is for a student at this level, independent of length.
        - confidence: HIGH when detailed instructions or a rubric were provided, MEDIUM when they are partial,
          LOW when there is little beyond the assignment name and course. With LOW, base the estimate on what
          this kind of assignment usually involves in a course like this one.
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
