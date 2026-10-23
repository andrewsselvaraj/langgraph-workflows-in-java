package com.andrewsselvaraj.langgraph.api;

import static com.andrewsselvaraj.langgraph.api.Results.as;
import static com.andrewsselvaraj.langgraph.api.Results.input;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.andrewsselvaraj.langgraph.workflows.P21ScannedInvoiceReadingAgent;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Project 21: upload a PDF invoice (multipart field {@code file}) and get its key fields back.
 * Without a file, the bundled sample invoice is used.
 */
@RestController
public class InvoiceController {

    public record Invoice(String vendorName, String customerName, String invoiceNumber, double totalAmount,
                          String dueDate) {
    }

    public record Response(@JsonProperty("structured_data") StructuredData structuredData) {
    }

    record StructuredData(@JsonProperty("vendor_name") String vendorName,
                          @JsonProperty("customer_name") String customerName,
                          @JsonProperty("invoice_number") String invoiceNumber,
                          @JsonProperty("total_amount") double totalAmount,
                          @JsonProperty("due_date") String dueDate) {
    }

    private final P21ScannedInvoiceReadingAgent workflow;

    public InvoiceController(P21ScannedInvoiceReadingAgent workflow) {
        this.workflow = workflow;
    }

    @PostMapping(value = "/api/invoices/extract", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    public Invoice extract(@RequestParam(value = "file", required = false) MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return toInvoice(workflow.run(input()));
        }
        Path pdf = Files.createTempFile("invoice-", ".pdf");
        try {
            file.transferTo(pdf);
            return toInvoice(workflow.run(input("invoice_path", pdf.toString())));
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    private static Invoice toInvoice(java.util.Map<String, Object> result) {
        StructuredData d = as(result, Response.class).structuredData();
        return new Invoice(d.vendorName(), d.customerName(), d.invoiceNumber(), d.totalAmount(), d.dueDate());
    }
}
