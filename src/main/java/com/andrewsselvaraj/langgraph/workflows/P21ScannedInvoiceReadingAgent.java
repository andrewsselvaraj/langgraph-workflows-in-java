package com.andrewsselvaraj.langgraph.workflows;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.core.AbstractWorkflow;
import com.andrewsselvaraj.langgraph.core.GraphState;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * project-21-scanned-invoice-reading-agent: {@code read_file} (Spring AI PDF reader)
 * {@code -> extract_data}. The Python {@code llm.with_structured_output(Invoice)} becomes Spring AI's
 * {@link BeanOutputConverter}: it adds the JSON schema of {@link Invoice} to the prompt and parses the
 * reply back into the record.
 */
@Component
public class P21ScannedInvoiceReadingAgent extends AbstractWorkflow {

    static final String SAMPLE_INVOICE = "classpath:data/invoice01.pdf";

    /** Structured data the LLM must extract (the Pydantic model). */
    public record Invoice(
            @JsonProperty("vendor_name") @JsonPropertyDescription("The name of the company issuing the invoice.") String vendorName,
            @JsonProperty("customer_name") @JsonPropertyDescription("The name of the customer on the invoice.") String customerName,
            @JsonProperty("invoice_number") @JsonPropertyDescription("The unique identifier for the invoice.") String invoiceNumber,
            @JsonProperty("total_amount") @JsonPropertyDescription("The total amount due on the invoice.") double totalAmount,
            @JsonProperty("due_date") @JsonPropertyDescription("The date when the invoice payment is due.") String dueDate) {
    }

    private final BeanOutputConverter<Invoice> converter = new BeanOutputConverter<>(Invoice.class);
    private final ObjectMapper json = new ObjectMapper();

    public P21ScannedInvoiceReadingAgent(ChatModel llm) {
        super(llm, "21", "scanned-invoice-reading-agent", "Invoice reader",
                "Reads a PDF invoice and extracts vendor, customer, number, total and due date as JSON.");
    }

    @Override
    protected StateGraph<GraphState> buildGraph() throws GraphStateException {
        return GraphState.newGraph()
                .addNode("read_file", node_async(state -> Map.of("invoice_text", readPdf(state.text("invoice_path")))))
                .addNode("extract_data", node_async(state -> {
                    String prompt = """
                            You are an expert accounting assistant.
                            Analyze the following invoice text and extract the key details into the required JSON format.

                            Invoice Text:
                            ---
                            %s
                            ---

                            %s
                            """.formatted(state.text("invoice_text"), converter.getFormat());
                    Invoice invoice = converter.convert(ask(prompt));
                    Map<String, Object> data = json.convertValue(invoice, new TypeReference<LinkedHashMap<String, Object>>() { });
                    return Map.of("structured_data", data);
                }))
                .addEdge(START, "read_file")
                .addEdge("read_file", "extract_data")
                .addEdge("extract_data", END);
    }

    static String readPdf(String location) {
        if (!location.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("invoice_path must point to a .pdf file");
        }
        Resource pdf = location.startsWith("classpath:")
                ? new ClassPathResource(location.substring("classpath:".length()))
                : new FileSystemResource(location);
        return new PagePdfDocumentReader(pdf).get().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
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
