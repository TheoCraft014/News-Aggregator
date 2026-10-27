# Multithreaded News Aggregator and Analyzer

A concurrent Java application designed to process large volumes of news articles from JSON files, eliminate duplicates, filter them by language and category, extract English keywords, and generate statistical reports.


## Project Structure

### 1. Core Data & Input Classes

*   **`Article.java`**
    *   **Purpose:** Serves as the core data model representing a single news article.
    *   **Implementation:** Utilizes the **Builder Pattern** to cleanly construct immutable or loosely-coupled article objects containing metadata fields like `uuid`, `title`, `author`, `url`, `text`, `published` timestamp, `language`, and a list of `categories`.
*   **`JsonInput.java`**
    *   **Purpose:** Handles the deserialization of raw JSON data into Java objects.
    *   **Implementation:** Uses the `json-simple` library to parse JSON files path-by-path, mapping arrays and objects into a list of populated `Article` instances.

### 2. Concurrency and Execution Classes

*   **`NewsAggregator.java`**
    *   **Purpose:** Acts as the primary entry point (`main` method) and coordinator for the application.
    *   **Implementation:** 
        *   Reads configuration files (languages, categories, and linking words) specified through command-line arguments.
        *   Initializes thread-safe synchronization utilities (`ConcurrentHashMap`, `BlockingQueue`, and `CyclicBarrier`).
        *   Spawns and manages worker threads to process the input article file paths in parallel.
*   **`MyThread.java`**
    *   **Purpose:** Extends `Thread` to implement the concurrent producer-consumer pipeline for each worker thread.
    *   **Implementation:** 
        *   **Phase 1 (Parsing + Counting):** Deserializes assigned JSON paths and registers `uuid` and `title` frequencies to track duplicates.
        *   **Phase 2 (Filtering + Mapping):** Filters out duplicate articles and indexes unique articles into language and category maps.
        *   **Phase 3 (Keyword Extraction):** Tokenizes English article text, cleans punctuation, filters out stop/linking words, and compiles keyword frequency maps.
        *   **Phase 4 & 5 (File + Report Generation):** Uses thread synchronization (`CyclicBarrier`) to coordinate writing output text files (`keywords_count.txt`, `all_articles.txt`, language/category lists, and the final `reports.txt` summary).

---

## Workflow

1. **Initialization:** The program reads input configuration arguments and sets up shared thread-safe queues for file paths, languages, and categories.
2. **Concurrent Processing:** Worker threads concurrently read JSON data, parse the contents, and eliminate duplicates.
3. **Data Indexing:** Articles are sorted, indexed by category and language, and parsed for English keywords.
4. **Output Generation:** The program exports structured text outputs, including individual language/category files, a sorted keyword count list, a master chronological article list, and a final metrics report (`reports.txt`).
