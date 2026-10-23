package com.andrewsselvaraj.langgraph;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Offline stand-in for the LLM so the graphs can be tested without an API key.
 * Asks for the web_search tool once when tools are offered, returns invoice JSON for the
 * invoice prompt, and otherwise echoes the start of the last message.
 */
public class FakeChatModel implements ChatModel {

    public final AtomicInteger calls = new AtomicInteger();

    @Override
    public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        List<Message> messages = prompt.getInstructions();
        Message last = messages.getLast();
        boolean toolsOffered = prompt.getOptions() instanceof ToolCallingChatOptions o
                && o.getToolCallbacks() != null && !o.getToolCallbacks().isEmpty();
        if (toolsOffered && !(last instanceof ToolResponseMessage)) {
            return reply(AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "web_search",
                            "{\"query\":\"weather in Chennai\"}")))
                    .build());
        }
        String text = last instanceof UserMessage u ? u.getText()
                : last instanceof ToolResponseMessage t ? "Based on the search: " + t.getResponses().getFirst().responseData()
                : last.getText();
        if (text.contains("Invoice Text")) {
            return reply(new AssistantMessage("""
                    {"vendor_name":"ACME Corp","customer_name":"Jane Doe","invoice_number":"INV-001",
                     "total_amount":123.45,"due_date":"2026-01-31"}"""));
        }
        if (text.contains("Write HTML/CSS code")) {
            return reply(new AssistantMessage("```html\n<h1>Calc</h1>\n```\n```css\nh1{color:red}\n```"));
        }
        return reply(new AssistantMessage("echo: " + text.substring(0, Math.min(80, text.length()))));
    }

    private static ChatResponse reply(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}
