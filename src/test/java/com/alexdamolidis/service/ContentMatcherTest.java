package com.alexdamolidis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.alexdamolidis.model.ContentToc;
import com.alexdamolidis.model.ContentToc.ContentModule;
import com.alexdamolidis.model.ContentToc.ContentTopic;

/**
 * The titles used here are modelled on real course layouts: modules named after the assignment,
 * files named after it in a weekly module, and inconsistent spellings of the same name.
 */
public class ContentMatcherTest {

    private static long nextId = 1;

    private static ContentTopic file(String title, String fileName){
        return new ContentTopic(nextId++, title, "File", "/content/enforced/123/" + fileName, false, false, "2026-09-02T15:00:00.000Z");
    }

    private static ContentTopic video(String title){
        return new ContentTopic(nextId++, title, "ContentService", "/d2l/le/contentservice/" + nextId, false, false, "2026-09-02T15:00:00.000Z");
    }

    private static ContentModule module(String title, ContentTopic... topics){
        return new ContentModule(nextId++, title, List.of(), List.of(topics));
    }

    private static List<String> titles(List<ContentTopic> topics){
        return topics.stream().map(ContentTopic::title).toList();
    }

    @Test
    public void normalizeSplitsCamelCaseNumbersAndPunctuation(){
        assertEquals("help for assignment 3", ContentMatcher.normalize("HelpForAssignment3"));
        assertEquals("assignment 1", ContentMatcher.normalize("Assignment#1"));
        assertEquals("a 2 price list", ContentMatcher.normalize("A#2_Price List"));
        assertEquals("abcd 12345 assignment 4 demo", ContentMatcher.normalize("ABCD12345Assignment 4 Demo"));
    }

    // Numbered and plain names produce a single pattern; only midterms and finals have a fallback.
    private static Pattern onlyPattern(String assignmentName){
        List<Pattern> patterns = ContentMatcher.buildNamePatterns(assignmentName);
        assertEquals(1, patterns.size(), assignmentName);
        return patterns.get(0);
    }

    @Test
    public void numberedNamesMatchCommonSpellings(){
        Pattern pattern = onlyPattern("Assignment 2");

        for(String title : List.of("Assignment#2", "Assignment #2", "Help for Assignment 2", "HelpForAssignment2",
                                   "A#2_Menu Prices", "Asg 2 video explanation transcript", "Assignment_2_Instructions")){
            assertTrue(pattern.matcher(ContentMatcher.normalize(title)).find(), title);
        }
        for(String title : List.of("Assignment 12", "Assignment 1", "Lecture 2 on variable declaration and assignment")){
            assertFalse(pattern.matcher(ContentMatcher.normalize(title)).find(), title);
        }
    }

    @Test
    public void numberMatchesIgnoreLeadingZerosButNotOtherNumbers(){
        Pattern lab = onlyPattern("Lab 1");

        assertTrue(lab.matcher(ContentMatcher.normalize("Lab 01 Instructions")).find());
        assertFalse(lab.matcher(ContentMatcher.normalize("Lab 10 Instructions")).find());
    }

    @Test
    public void courseCodesAndStructuralWordsAreNotUsedToMatch(){
        Pattern deliverable = onlyPattern("ABCD12345 Deliverable 1");

        assertTrue(deliverable.matcher(ContentMatcher.normalize("ABCD12345_Deliverable 1 - Team Contract")).find());
        assertFalse(deliverable.matcher(ContentMatcher.normalize("ABCD12345 Week 1 Slides")).find());

        Pattern reflection = onlyPattern("Week 3 Reflection");
        assertFalse(reflection.matcher(ContentMatcher.normalize("Week 3 Lecture Slides")).find());
    }

    @Test
    public void midtermFallsBackToCommonSpellingsWhenNothingHasTheFullName(){
        ContentToc toc = new ContentToc(List.of(
            module("Mid Term Instructions", file("Exam Rules", "rules.pdf")),
            module("Week 6", file("Mid_Exam_Instructions_F26", "mid_exam.odt"))
        ));

        // Both match through the generic fallback; the instructions file sorts first.
        assertEquals(List.of("Mid_Exam_Instructions_F26", "Exam Rules"), titles(ContentMatcher.findMatches(toc, "Midterm PART B ")));
    }

