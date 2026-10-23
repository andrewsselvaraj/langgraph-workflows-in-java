# LangGraph Workflows in Java

A Java / Spring Boot port of [Master LangGraph Workflows in Python: 20+ real-world agent projects](https://github.com/hereandnowai/master-langgraph-workflows-in-python-20-real-world-agent-projects-by-hereandnow-ai) by HERE AND NOW AI.
Every Python `StateGraph` is rebuilt with [LangGraph4j](https://github.com/langgraph4j/langgraph4j), and every LLM, tool, embedding, document and RAG call uses [Spring AI](https://spring.io/projects/spring-ai) on Spring Boot 4.

An earlier version built on LangChain4j instead of Spring AI is kept at the git tag `langchain4j-version`.

| | Python original | This port |
|---|---|---|
| Graph engine | LangGraph 0.5 | LangGraph4j 1.9.6 |
| LLM | `ChatCerebras` / `ChatOllama` (`gpt-oss-120b`) | Spring AI `ChatModel` (OpenAI starter) pointed at Cerebras or Ollama, both OpenAI-compatible |
| Tools | `bind_tools` + `ToolNode` | Spring AI `@Tool` callbacks; the graph's `tools` node executes the calls |
| Web search | Tavily / DuckDuckGo | Tavily REST API via Spring `RestClient` |
| Embeddings + vector DB | HuggingFace `all-MiniLM-L6-v2` + FAISS | Spring AI `TransformersEmbeddingModel` (local `all-MiniLM-L6-v2`, ONNX) + `SimpleVectorStore` |
| Checkpointer | `MemorySaver` / `SqliteSaver` | `MemorySaver` with `SpringAIStateSerializer` |
| Human in the loop | `interrupt()` + `Command(resume=...)` | `interruptBefore(...)` + `updateState(...)` + `GraphInput.resume()` |
| Structured output | `with_structured_output(Invoice)` | Spring AI `BeanOutputConverter` into a Java `record` |
| PDF and web pages | `pypdf`, Tavily | Spring AI `PagePdfDocumentReader`, `JsoupDocumentReader` |
| Runtime | `python main.py` per project | One Spring Boot app: REST API **and** a `--workflow=<id>` CLI |

## Requirements

* Java 21 (the Maven wrapper is included, no Maven install needed)
* Internet on first use of projects 10, 13, 15, 16 and 20: Spring AI downloads the MiniLM model and the DJL native libraries once (a few hundred MB, cached under `~/.djl.ai`)
* A Cerebras API key, **or** a local [Ollama](https://ollama.com) with `gpt-oss:20b`
* Optional: Tavily key (web search), WordPress credentials (project 22), GitHub token (project 23)

```bash
cp .env.example .env   # then fill in CEREBRAS_API_KEY (and TAVILY_API_KEY if you have one)
```

To use Ollama instead of Cerebras, set `LLM_BASE_URL=http://localhost:11434/v1` and `LLM_MODEL=gpt-oss:20b`.
Any other OpenAI-compatible endpoint works with `LLM_BASE_URL`, `LLM_API_KEY` and `LLM_MODEL`. These map to Spring AI's `spring.ai.openai.*` properties in `application.yml`.

## Run

Build once:

```bash
./mvnw package
```

### As a REST API

```bash
java -jar target/langgraph-workflows-in-java-0.1.0-SNAPSHOT.jar
```

Every project has its own controller in `src/main/java/.../api/` with a typed request and response
(Java records, validated with Bean Validation). Bad input returns `400` with a problem-details body;
a failing LLM call returns `502`.

| # | Endpoint | Request body | Response |
|---|---|---|---|
| 01 | `POST /api/basic-chatbot/chat` | `message` | `answer` |
| 02 | `POST /api/search-agent/chat` | `message`, `threadId`? | `answer`, `threadId` |
| 03 | `POST /api/hitl/questions` | `question` | `threadId`, `draft` (201) |
| 03 | `POST /api/hitl/questions/{threadId}/approval` | `answer` (`yes`/`no`) | `approved`, `answer` |
| 05 | `POST /api/conversation-loop` | `message` | `answer`, `messageCount` |
| 06 | `POST /api/items/process` | `items[]` | `summary`, `results[]` |
| 07 | `POST /api/checkpointed-chat` | `message`, `threadId`?, `repeat`? | `answer`, `threadId`, `messagesInThread` |
| 08 | `POST /api/data-extraction` | `question` | `answer`, `usedWebSearch` |
| 09 | `POST /api/slogans` | `product`, `rounds`? | `slogan`, `history[]` |
| 10 | `POST /api/hr-policy/questions` | `question`, `policy`? | `answer`, `context` |
| 11 | `POST /api/research-reports` | `topic` | `research`, `blogPost` |
| 12 | `POST /api/email-replies` | `email` | `reply` |
| 13 | `POST /api/candidate-assessments` | `candidateResponse`, `criteria`? | `assessment` |
| 14 | `POST /api/budget-summaries` | `csvData` | `totals`, `summary` |
| 15 | `POST /api/resume-matches` | `resumeText`, `jobDescription` | `matchScore`, `feedback` |
| 16 | `POST /api/rag/questions` | `question`, `knowledgeBase`? | `answer`, `context` |
| 17 | `POST /api/log-analyses` | `logContent` | `errors`, `summary` |
| 18 | `POST /api/web-reports` | `topic` | `report`, `sources` |
| 19 | `POST /api/command-summaries` | `command` (allow-listed) | `output`, `summary` |
| 20 | `POST /api/assistant` | `message` | `routedTo`, `answer` |
| 21 | `POST /api/invoices/extract` | multipart `file` (PDF); none = sample invoice | `vendorName`, `customerName`, `invoiceNumber`, `totalAmount`, `dueDate` |
| 22 | `POST /api/blog-posts/publish` | `urls[]`, `organizationName`?, `organizationDescription`? | `published`, `title`, `postId`, `blogPost` |
| 23 | `POST /api/coding-agent/builds` | `requirement` | `deployedUrl`, `review` |
| 24 | `POST /api/coding-agent-mini/pages` | `request` | `savedTo`, `review` |

`?` marks optional fields.

```bash
curl -X POST localhost:8080/api/budget-summaries -H 'Content-Type: application/json' \
     -d '{"csvData": "Category,Amount\nFood,200\nRent,1000\nFood,50"}'

curl -X POST localhost:8080/api/invoices/extract -F file=@my-invoice.pdf
```

Human-in-the-loop (project 03) takes two calls:

```bash
curl -X POST localhost:8080/api/hitl/questions -H 'Content-Type: application/json' -d '{"question": "What is LangGraph4j?"}'
# -> 201 {"status": "awaiting_approval", "threadId": "...", "draft": "..."}
curl -X POST localhost:8080/api/hitl/questions/<threadId>/approval -H 'Content-Type: application/json' -d '{"answer": "yes"}'
```

There is also a generic API over all projects, handy for exploring:

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/workflows` | Lists all workflows with their sample input |
| `GET` | `/api/workflows/{id}` | One workflow (`{id}` is `14` or `14-budget-data-summarization`) |
| `GET` | `/api/workflows/{id}/graph` | Mermaid diagram of the graph |
| `POST` | `/api/workflows/{id}` | Runs it with a free-form JSON body; missing keys fall back to the sample input |

### From the command line (like `python main.py`)

```bash
java -jar target/langgraph-workflows-in-java-0.1.0-SNAPSHOT.jar --workflow=list
java -jar target/langgraph-workflows-in-java-0.1.0-SNAPSHOT.jar --workflow=01          # interactive chat
java -jar target/langgraph-workflows-in-java-0.1.0-SNAPSHOT.jar --workflow=17          # runs with the sample input
java -jar target/langgraph-workflows-in-java-0.1.0-SNAPSHOT.jar --workflow=16 --question="What is LangGraph4j?"
```

Projects 01, 02 and 03 are interactive in the terminal, as in Python. Any other `--key=value` becomes an input value.

## The projects

| # | Python folder | Java class | Graph |
|---|---|---|---|
| 01 | project-01-basic-chatbot | `P01BasicChatbot` | `bot` |
| 02 | project-02-agent-with-search-tool | `P02AgentWithSearchTool` | `agent ⇄ tools` (ReAct loop, memory per thread) |
| 03 | project-03-human-in-the-loop-chatbot | `P03HumanInTheLoopChatbot` | `search → draft → ⏸ approve → final` |
| 05 | project-05-multi-agent-collaboration | `P05MultiAgentCollaboration` | `chatbot ⟲` until more than 5 messages |
| 06 | project-06-iterative-processing-workflow | `P06IterativeProcessingWorkflow` | `process` (map) `→ aggregate` (reduce) |
| 07 | project-07-document-summarization-pipeline | `P07DocumentSummarizationPipeline` | `chatbot` with a checkpointer |
| 08 | project-08-data-extraction-agent | `P08DataExtractionAgent` | `tool → chatbot` |
| 09 | project-09-creative-content-generation | `P09CreativeContentGeneration` | `generate → refine ⟲` for N rounds |
| 10 | project-10-decision-making-agent | `P10DecisionMakingAgent` | `retrieve → chatbot` (HR policy RAG) |
| 11 | project-11-research-and-report-generation | `P11ResearchAndReportGeneration` | `researcher → writer` |
| 12 | project-12-email-response-automation | `P12EmailResponseAutomation` | `parse → write` |
| 13 | project-13-candidate-assessment-system | `P13CandidateAssessmentSystem` | `assessor` (with retrieval) |
| 14 | project-14-budget-data-summarization | `P14BudgetDataSummarization` | `parse_csv → summarize_budget` |
| 15 | project-15-resume-job-description-matching | `P15ResumeJobDescriptionMatching` | `compare` (embeddings) `→ feedback` |
| 16 | project-16-rag-retrieval-augmented-generation-chatbot | `P16RagChatbot` | `retrieve → generate` |
| 17 | project-17-log-analysis-and-summarization | `P17LogAnalysis` | `parse_logs → summarize_errors` |
| 18 | project-18-web-research-and-report-generation | `P18WebResearchReport` | `research → report` |
| 19 | project-19-shell-command-execution-and-summarization | `P19ShellCommandSummarization` | `execute → summarize` |
| 20 | project-20-multi-domain-routing-agent | `P20MultiDomainRoutingAgent` | router `→ finance / hr / marketing / general` |
| 21 | project-21-scanned-invoice-reading-agent | `P21ScannedInvoiceReadingAgent` | `read_file → extract_data` (structured output) |
| 22 | project-22-blog-writing-and-publishing-agent | `P22BlogWritingAndPublishingAgent` | `research → write → proofread → publish` |
| 23 | project-23-coding-agent | `P23CodingAgent` | `prd → tasks → code → review → deploy` |
| 24 | project-24-coding-agent-mini | `P24CodingAgentMini` | `coder → reviewer → saver` |

The Python repo has no project 04, so neither does this one.

### Where the port differs on purpose

A few Python graphs could not run as written. The Java versions keep the intent and make them work:

* **08**: the tool node was unreachable. Here it runs first and feeds the chatbot (skipped without a Tavily key).
* **09**: the cycle only stopped by hitting `recursion_limit` (an error). Here a round counter ends it.
* **10, 20**: used `FakeEmbeddings`, and 10 never used its retriever. Here both retrieve with real MiniLM embeddings.
* **16**: returned a `context` key that wasn't in the state. Here it is.
* **20**: `route_query` was the entry point but never added as a node, and every specialist then ran `general_chat` again. Here the router is a conditional edge from `START` and each agent ends the run.
* **03, 22**: DuckDuckGo and the "Tavily get_contents" call are replaced by Tavily search (03) and a direct page download stripped to text (22).
* **19**: Because commands can arrive over HTTP, only commands in `app.shell.allowed-commands` run.

## Project layout

```
src/main/java/com/andrewsselvaraj/langgraph/
  LangGraphWorkflowsApplication.java   entry point (web or CLI)
  config/                              app settings and the local embedding model bean (the chat model is Spring AI auto-config)
  api/                                 one REST controller per project (typed records + validation) and error handling
  core/                                GraphState, Workflow API, registry, generic REST controller, CLI runner, Tavily, retriever
  workflows/                           one class per project (P01 ... P24) + WordPress and GitHub clients
src/main/resources/data/               invoice01.pdf, organisation description for the blog agent
src/test/java/                         offline tests with a fake chat model and fake embeddings
```

Each workflow builds its graph in `buildGraph()`, the direct translation of the Python `add_node` / `add_edge` block.

## Tests

```bash
./mvnw test
```

The tests use a fake chat model, fake embeddings and a mocked search engine, so they need no API keys or downloads. They run every graph except the blog agent (which needs the network) and check the branching, loops, checkpoints, interrupt/resume, retrieval, structured output, and call every REST endpoint (including validation errors and the PDF upload).

## Credits

Based on the MIT-licensed Python course material, Copyright (c) 2025 HERE AND NOW AI ([hereandnowai.com](https://hereandnowai.com)).
