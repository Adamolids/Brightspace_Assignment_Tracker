# Brightspace Assignment Tracker

A Java 17 synchronization utility for the Brightspace (D2L) platform.  
This tool automates the retrieval of course metadata and assignment folders by interfacing with the platform’s REST API endpoints, then leverages a Large Language Model (Gemini API) to enrich raw coursework data with AI generated summaries, reasoning, and intelligent priority scoring.

---

## Architecture and Evolution

### The Shift from Scraping to REST

The project’s first iteration utilized Jsoup for HTML parsing. While functional, reliance on front end CSS selectors proved unstable. After researching Brightspace’s developer resources and analyzing browser network traffic, I transitioned to using REST API endpoints for a more reliable and maintainable integration.

### From Data Retrieval to Stateful Synchronization

As the project evolved, the focus expanded beyond simple data retrieval:

- API responses are mapped into internal domain models to decouple the application from external schema changes.
- A repository layer was introduced to hydrate previously synchronized data from local JSON storage, enabling incremental synchronization across runs.
- Synchronization logic was redesigned to be idempotent, using composite keys (`orgUnitId` + `folderId`) and `HashSet` based comparisons to prevent duplicate entries across repeated runs.
- Unit tests were later introduced using JUnit 5 and Mockito to validate synchronization behavior and edge cases.

### Transition to Relational Persistence

The most recent evolution replaced local JSON storage with a SQLite backed repository, improving data integrity and enabling more robust synchronization.

- Atomic Saves: The sync pipeline now gathers all required data (API metadata and LLM enrichment) before performing a single final upsert, preventing partial writes and reducing the risk of database corruption.
- Timezone Awareness: Migration from `LocalDateTime` to `OffsetDateTime` ensures assignment deadlines remain accurate regardless of system timezone.
- Hallucination Safeguards: `daysUntilDue` is now computed in application code instead of by the LLM, preventing hallucinated values.

### Current Architecture

The current version follows a layered architecture with clear separation of concerns:

**Network Layer:** `BrightspaceClient` handles session based authentication and raw HTTP communication.

**Repository Layer:** `SemesterRepository` persists and hydrates assignment data from the local SQLite database, enabling incremental synchronization across runs.

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
├── repository/ (SQLite persistence and SQL schema management)
├── service/    (Synchronization logic and duplicate detection)
├── util/       (API communication, endpoint configuration, and content extraction helpers)
└── test/java/  (JUnit 5 and Mockito test suites)

.cookies.example.txt (Template for Brightspace session cookies)
.env.example.txt     (Template for required environment variables)

tracker.db      (Main relational database - git-ignored)
cookies.txt     (Local session storage    – git-ignored)
.env            (Stores LLM API key       - git-ignored)
```

### Data Mapping Strategy

The Brightspace API returns large, nested objects. To simplify processing, I leveraged URL query filters to limit responses to only relevant records before mapping them to domain models.

Credit Based Filtering: Uses the _VC marker in course codes to differentiate credit bearing courses from non academic resources. Since both may contain assignments, this ensures only relevant coursework is processed.

Integrity Logic: Implements a composite key (orgUnitId + folderId) to guarantee assignment uniqueness and maintain consistent state across repeated synchronization runs.

## Setup and Usage
### AI Summarization Notice
The assignment summarization feature sends assignment contents and attachment text to a Large Language Model for summarization, reasoning, and priority scoring.

Users should be aware that Google's free tier API may store and use submitted content for service improvement. Because course materials may be considered institutional intellectual property, sending this data through the free tier API could violate your school's policies.

To avoid this risk, it is strongly recommended to use a paid API tier or locally hosted LLM where submitted data is not retained or used for training.

### Authentication Setup

This project requires a cookies.txt file and a .env in the root directory.

1. Create a cookies.txt and a .env file in the project root.

2. Log in to Brightspace in a browser and open Developer Tools (F12).

3. Under Network > Doc, find the home request and copy the full Cookie request header value.

4. Paste the entire string into the first line of cookies.txt.

5. Obtain Google API key.

6. Paste the key into the .env file as indicated in `.env.example.txt`.

Note: Brightspace's official OAuth 2.0 requires institutional admin approval and cannot be self registered by students. To enable student level access, this project uses session-based cookie authentication as a workaround. The cookies.txt file must be refreshed if the server returns a 403 Forbidden response.

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

 - [x] Persistent local storage (SQLite integration)

 - [ ] Google Calendar API export