    @Test
    public void midtermPrefersFilesCarryingTheFullName(){
        ContentToc toc = new ContentToc(List.of(
            module("Co-op Forms",
                file("Midterm Student Check-In Survey", "survey.docx"),
                file("Midterm Employer Evaluation", "evaluation.docx"))
        ));

        assertEquals(List.of("Midterm Employer Evaluation"), titles(ContentMatcher.findMatches(toc, "Midterm Employer Evaluation")));
        assertEquals(List.of("Midterm Student Check-In Survey"), titles(ContentMatcher.findMatches(toc, "Midterm Student Check-In Survey")));
    }

    @Test
    public void finalPrefersFullNameThenFallsBack(){
        ContentToc both = new ContentToc(List.of(
            module("Final Project",
                file("Final Project Proposal Guide", "proposal.pdf"),
                file("Final Project Report Template", "report.docx"))
        ));
        ContentToc onlyGeneric = new ContentToc(List.of(
            module("Week 12", file("Final Project Guidelines", "guidelines.pdf"))
        ));

        // The module is titled "Final Project", so it only matches through the generic fallback.
        assertEquals(List.of("Final Project Report Template"), titles(ContentMatcher.findMatches(both, "Final Project Report")));
        assertEquals(List.of("Final Project Guidelines"), titles(ContentMatcher.findMatches(onlyGeneric, "Final Project Report")));
    }

    @Test
    public void singleWordNamesAreTooGenericToMatch(){
        assertTrue(ContentMatcher.buildNamePatterns("Essay").isEmpty());
    }

    @Test
    public void matchedModuleContributesItsDocumentsOnly(){
        ContentToc toc = new ContentToc(List.of(
            module("Week 1", file("Week 1 Lecture", "week1.pptx")),
            module("Assignment#2",
                file("A#2_Price List", "A#2_Price List.pdf"),
                file("Assignment 2: Calculations Example", "assignment 2 calculations example.pptx"),
                file("Assignment_2_Instructions and Worksheet", "Assignment_2_Instructions and Worksheet.docx"),
                file("Calculations Data", "calculations.xlsx"),
                video("Assignment Video Explanation"))
        ));

        List<String> matches = titles(ContentMatcher.findMatches(toc, "Assignment 2"));

        // Instructions first, then explanations, then everything else; spreadsheets and videos are skipped.
        assertEquals(List.of("Assignment_2_Instructions and Worksheet", "Assignment 2: Calculations Example", "A#2_Price List"), matches);
    }

    @Test
    public void hashInFileNameIsNotTreatedAsAUrlFragment(){
        assertTrue(ContentMatcher.isReadable(file("A#2_Chef's Menu", "A#2_Chef's Menu.pdf")));
        assertTrue(ContentMatcher.isReadable(file("Week 3 Notes", "notes.pdf#page=2")));
        assertFalse(ContentMatcher.isReadable(file("Starter Code", "starter.zip")));
    }

    @Test
    public void fileNamedAfterAssignmentIsFoundInAnyModule(){
        ContentToc toc = new ContentToc(List.of(
            module("Week 1",
                file("Week 1 Lecture", "week1.pptx"),
                file("ABCD12345_Deliverable 1 - Team Contract", "contract.docx"))
        ));

        assertEquals(List.of("ABCD12345_Deliverable 1 - Team Contract"), titles(ContentMatcher.findMatches(toc, "Deliverable 1")));
    }

    @Test
    public void nestedModulesInsideAMatchedModuleAreIncluded(){
        ContentModule nested = module("Resources", file("Sample Output", "sample.pdf"));
        ContentModule parent = new ContentModule(nextId++, "Help For Assignment 4", List.of(nested),
                                                 List.of(file("ABCD12345Assignment 4 Demo", "demo.mp4")));
        ContentToc toc = new ContentToc(List.of(parent));

        assertEquals(List.of("Sample Output"), titles(ContentMatcher.findMatches(toc, "Assignment#4")));
    }

    @Test
    public void matchesAreCappedWithRequirementsFirst(){
        ContentTopic[] files = new ContentTopic[ContentMatcher.MAX_MATCHES_PER_ASSIGNMENT + 3];
        for(int i = 0; i < files.length - 1; i++){
            files[i] = file("A#3 Example " + i, "example" + i + ".pdf");
        }
        files[files.length - 1] = file("Assignment 3 Rubric", "rubric.docx");
        ContentToc toc = new ContentToc(List.of(module("Assignment 3", files)));

        List<ContentTopic> matches = ContentMatcher.findMatches(toc, "Assignment 3");

        assertEquals(ContentMatcher.MAX_MATCHES_PER_ASSIGNMENT, matches.size());
        assertEquals("Assignment 3 Rubric", matches.get(0).title());
    }
}
