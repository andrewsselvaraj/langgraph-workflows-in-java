package com.andrewsselvaraj.langgraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Shared graph state: the Python {@code messages: Annotated[list, add_messages]}
 * channel plus free-form keys ({@code summary}, {@code draft}, ...). Keys other than
 * {@code messages} have no reducer, so a node's update simply replaces them,
 * exactly like plain TypedDict fields in LangGraph.
 */
public class GraphState extends MessagesState<ChatMessage> {

    public static final String MESSAGES = MESSAGES_STATE;

    /** {@code add_messages}: append, keeping repeats (the same question can be asked twice). */
    public static final Map<String, Channel<?>> SCHEMA =
            Map.of(MESSAGES, Channels.appenderWithDuplicate(ArrayList::new));

    public GraphState(Map<String, Object> data) {
        super(data);
    }

    /** New graph whose state can be checkpointed (LangChain4j messages are serialized properly). */
    public static StateGraph<GraphState> newGraph() {
        return new StateGraph<>(SCHEMA, new LC4jStateSerializer<>(GraphState::new));
    }

    public String text(String key) {
        return this.<Object>value(key).map(String::valueOf).orElse("");
    }

    public boolean flag(String key) {
        return this.<Boolean>value(key).orElse(false);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> list(String key) {
        return this.<List<T>>value(key).orElse(List.of());
    }

    /** Text of the most recent message, like {@code state["messages"][-1].content}. */
    public String lastText() {
        return lastMessage().map(GraphState::textOf).orElse("");
    }

    /** Text of the most recent user message. */
    public String lastUserText() {
        List<ChatMessage> msgs = messages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) instanceof UserMessage u) {
                return textOf(u);
            }
        }
        return "";
    }

    public static String textOf(ChatMessage message) {
        return switch (message) {
            case UserMessage m -> m.hasSingleText() ? m.singleText() : m.contents().toString();
            case AiMessage m -> m.text() == null ? "" : m.text();
            case SystemMessage m -> m.text();
            case ToolExecutionResultMessage m -> m.text();
            default -> message.toString();
        };
    }
}
