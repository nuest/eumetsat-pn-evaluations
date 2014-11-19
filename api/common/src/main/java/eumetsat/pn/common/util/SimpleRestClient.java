/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common.util;

/**
 * 2013 - Jeffrey Robbins SimpleRestClient v2 - Building a REST client from the
 * standard java libs This is by no means an example on the best way to do
 * things, just a bit of fun in learning Comments on making it better welcomed
 *
 * Licensed under the Apache License, V2.0
 */
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Scanner;

/**
 * origin: http://jeff.robbins.ws/code/simple-java-rest-client
 */
public class SimpleRestClient {

    public WebResponse doGetRequest(URL url, HashMap<String, String> headers, HashMap<String, String> params, String body, boolean debug) {
        GET get = new GET(url, headers, params, body);
        return sendRequest(get, debug);
    }

    public WebResponse doPostRequest(URL url, HashMap<String, String> headers, String body, boolean debug) {
        POST post = new POST(url, headers, body);
        return sendRequest(post, debug);
    }

    private WebResponse sendRequest(Request request, boolean debug) {
        HttpURLConnection conn = null;
        WebResponse response = null;
        long time = 0;

        try {
            conn = (HttpURLConnection) request.getUrl().openConnection();

            if (request.headers != null) {
                for (String header : request.headers.keySet()) {
                    conn.addRequestProperty(header, request.headers.get(header));
                }
            }

            time = System.currentTimeMillis();

            if (request instanceof POST) {

                byte[] payload = ((POST) request).body.getBytes();

                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                conn.getOutputStream().write(payload);
            }

            if (request instanceof GET) {
                //encode body only if there is one. otherwise send GET request without body
                if (((GET) request).body != null) {
                    byte[] payload = ((GET) request).body.getBytes();

                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(payload.length);
                    conn.getOutputStream().write(payload);
                }
            }

            int status = conn.getResponseCode();

            if (status != HttpURLConnection.HTTP_OK) {
                response = new WebResponse(status, conn.getResponseMessage());
            } else {
                response = new WebResponse(status, readInputStream(conn.getInputStream()));
            }

            response.time = System.currentTimeMillis() - time;
            if (debug) {
                dumpRequest(request, response);
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return response;
    }

    private static String readInputStream(InputStream is) throws IOException {
        Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * Convenience method to output everything about the request
     */
    public void dumpRequest(Request req, WebResponse resp)
            throws MalformedURLException {
        StringBuilder sb = new StringBuilder();
        sb.append("=> Dumping request information:");
        sb.append("\n").append("======================= REQUEST ==========================");
        sb.append("\n==> ").append("URL: ").append(req.getUrl());
        sb.append("\n==> ").append("Method: ").append((req instanceof POST ? "POST" : "GET"));

        if (req.headers != null) {
            for (String header : req.headers.keySet()) {
                sb.append("\n===> ").append("Header: ").append(header).append(": ").append(req.headers.get(header));
            }
        }

        if (req instanceof GET && ((GET) req).params != null) {
            for (String param : ((GET) req).params.keySet()) {
                sb.append("\n===> ").append("Param: ").append(param).append("=").append(((GET) req).params.get(param));
            }
        }

        if (req instanceof POST) {
            sb.append("\n==> ").append("Request body: ").append(((POST) req).body);
        }

        sb.append("\n").append("======================= RESPONSE =========================");

        sb.append("\n==> ").append("Round trip time: ").append(resp.time).append(" ms");
        sb.append("\n==> ").append("Response status: ").append(resp.status);
        sb.append("\n==> ").append("Response body:\n").append(resp.body);

        sb.append("\n==========================================================");

        System.out.println(sb.toString());
    }

    private class Request {

        public URL baseUrl;
        public HashMap<String, String> headers;

        public Request(URL baseUrl, HashMap<String, String> headers) {
            this.baseUrl = baseUrl;
            this.headers = headers;
        }

        public URL getUrl() throws MalformedURLException {
            return baseUrl;
        }
    }

    private class POST extends Request {

        public String body;

        public POST(URL baseUrl, HashMap<String, String> headers, String body) {
            super(baseUrl, headers);
            this.body = body;
        }
    }

    private class GET extends Request {

        public String body;
        public HashMap<String, String> params;

        public GET(URL baseUrl, HashMap<String, String> headers, HashMap<String, String> params, String body) {
            super(baseUrl, headers);
            this.params = params;
            this.body = body;
        }

        @Override
        public URL getUrl() throws MalformedURLException {
            StringBuilder sb = new StringBuilder(baseUrl.toString());
            if (params != null && params.size() > 0) {
                sb.append(createParamString());
            }

            return new URL(sb.toString());
        }

        private String createParamString() {
            StringBuilder sb = new StringBuilder();
            if (params != null && params.size() > 0) {
                sb.append("?");
                //TODO: Need to encode the paramater values
                for (String param : params.keySet()) {
                    sb.append(param).append("=").append(params.get(param)).append("&");
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        }
    }

    public class WebResponse {

        public int status;
        public String body;
        public long time;

        protected WebResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
