package com.andrewsselvaraj.langgraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.serializer.std.SpringAIStateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Shared graph state: the Python {@code messages: Annotated[list, add_messages]} channel (holding
 * Spring AI {@link Message}s) plus free-form keys ({@code summary}, {@code draft}, ...). Keys other
 * than {@code messages} have no reducer, so a node's update simply replaces them, exactly like plain
 * TypedDict fields in LangGraph.
 */
public class GraphState extends MessagesState<Message> {

    public static final String MESSAGES = MESSAGES_STATE;

    /** {@code add_messages}: append, keeping repeats (the same question can be asked twice). */
    public static final Map<String, Channel<?>> SCHEMA =
            Map.of(MESSAGES, Channels.appenderWithDuplicate(ArrayList::new));

    public GraphState(Map<String, Object> data) {
        super(data);
    }

    /** New graph whose state can be checkpointed (Spring AI messages are serialized properly). */
    public static StateGraph<GraphState> newGraph() {
        return new StateGraph<>(SCHEMA, new SpringAIStateSerializer<>(GraphState::new));
    }

    public String text(String key) {
        return this.<Object>value(key).map(String::valueOf).orElse("");
    }

    public boolean flag(String key) {
        return this.<Boolean>value(key).orElse(false);
    }

    public <T> List<T> list(String key) {
        return this.<List<T>>value(key).orElse(List.of());
    }

    /** Text of the most recent message, like {@code state["messages"][-1].content}. */
    public String lastText() {
        return lastMessage().map(GraphState::textOf).orElse("");
    }

    /** Text of the most recent user message. */
    public String lastUserText() {
        List<Message> msgs = messages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) instanceof UserMessage u) {
                return textOf(u);
            }
        }
        return "";
    }

    public static String textOf(Message message) {
        if (message instanceof ToolResponseMessage tool) {
            return tool.getResponses().stream().map(ToolResponseMessage.ToolResponse::responseData)
                    .collect(Collectors.joining("\n"));
        }
        return message.getText() == null ? "" : message.getText();
    }
}
