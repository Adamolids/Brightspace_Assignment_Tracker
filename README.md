Brightspace Assignment Tracker
A Java 17 synchronization utility for the Brightspace (D2L) platform. This tool automates the retrieval of course metadata, assignment folders, and attachments by interfacing directly with the platform's internal REST API.

Architecture & Evolution
The Shift from Scraping to REST
The project's first iteration utilized Jsoup for HTML parsing. While functional, the reliance on front end CSS selectors was unstable. By inspecting network traffic, I identified the Brightspace Valence API endpoints used for mobile and dashboard views.

The current version utilizes a strictly decoupled architecture:

Network Layer: BrightspaceClient handles session based authentication and raw HTTP requests.

Parsing Layer: JsonAssignmentParser utilizes Jackson to map deeply nested JSON payloads into flat, local POJOs.

Service Layer: AssignmentService manages synchronization logic, using HashSet comparisons to prevent duplicate entries during local updates.

Technical Implementation
Project Structure

Plaintext
├── src/main/java/com/alexdamolidis/
│   ├── client/           # HttpClient wrappers & Auth logic
│   ├── model/            # Course, Assignment, and Attachment POJOs
│   ├── service/          # Sync logic & Duplicate prevention
│   └── parser/           # Jackson based JSON mapping
├── src/test/java/        # JUnit 5 & Mockito test suites
└── cookies.txt           # Local session storage (Git ignored)
Data Mapping Strategy
The Brightspace API returns complex objects where academic metadata is often obfuscated. The engine filters these based on internal organizational codes:

Academic Filter: Identifies _VC string markers in course codes to distinguish between credit bearing courses and non academic resources.

Integrity Logic: Uses a combination of orgUnitId and folderId as a composite key to ensure that assignments moved between categories are not duplicated.

Setup & Usage
Authentication Setup
This project requires a cookies.txt file in the root directory. To configure:

Create a cookies.txt file in the project root.

Log in to Brightspace in a browser and open Developer Tools (F12).

Under Network > Doc, find the home request and copy the Cookie header string.

Paste the entire string into the first line of cookies.txt.

Note: As these are session based cookies, the file must be updated if the server returns a 403 Forbidden status.

Execution

Bash
mvn clean compile
mvn test
mvn exec:java -Dexec.mainClass="com.alexdamolidis.App"
Roadmap
[x] REST API integration and JSON to Object mapping.

[x] Mockito backed unit test suite for sync logic.

[ ] LLM based assignment summarization and priority scoring.

[ ] Persistent local storage (SQLite integration).

[ ] Google Calendar API export.
