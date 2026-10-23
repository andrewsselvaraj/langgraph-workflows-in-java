package com.andrewsselvaraj.langgraph;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point.
 *
 * <ul>
 *   <li>No arguments: starts the REST API (see {@code WorkflowController}).</li>
 *   <li>{@code --workflow=<id>}: runs a single workflow on the console and exits,
 *       just like {@code python main.py} in the original project.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LangGraphWorkflowsApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LangGraphWorkflowsApplication.class);
        if (Arrays.stream(args).anyMatch(a -> a.startsWith("--workflow"))) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
