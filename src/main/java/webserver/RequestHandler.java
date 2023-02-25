package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
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
            log.debug(line);
            if (line == null) {
                return;
            }

            String[] tokens = line.split(" ");
            String url = tokens[1];

            Map<String, String> headerMap = createHeaderMap(br);

            String requestPath = "/index.html";

            if (url.startsWith("/user/create")) {
                String body = IOUtils.readData(br, Integer.parseInt(headerMap.get("Content-Length")));
                log.debug("body: {}", body);

                Map<String, String> params = HttpRequestUtils.parseQueryString(body);
                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                log.debug("Create user: {}", user);

                DataBase.addUser(user);

                DataOutputStream dos = new DataOutputStream(out);
                response302LoginSuccessHeader(dos);

            } else if ("/user/login".equals(url)) {
                String body = IOUtils.readData(br, Integer.parseInt(headerMap.get("Content-Length")));
                Map<String, String> params = HttpRequestUtils.parseQueryString(body);

                User user = DataBase.findUserById(params.getOrDefault("userId", ""));
                if (user == null) {
                    responseResource(out, "/user/login_failed.html");
                }

                if (user.getPassword().equals(params.getOrDefault("password", ""))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302LoginSuccessHeader(dos);
                } else {
                    responseResource(out, "/user/login_failed.html");
                }

            } else if (url.equals("/")) {
                requestPath = "/index.html";
                byte[] body = Files.readAllBytes(new File("./webapp" + requestPath).toPath());
                DataOutputStream dos = new DataOutputStream(out);
                response200Header(dos, body.length);
                responseBody(dos, body);

            } else {
                responseResource(out, url);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private Map<String, String> createHeaderMap(BufferedReader br) throws IOException {
        Map<String, String> headerMap = new HashMap<>();

        String line;

        while (!"".equals(line = br.readLine())) {
            String[] split = line.split(":", 2);
            headerMap.put(split[0], split[1].trim());
        }

        return headerMap;
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

    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + url + " \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302LoginSuccessHeader(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Set-Cookie: logined=true \r\n");
            dos.writeBytes("Location: /index.html \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseResource(OutputStream out, String url) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body =  Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length);
        responseBody(dos, body);
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
