package com.andrewsselvaraj.langgraph;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.andrewsselvaraj.langgraph.core.WebSearch;
import com.jayway.jsonpath.JsonPath;

/** Calls every per-project endpoint once, with a fake LLM, fake embeddings and a mocked search. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiControllersTest {

    @TempDir
    static Path output;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.output-dir", () -> output.toString());
    }

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        ChatModel fakeChatModel() {
            return new FakeChatModel();
        }

        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WebSearch webSearch;

    @BeforeEach
    void stubSearch() {
        when(webSearch.isEnabled()).thenReturn(true);
        when(webSearch.search(anyString())).thenReturn("Chennai: 31C, humidity 70%");
        when(webSearch.search(anyString(), anyInt())).thenReturn("AAPL 230.10 USD");
    }

    private ResultActions post(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void chatEndpoints() throws Exception {
        post("/api/basic-chatbot/chat", "{\"message\":\"Hi\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("echo: Hi"));
        post("/api/search-agent/chat", "{\"message\":\"Weather in Chennai?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("31C")))
                .andExpect(jsonPath("$.threadId").isNotEmpty());
        post("/api/conversation-loop", "{\"message\":\"Hi\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.messageCount").value(6));
        post("/api/checkpointed-chat", "{\"message\":\"Capital of France?\",\"repeat\":2}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.messagesInThread").value(4));
    }

    @Test
    void humanInTheLoopTakesTwoCalls() throws Exception {
        String body = post("/api/hitl/questions", "{\"question\":\"What is LangGraph4j?\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("awaiting_approval"))
                .andReturn().getResponse().getContentAsString();
        String threadId = JsonPath.read(body, "$.threadId");
        post("/api/hitl/questions/" + threadId + "/approval", "{\"answer\":\"yes\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.answer").value(startsWith("echo:")));
    }

    @Test
    void contentEndpoints() throws Exception {
        post("/api/items/process", "{\"items\":[\"apple\",\"kiwi\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Aggregated results: 2 items processed"))
                .andExpect(jsonPath("$.results.length()").value(2));
        post("/api/slogans", "{\"product\":\"tea\",\"rounds\":2}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(4));
        post("/api/research-reports", "{\"topic\":\"AI\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.blogPost").value(startsWith("echo:")));
        post("/api/email-replies", "{\"email\":\"I want a refund\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply").value(containsString("refund")));
    }

    @Test
    void knowledgeEndpoints() throws Exception {
        post("/api/data-extraction", "{\"question\":\"Apple stock price?\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.usedWebSearch").value(true));
        post("/api/hr-policy/questions", "{\"question\":\"How many vacation days?\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.context").value(containsString("20 days")));
        post("/api/rag/questions", "{\"question\":\"Who is Ada?\",\"knowledgeBase\":\"Ada wrote the first program.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.context").value(containsString("Ada")));
        post("/api/web-reports", "{\"topic\":\"solar\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources").value(containsString("Chennai")));
    }

    @Test
    void hrEndpoints() throws Exception {
        post("/api/candidate-assessments", "{\"candidateResponse\":\"I used a state machine.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessment").value(startsWith("echo:")));
        post("/api/resume-matches", "{\"resumeText\":\"Python developer\",\"jobDescription\":\"Python engineer\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.matchScore").isNumber());
    }

    @Test
    void operationsEndpoints() throws Exception {
        post("/api/budget-summaries", "{\"csvData\":\"Category,Amount\\nFood,200\\nFood,50\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals").value(containsString("250")));
        post("/api/log-analyses", "{\"logContent\":\"ERROR: disk full\\nINFO: ok\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.errors").value("ERROR: disk full"));
        post("/api/command-summaries", "{\"command\":\"rm -rf /\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Command not allowed")));
        post("/api/assistant", "{\"message\":\"What is the vacation policy?\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.routedTo").value("hr_agent"));
    }

    @Test
    void invoiceUpload() throws Exception {
        byte[] pdf = new ClassPathResource("data/invoice01.pdf").getContentAsByteArray();
        mvc.perform(multipart("/api/invoices/extract")
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorName").value("ACME Corp"))
                .andExpect(jsonPath("$.totalAmount").value(123.45));
    }

    @Test
    void codingEndpoints() throws Exception {
        post("/api/coding-agent/builds", "{\"requirement\":\"calculator\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.deployedUrl").value(startsWith("file:")));
        post("/api/coding-agent-mini/pages", "{\"request\":\"a landing page\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedTo").value(containsString("index.html")));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        post("/api/basic-chatbot/chat", "{\"message\":\"\"}").andExpect(status().isBadRequest());
        post("/api/items/process", "{\"items\":[]}").andExpect(status().isBadRequest());
        post("/api/slogans", "{\"product\":\"tea\",\"rounds\":99}").andExpect(status().isBadRequest());
        post("/api/blog-posts/publish", "{}").andExpect(status().isBadRequest());
    }
}
