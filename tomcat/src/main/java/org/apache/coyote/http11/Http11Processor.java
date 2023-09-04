package org.apache.coyote.http11;

import nextstep.jwp.exception.UncheckedServletException;
import org.apache.coyote.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (var inputStream = connection.getInputStream();
             var outputStream = connection.getOutputStream()) {

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            List<String> headers = new ArrayList<>();
            String header = "";
            while (!(header = bufferedReader.readLine()).equals("")) {
                headers.add(header);
            }
            String[] splitFirstLine = Objects.requireNonNull(headers.get(0)).split(" ");
            String requestMethod = splitFirstLine[0];
            String requestUri = splitFirstLine[1];

            String responseBody = renderResponseBody(requestMethod, requestUri);

            String requestAcceptHeader = findAcceptHeader(headers);
            String contentTypeHeader = getContentTypeHeaderFrom(requestAcceptHeader);

            List<String> responseHeaders = new ArrayList<>();
            responseHeaders.add("HTTP/1.1 200 OK ");
            responseHeaders.add(contentTypeHeader);
            responseHeaders.add("Content-Length: " + responseBody.getBytes().length + " ");
            String responseHeader = String.join("\r\n", responseHeaders);

            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

            var response = String.join("\r\n", responseHeader, "", responseBody);

            bufferedOutputStream.write(response.getBytes());
            bufferedOutputStream.flush();
        } catch (IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static String findAcceptHeader(List<String> headers) {
        return headers.stream()
                      .filter(it -> it.startsWith("Accept"))
                      .findFirst()
                      .orElseThrow(() -> new IllegalArgumentException("해당 헤더가 존재하지 않습니다."));
    }

    private static String getContentTypeHeaderFrom(String requestAcceptHeader) {
        String[] splitAcceptHeader = requestAcceptHeader.split(" ");
        String headerValue = splitAcceptHeader[1];
        String[] acceptTypes = headerValue.split(";");
        String[] splitAcceptTypes = acceptTypes[0].split(",");

        if (Arrays.asList(splitAcceptTypes).contains("text/css")) {
            return  "Content-Type: text/css;charset=utf-8 ";
        }
        return "Content-Type: text/html;charset=utf-8 ";
    }

    private String renderResponseBody(String requestMethod, String requestUri) {
        if (!requestMethod.equalsIgnoreCase("GET")) {
            throw new IllegalArgumentException("GET 요청만 처리 가능합니다.");
        }

        String fileName = "static" + requestUri;
        String path = getClass().getClassLoader().getResource(fileName).getPath();
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
            return lines.collect(Collectors.joining("\n", "", "\n"));
        } catch (IOException | UncheckedIOException e) {
            return "Hello world!";
        }
    }
}
