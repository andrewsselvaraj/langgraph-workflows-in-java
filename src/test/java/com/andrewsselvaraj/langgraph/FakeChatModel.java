package com.andrewsselvaraj.langgraph;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Offline stand-in for the LLM so the graphs can be tested without an API key.
 * Asks for the web_search tool once when tools are offered, returns invoice JSON for the
 * invoice prompt, and otherwise echoes the start of the last message.
 */
public class FakeChatModel implements ChatModel {

    public final AtomicInteger calls = new AtomicInteger();

    @Override
    public ChatResponse doChat(ChatRequest request) {
        calls.incrementAndGet();
        List<ChatMessage> messages = request.messages();
        ChatMessage last = messages.getLast();
        boolean toolsOffered = request.toolSpecifications() != null && !request.toolSpecifications().isEmpty();
        if (toolsOffered && !(last instanceof ToolExecutionResultMessage)) {
            return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                    .id("call-1").name("web_search").arguments("{\"query\":\"weather in Chennai\"}").build())).build();
        }
        String text = last instanceof UserMessage u && u.hasSingleText() ? u.singleText()
                : last instanceof ToolExecutionResultMessage t ? "Based on the search: " + t.text()
                : String.valueOf(last);
        if (text.contains("Invoice Text")) {
            return reply("""
                    {"vendorName":"ACME Corp","customerName":"Jane Doe","invoiceNumber":"INV-001",
                     "totalAmount":123.45,"dueDate":"2026-01-31"}""");
        }
        if (text.contains("Write HTML/CSS code")) {
            return reply("```html\n<h1>Calc</h1>\n```\n```css\nh1{color:red}\n```");
        }
        return reply("echo: " + text.substring(0, Math.min(80, text.length())));
    }

    private static ChatResponse reply(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
