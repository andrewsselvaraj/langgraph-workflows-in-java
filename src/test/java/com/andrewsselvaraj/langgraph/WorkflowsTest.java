package com.andrewsselvaraj.langgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.andrewsselvaraj.langgraph.core.WebSearch;
import com.andrewsselvaraj.langgraph.core.Workflow;
import com.andrewsselvaraj.langgraph.core.WorkflowRegistry;

import dev.langchain4j.model.chat.ChatModel;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowsTest {

    @TempDir
    static Path output;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.output-dir", () -> output.toString());
    }

    @TestConfiguration
    static class FakeLlm {
        @Bean
        @Primary
        ChatModel fakeChatModel() {
            return new FakeChatModel();
        }
    }

    @Autowired
    WorkflowRegistry registry;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WebSearch webSearch;

    @BeforeEach
    void stubSearch() {
        when(webSearch.isEnabled()).thenReturn(true);
        when(webSearch.search(anyString())).thenReturn("Chennai: 31C, humidity 70%, wind 12 km/h");
        when(webSearch.search(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("AAPL 230.10 USD");
    }

    private Map<String, Object> run(String id) throws Exception {
        Workflow w = registry.find(id).orElseThrow();
        return w.run(new java.util.HashMap<>(w.sampleInput()));
    }

    @Test
    void allProjectsAreRegistered() {
        assertThat(registry.all()).extracting(Workflow::id).containsExactly(
                "01", "02", "03", "05", "06", "07", "08", "09", "10", "11", "12",
                "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24");
    }

    @Test
    void everyGraphRendersAsMermaid() throws Exception {
        for (Workflow w : registry.all()) {
            assertThat(w.mermaid()).as(w.key()).contains("flowchart");
        }
    }

    @Test
    void basicChatbotAnswers() throws Exception {
        assertThat(run("01").get("answer")).asString().startsWith("echo:");
    }

    @Test
    void toolLoopCallsSearchThenAnswers() throws Exception {
        Map<String, Object> result = run("02");
        assertThat(result.get("answer")).asString().contains("Chennai: 31C");
    }

    @Test
    void humanInTheLoopPausesAndResumes() throws Exception {
        Map<String, Object> pending = run("03");
        assertThat(pending.get("status")).isEqualTo("awaiting_approval");
        assertThat(pending.get("draft")).asString().startsWith("echo:");

        Workflow w = registry.find("03").orElseThrow();
        Map<String, Object> approved = w.run(Map.of("threadId", pending.get("threadId"), "answer", "yes"));
        assertThat(approved.get("approved")).isEqualTo(true);
        assertThat(approved.get("answer")).isEqualTo(pending.get("draft"));

        Map<String, Object> second = run("03");
        Map<String, Object> rejected = w.run(Map.of("threadId", second.get("threadId"), "answer", "no"));
        assertThat(rejected.get("answer")).isEqualTo("I'll refine and resend shortly.");
    }

    @Test
    void thresholdLoopStopsAfterFiveMessages() throws Exception {
        assertThat(run("05").get("messageCount")).isEqualTo(6);
    }

    @Test
    void mapReduceProcessesEveryItem() throws Exception {
        Map<String, Object> result = run("06");
        assertThat(result.get("summary")).isEqualTo("Aggregated results: 3 items processed");
        assertThat((List<?>) result.get("results")).hasSize(3);
    }

    @Test
    void checkpointerKeepsThreadHistory() throws Exception {
        assertThat(run("07").get("messagesInThread")).isEqualTo(4);
    }

    @Test
    void refineCycleEndsAfterRounds() throws Exception {
        Workflow w = registry.find("09").orElseThrow();
        Map<String, Object> result = w.run(Map.of("product", "tea", "rounds", 2));
        assertThat((List<?>) result.get("history")).hasSize(4);
    }

    @Test
    void retrievalWorkflowsUseTheKnowledgeBase() throws Exception {
        assertThat(run("10").get("context")).asString().contains("20 days");
        assertThat(run("16").get("context")).asString().contains("LangGraph");
        assertThat(run("13").get("assessment")).asString().startsWith("echo:");
    }

    @Test
    void budgetIsTotalledPerCategory() throws Exception {
        assertThat(run("14").get("totals")).asString().contains("Rent", "1000");
    }

    @Test
    void resumeMatchScoresSimilarTextsHigher() throws Exception {
        double score = Double.parseDouble((String) run("15").get("matchScore"));
        assertThat(score).isBetween(0.2, 1.0);
    }

    @Test
    void logAnalyzerKeepsOnlyErrors() throws Exception {
        assertThat(run("17").get("errors")).asString().contains("Disk full").doesNotContain("INFO");
    }

    @Test
    void shellAgentRejectsCommandsOutsideTheAllowList() {
        Workflow w = registry.find("19").orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> w.run(Map.of("command", "rm -rf /")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routerPicksTheRightAgent() throws Exception {
        Workflow w = registry.find("20").orElseThrow();
        assertThat(w.run(Map.of("message", "What is the vacation policy?")).get("routedTo")).isEqualTo("hr_agent");
        assertThat(w.run(Map.of("message", "I need a new marketing slogan.")).get("routedTo")).isEqualTo("marketing_agent");
        assertThat(w.run(Map.of("message", "Tell me a joke.")).get("routedTo")).isEqualTo("general_chat");
        assertThat(run("20").get("routedTo")).isEqualTo("finance_agent");
    }

    @Test
    @SuppressWarnings("unchecked")
    void invoiceIsExtractedAsStructuredData() throws Exception {
        Map<String, Object> data = (Map<String, Object>) run("21").get("structured_data");
        assertThat(data).containsEntry("vendor_name", "ACME Corp").containsEntry("total_amount", 123.45);
    }

    @Test
    void codingAgentsWriteTheirFiles() throws Exception {
        assertThat(run("23").get("deployedUrl")).asString().startsWith("file:");
        assertThat(Path.of((String) run("24").get("savedTo"))).exists();
    }

    @Test
    void restApiListsAndRunsWorkflows() throws Exception {
        mvc.perform(get("/api/workflows")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(23));
        mvc.perform(post("/api/workflows/17").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.containsString("Database")));
        mvc.perform(get("/api/workflows/404")).andExpect(status().isNotFound());
    }
}
