package com.andrewsselvaraj.langgraph.workflows;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.andrewsselvaraj.langgraph.config.AppProperties;

/**
 * Minimal WordPress XML-RPC client: just {@code wp.newPost}, which is what
 * {@code wordpress_xmlrpc.methods.posts.NewPost} calls in the Python project.
 */
@Component
public class WordPressClient {

    private final AppProperties.Wordpress props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public WordPressClient(AppProperties props) {
        this.props = props.wordpress();
    }

    public boolean isConfigured() {
        return props.configured();
    }

    /** Publishes the post and returns the new post id. */
    public String newPost(String title, String htmlContent) throws Exception {
        String body = """
                <?xml version="1.0"?>
                <methodCall>
                  <methodName>wp.newPost</methodName>
                  <params>
                    <param><value><int>0</int></value></param>
                    <param><value><string>%s</string></value></param>
                    <param><value><string>%s</string></value></param>
                    <param><value><struct>
                      <member><name>post_title</name><value><string>%s</string></value></member>
                      <member><name>post_content</name><value><string>%s</string></value></member>
                      <member><name>post_status</name><value><string>publish</string></value></member>
                    </struct></value></param>
                  </params>
                </methodCall>
                """.formatted(xml(props.username()), xml(props.password()), xml(title), xml(htmlContent));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(props.url()))
                        .header("Content-Type", "text/xml; charset=UTF-8")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String reply = response.body();
        if (response.statusCode() >= 400 || reply.contains("<fault>")) {
            throw new IllegalStateException("WordPress rejected the post (HTTP " + response.statusCode() + "): " + reply);
        }
        return reply.replaceAll("(?s).*<string>([^<]*)</string>.*", "$1").trim();
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
