package com.andrewsselvaraj.langgraph.core;

import java.io.BufferedReader;
import java.io.PrintStream;

/** A workflow that, like its Python original, talks to the user in a terminal loop. */
public interface ConsoleWorkflow extends Workflow {

    void runConsole(BufferedReader in, PrintStream out) throws Exception;
}
