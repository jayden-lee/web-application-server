package webserver;

import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();
            if (line == null) {
                return;
            }

            log.debug(line);

            String[] tokens = line.split(" ");

            String url = tokens[1];
            String requestPath = "/index.html";
            if (url.startsWith("/user/create")) {
                int index = url.indexOf("?");
                Map<String, String> params = HttpRequestUtils.parseQueryString(url.substring(index + 1));
                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                log.debug("Create user: {}", user);

            } else if (url.equals("/")) {
                requestPath = "/index.html";

            } else {
                requestPath = url;
            }

            byte[] body = Files.readAllBytes(new File("./webapp" + requestPath).toPath());
            DataOutputStream dos = new DataOutputStream(out);
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private String requestHeader(InputStream in) {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String line;
        try {
            do {
                line = br.readLine();
                String[] tokens = line.split(" ");
                return tokens[1];
            } while (!"".equals(line));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
