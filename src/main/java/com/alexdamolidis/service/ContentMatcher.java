package com.alexdamolidis.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alexdamolidis.model.ContentToc;
import com.alexdamolidis.model.ContentToc.ContentModule;
import com.alexdamolidis.model.ContentToc.ContentTopic;

/**
 * Finds the files in a course's Content section that belong to an assignment.
 *
 * Professors do not link assignment folders into Content, so matching is done by name. A module
 * named after the assignment (e.g. "Assignment#2" or "Help for Assignment 2") contributes all of its
 * readable files, and a file named after the assignment (e.g. "A#2_Menu") is included wherever it is.
 */
public class ContentMatcher {

    // Document types worth sending to Claude. Videos, archives, code samples, and spreadsheets are skipped.
    static final Set<String> READABLE_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "odt", "odp", "rtf", "txt", "md", "html", "htm");

    static final int MAX_MATCHES_PER_ASSIGNMENT = 10;

    // Words that describe where content lives rather than what the assignment is, so "Week 3" in an
    // assignment name does not pull in the whole week.
    private static final Set<String> STRUCTURAL_WORDS = Set.of("week", "module", "unit", "chapter", "lesson", "part", "section", "topic", "day", "class", "lecture");

    private static final Set<String> ASSIGNMENT_WORDS = Set.of("assignment", "assignments", "assign", "asg", "assgn", "a");

    // Ways professors write "assignment" in titles, e.g. "A#2_Menu" or "Asg 2 video explanation".
    private static final String ASSIGNMENT_ALIASES = "(?:assignment|assignments|assign|asg|assgn|a)";

    // One or two digit numbers only, so course codes ("ABCD12345") and years ("Fall 2026") are ignored.
    private static final Pattern NUMBERED_NAME = Pattern.compile("\\b([a-z]+) 0*(\\d{1,2})\\b");
    private static final Pattern MIDTERM       = Pattern.compile("\\bmid ?(?:term|exam)s?\\b");
    private static final Pattern FINAL         = Pattern.compile("\\bfinal (?:exam|project|assessment|assignment)\\b");

    // Titles that usually hold the actual requirements are sent to Claude first, then titles that explain them,
    // so a module with many data files (e.g. seven restaurant menus) does not crowd out the useful ones.
    private static final Pattern REQUIREMENTS_TITLE = Pattern.compile("instruction|rubric|requirement|guideline|outline|brief|breakdown|worksheet|template|description");
    private static final Pattern EXPLANATION_TITLE  = Pattern.compile("example|sample|explanation|transcript|walkthrough|overview|faq");

    private static final Pattern CAMEL_OR_DIGIT_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=\\d)|(?<=\\d)(?=[A-Za-z])");
    private static final Pattern NON_ALPHANUMERIC        = Pattern.compile("[^a-z0-9]+");

    private ContentMatcher(){}

    /**
     * Finds the readable Content section files that appear to belong to an assignment.
     *
     * @param toc the course's Content table of contents
     * @param assignmentName the assignment's name in Brightspace
     * @return matching topics, instructions and rubrics first, then examples and explanations, capped at
     *         {@value #MAX_MATCHES_PER_ASSIGNMENT}. Empty if the name is too generic to match safely.
     */
    public static List<ContentTopic> findMatches(ContentToc toc, String assignmentName){
        for(Pattern namePattern : buildNamePatterns(assignmentName)){
            Map<Long, ContentTopic> matches = new LinkedHashMap<>();
            collectMatches(toc.modules(), namePattern, false, matches);

            if(matches.isEmpty()) continue;

            return matches.values().stream()
                          .sorted(Comparator.comparingInt(ContentMatcher::relevanceRank))
                          .limit(MAX_MATCHES_PER_ASSIGNMENT)
                          .toList();
        }
        return List.of();
    }

    private static int relevanceRank(ContentTopic topic){
        String title = normalize(topic.title());

        if(REQUIREMENTS_TITLE.matcher(title).find()) return 0;
        if(EXPLANATION_TITLE.matcher(title).find())  return 1;
        return 2;
    }

    private static void collectMatches(List<ContentModule> modules, Pattern namePattern, boolean insideMatchedModule,
                                       Map<Long, ContentTopic> matches){
        for(ContentModule module : modules){
            boolean moduleMatches = insideMatchedModule || namePattern.matcher(normalize(module.title())).find();

            for(ContentTopic topic : module.topics()){
                if(isReadable(topic) && (moduleMatches || namePattern.matcher(normalize(topic.title())).find())){
                    matches.putIfAbsent(topic.topicId(), topic);
                }
            }
            collectMatches(module.modules(), namePattern, moduleMatches, matches);
        }
    }

    /**
     * Builds the patterns used to recognize an assignment in Content titles, most specific first.
     * findMatches uses the first pattern that matches anything.
     *
     * - Numbered names match on their kind and number: "Assignment 3 Part 1" matches "Assignment#3", "A#3",
     *   and "HelpForAssignment3", and "Lab 1" matches "Lab 01" but not "Lab 10".
     * - Midterms and finals try the full name first, so "Midterm Employer Evaluation" does not also pick up
     *   "Midterm Student Check-In Survey". Only when nothing carries the full name do they fall back to the
     *   common spellings ("Mid Term", "Mid_Exam", or the "Final Project" part of "Final Project Report").
     * - Anything else needs at least two words and matches as a whole phrase.
     *
     * @param assignmentName the assignment's name in Brightspace
     * @return the patterns to try in order, empty if the name is too generic to match safely
     */
    static List<Pattern> buildNamePatterns(String assignmentName){
        String  name       = normalize(assignmentName);
        Pattern fullPhrase = name.split(" ").length >= 2 ? Pattern.compile("\\b" + Pattern.quote(name) + "\\b") : null;

        Matcher numbered = NUMBERED_NAME.matcher(name);
        while(numbered.find()){
            String kind = numbered.group(1);
            if(STRUCTURAL_WORDS.contains(kind)) continue;

            String kindPattern = ASSIGNMENT_WORDS.contains(kind) ? ASSIGNMENT_ALIASES : Pattern.quote(kind);
            return List.of(Pattern.compile("\\b" + kindPattern + " 0*" + numbered.group(2) + "\\b"));
        }
        if(MIDTERM.matcher(name).find()){
            return fullNameFirst(fullPhrase, MIDTERM);
        }
        Matcher fin = FINAL.matcher(name);
        if(fin.find()){
            return fullNameFirst(fullPhrase, Pattern.compile("\\b" + Pattern.quote(fin.group()) + "\\b"));
        }
        return fullPhrase != null ? List.of(fullPhrase) : List.of();
    }

    private static List<Pattern> fullNameFirst(Pattern fullPhrase, Pattern generic){
        return fullPhrase != null ? List.of(fullPhrase, generic) : List.of(generic);
    }

    /**
     * Lowercases a title and splits it into plain words, so "HelpForAssignment3", "Assignment#3",
     * and "assignment-3" all become "... assignment 3".
     */
    static String normalize(String text){
        if(text == null) return "";

        String separated = CAMEL_OR_DIGIT_BOUNDARY.matcher(text).replaceAll(" ");
        return NON_ALPHANUMERIC.matcher(separated.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    static boolean isReadable(ContentTopic topic){
        if(topic.isHidden() || topic.isBroken() || !"File".equals(topic.typeIdentifier())) return false;

        return READABLE_EXTENSIONS.contains(extensionOf(topic.url()));
    }

    // Only the query string is cut before finding the extension, since professors use '#' in file names
    // ("A#2_Menu.pdf"). A trailing fragment is cut from the extension itself instead.
    private static String extensionOf(String url){
        if(url == null) return "";

        String path     = url.split("\\?", 2)[0];
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int    dot      = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).split("#", 2)[0].toLowerCase(Locale.ROOT) : "";
    }
}
