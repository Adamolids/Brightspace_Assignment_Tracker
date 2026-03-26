package tools;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.ai.LlmService;
import com.alexdamolidis.model.Assignment;
import com.alexdamolidis.parser.StringParser;
import com.alexdamolidis.util.HttpValidator;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class GeminiDevUtils {

    public static final Logger logger = LoggerFactory.getLogger(GeminiDevUtils.class);
    private ObjectMapper mapper;
    private LlmService geminiService;

    public GeminiDevUtils(){
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.geminiService = new LlmService();
    }
    /**
     * days : -10, 1, 3, 6, 12, 25
     * 
     * For course 2
     * List<Integer> days = List.of(-10, 1, 3);
     * 
     * For course 3
     * List<Integer> days = List.of(6, 12, 25);
     */
    private List<Assignment> fetchCompleteAssignmentsFromJSON() throws StreamReadException, DatabindException, IOException{
        List<Assignment> assignments = mapper.readValue(new File("src/main/resources/demo/assignments_C3.json"), new TypeReference<List<Assignment>>() {});

        for(Assignment assignment : assignments ){
            String cleanText = StringParser.cleanHtml(assignment.getInstructionText());
            assignment.setInstructionText(cleanText);
        } 
        
        return assignments;
    }

    private List<Assignment> setDemoDueDatesFromEnrichment() throws StreamReadException, DatabindException, IOException{
        List<Assignment> assignments = fetchCompleteAssignmentsFromJSON();
        
        List<OffsetDateTime> dueDates = getMockDueDates(List.of(6, 12, 25));
        
        int count = 0;
        for(Assignment assignment : assignments ){
            assignment.setDueDate(dueDates.get(count));
            count++;
        } 

        return assignments;
    }

    public void enrichAssignmentsFromLocalJson() throws IOException, InterruptedException{
        int count = 1;
        List<Assignment> assignments = setDemoDueDatesFromEnrichment();

        for (Assignment assignment : assignments ){
            logger.warn("Enriching assignment: " + count);
            String prompt = geminiService.buildPrompt(assignment);

            HttpResponse<String> result = geminiService.getRawApiResponse(prompt);

            HttpValidator.validate(result, "Gemini API");

            Files.writeString(Path.of("src/main/resources/demo/courseThreeLlmResponse" + count +".json"), result.body());
            count++;
        }
    }       



    public List<OffsetDateTime> getMockDueDates(List<Integer> days){
        List<OffsetDateTime> dayTime = new ArrayList<>();

        for (int i = 0 ; i < days.size() ; i++){
            OffsetDateTime time = OffsetDateTime.now().plusDays(days.get(i));
            dayTime.add(time);
        }
        return dayTime;
    }

}