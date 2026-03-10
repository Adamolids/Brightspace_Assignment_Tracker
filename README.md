# Brightspace Assignment Tracker

A Java 17 synchronization utility for the Brightspace (D2L) platform.  
This tool automates the retrieval of course metadata, assignment folders, and attachments by interfacing directly with the platform’s REST API endpoints.

---

## Architecture and Evolution

### The Shift from Scraping to REST

The project’s first iteration utilized Jsoup for HTML parsing. While functional, reliance on front end CSS selectors proved unstable. After researching Brightspace’s developer resources and analyzing network traffic, I transitioned to using REST API endpoints for a more reliable and maintainable integration.

### From Data Retrieval to Stateful Synchronization

As the project evolved, the focus expanded beyond simple data retrieval:

- API responses are mapped into internal domain models to decouple the application from external schema changes.
- A repository layer was introduced to hydrate previously synchronized data from local JSON storage, enabling incremental synchronization across runs.
- Synchronization logic was redesigned to be idempotent, using composite keys (`orgUnitId` + `folderId`) and `HashSet` based comparisons to prevent duplicate entries across repeated runs.
- Unit tests were later introduced using JUnit 5 and Mockito to validate synchronization behavior and edge cases.

### Current Architecture

The current version follows a layered architecture with clear separation of concerns:

**Network Layer:** `BrightspaceClient` handles session based authentication and raw HTTP communication.

**Repository Layer:** `SemesterRepository` hydrates previously synchronized data from local JSON storage, supporting incremental updates and minimizing unnecessary network calls.

**Service Layer:** `AssignmentService` orchestrates synchronization logic and ensures idempotent updates using `HashSet` based comparisons.

**AI Layer:** `LlmService` enriches assignments with AI generated summaries, reasoning, and priority scores by constructing structured prompts, communicating with the Gemini API, and mapping JSON responses back into the domain model.

---

## Technical Implementation

### Project Structure

```text
src/main/java/com/alexdamolidis/
├── ai/         (LLM integration and AI enrichment services)
├── model/      (Semester, Course, Assignment, and Attachment POJOs)
├── parser/     (HTML sanitization and text normalization utilities)
├── repository/ (Local JSON persistence and state rehydration)
├── service/    (Synchronization logic and duplicate detection)
├── util/       (API communication, endpoint configuration, and content extraction helpers)
└── test/java/  (JUnit 5 and Mockito test suites)

.cookies.example.txt (Template for Brightspace session cookies)
.env.example.txt     (Template for required environment variables)

cookies.txt     (Local session storage – git-ignored)
.env            (Stores LLM API key    - git-ignored)
```

### Data Mapping Strategy

The Brightspace API returns large, nested objects. To simplify processing, I leveraged URL query filters to limit responses to only relevant records before mapping them to domain models.

Credit Based Filtering: Uses the _VC marker in course codes to differentiate credit bearing courses from non academic resources. Since both may contain assignments, this ensures only relevant coursework is processed.

Integrity Logic: Implements a composite key (orgUnitId + folderId) to guarantee assignment uniqueness and maintain consistent state across repeated synchronization runs.

## Setup and Usage
### AI Summarization Notice
The assignment summarization feature sends assignment contents and attachment text to a Large Language Model for summarization, reasoning, and priority score.

Users should be aware that Google's free tier API may store and use submitted content for service improvement. Because course materials may be considered institutional intellectual property, sending this data through the free tier API could violate your school's policies.

To avoid this risk, it is strongly recommended to use a paid API tier or locally hosted LLM where submitted data is not retained or used for training.

### Authentication Setup

This project requires a cookies.txt file in the root directory.

Create a cookies.txt file in the project root.

Log in to Brightspace in a browser and open Developer Tools (F12).

Under Network > Doc, find the home request and copy the full Cookie request header value.

Paste the entire string into the first line of cookies.txt.

Note: Because these are session based cookies, the file must be updated if the server returns a 403 Forbidden status.

### Multi Institution Compatibility

While configured for the D2L "Slate" environment, this tool can be adapted for other Brightspace instances.
Modify the EndpointBuilder class to update the base URL or API version string to match your institution’s Brightspace domain.

### Execution Commands
mvn clean compile
mvn test
mvn exec:java -Dexec.mainClass="com.alexdamolidis.App"

## Roadmap

 - [x] REST API integration and JSON to object mapping

 - [x] Mockito backed unit test suite for synchronization logic

 - [x] LLM based assignment summarization and priority scoring

 - [ ] Persistent local storage (SQLite integration)

 - [ ] Google Calendar API export
