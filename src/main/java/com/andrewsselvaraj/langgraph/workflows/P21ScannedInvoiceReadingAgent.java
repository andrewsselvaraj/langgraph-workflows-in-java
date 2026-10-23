package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * project-21-scanned-invoice-reading-agent: {@code read_file} (PDFBox) {@code -> extract_data}.
 * The Python {@code llm.with_structured_output(Invoice)} becomes a LangChain4j AI Service that
 * returns the {@link Invoice} record directly.
 */
@Component
public class P21ScannedInvoiceReadingAgent extends AbstractWorkflow {

    static final String SAMPLE_INVOICE = "classpath:data/invoice01.pdf";

    /** Structured data the LLM must extract (the Pydantic model). */
    public record Invoice(
            @Description("The name of the company issuing the invoice.") String vendorName,
            @Description("The name of the customer on the invoice.") String customerName,
            @Description("The unique identifier for the invoice.") String invoiceNumber,
            @Description("The total amount due on the invoice.") double totalAmount,
            @Description("The date when the invoice payment is due.") String dueDate) {
    }

    public interface InvoiceExtractor {
        @UserMessage("""
                You are an expert accounting assistant.
                Analyze the following invoice text and extract the key details into the required JSON format.

                Invoice Text:
                ---
                {{text}}
                ---
                """)
        Invoice extract(@V("text") String invoiceText);
    }

    private final ObjectMapper snakeCase = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public P21ScannedInvoiceReadingAgent(ChatModel llm) {
        super(llm, "21", "scanned-invoice-reading-agent", "Invoice reader",
                "Reads a PDF invoice and extracts vendor, customer, number, total and due date as JSON.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        InvoiceExtractor extractor = AiServices.create(InvoiceExtractor.class, llm);
        return GraphState.newGraph()
                .addNode("read_file", node_async(state -> Map.of("invoice_text", readPdf(state.text("invoice_path")))))
                .addNode("extract_data", node_async(state -> {
                    Invoice invoice = extractor.extract(state.text("invoice_text"));
                    Map<String, Object> data = snakeCase.convertValue(invoice, new TypeReference<LinkedHashMap<String, Object>>() { });
                    return Map.of("structured_data", data);
                }))
                .addEdge(START, "read_file")
                .addEdge("read_file", "extract_data")
                .addEdge("extract_data", END);
    }

    static String readPdf(String location) throws Exception {
        if (!location.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("invoice_path must point to a .pdf file");
        }
        try (InputStream in = location.startsWith("classpath:")
                ? new ClassPathResource(location.substring("classpath:".length())).getInputStream()
                : Files.newInputStream(Path.of(location))) {
            return new ApachePdfBoxDocumentParser().parse(in).text();
        }
    }

    @Override
    public Map<String, Object> sampleInput() {
        return Map.of("invoice_path", SAMPLE_INVOICE);
    }

    @Override
    public Map<String, Object> run(Map<String, Object> input) throws Exception {
        GraphState state = invoke(Map.of("invoice_path", str(input, "invoice_path", SAMPLE_INVOICE)));
        return Map.of("structured_data", state.value("structured_data").orElse(Map.of()));
    }
}
