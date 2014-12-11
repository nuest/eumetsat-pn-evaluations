/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.solr.webapp;

import com.github.autermann.yaml.Yaml;
import com.github.autermann.yaml.YamlNode;
import eumetsat.pn.common.AbstractApp;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import eumetsat.pn.common.ISO2JSON;
import eumetsat.pn.common.util.SimpleRestClient;
import eumetsat.pn.common.util.SimpleRestClient.WebResponse;
import eumetsat.pn.solr.SolrFeeder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple example just showing some basic functionality
 *
 * @author danu
 */
public class SolrApp extends AbstractApp {

    private static final Logger log = LoggerFactory.getLogger(SolrApp.class);
    private SolrServer server;

    public SolrApp() {
        this(false);
    }

    public SolrApp(boolean servletContainer) {
        super(servletContainer);
    }

    public SolrApp(SolrServer server) {
        super();
        this.server = server;
    }

    public SolrApp(String configFile) {
        super(configFile);
    }

    @Override
    protected Map<String, Object> describeProduct(String id) throws MalformedURLException, ParseException {
        Map<String, Object> data = new HashMap<>();

        //create the url with the id passed in argument
        URL url = new URL(this.productEndpointUrlString + id);

        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, String> params = new HashMap<>();
        String body = null;
        boolean debug = true;

//        WebResponse response = rClient.doGetRequest(url, headers, params,
//                body, debug);
//        log.trace("response = " + response);
//
//        JSONParser parser = new JSONParser();
//
//        JSONObject jsObj = (JSONObject) parser.parse(response.body);
//
//        Map<String, Object> identificationInfo = ((Map<String, Object>) (((Map<String, Object>) jsObj.get("_source")).get("identificationInfo")));
        data.put("id", id);
//        data.put("title", identificationInfo.get("title"));
//        data.put("abstract", identificationInfo.get("abstract"));

        return data;
    }

    @Override
    protected Map<String, Object> search(String searchTerms, String filterString, int from, int size) {
        Map<String, Object> data = new HashMap<>();
        // put "session" parameters here rightaway so it can be used in template even when empty result
        data.put("search_terms", searchTerms);
        data.put("filter_terms", filterString);

        URL url;
        try {
            url = new URL(searchEndpointUrlString);
        } catch (MalformedURLException e) {
            log.error("Search enpoint URL malformed: {}", e.getMessage());
            addMessage(data, MessageLevel.danger, "Search endpoint URL is malformed: " + e.getMessage());
            return data;
        }

        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, String> params = new HashMap<>();

        List<Map<String, String>> resHits = new ArrayList<>();

        log.trace("solr: {}", "...");

        //measure elapsed time
        Stopwatch stopwatch = Stopwatch.createStarted();

        // query Solr
        // transform JSON to data map for template engine
        stopwatch.stop();

        data.put("elapsed", (double) (stopwatch.elapsed(TimeUnit.MILLISECONDS)) / (double) 1000);

        return data;
    }

    @Override
    protected String getConfigBasename() {
        return "solr";
    }

    public static void main(String[] args) throws SolrServerException, IOException, URISyntaxException {
        YamlNode n;
        try (InputStream fis = ISO2JSON.class.getResourceAsStream("/app.yml")) {
            n = new Yaml().load(fis);
        }
        YamlNode conf = n.get("solr");

        /*
         YamlNode n;
         try (InputStream fis = ISO2JSON.class.getResourceAsStream("/feederconfig.yml")) {
         n = new Yaml().load(fis);
         }

         YamlNode conf = n.get("solr").get("feeder");
         String collection = conf.get("endpoint").get("collection").asTextValue();

         Path home = Paths.get(SolrApp.class.getResource("/").toURI());
         File xml = home.resolve("solr.xml").toFile();
         log.info("Starting embedded Solr with config file {} at {}", xml, home);
         // https://cwiki.apache.org/confluence/display/solr/Using+SolrJ
         // https://github.com/apache/lucene-solr/blob/trunk/solr/solrj/src/test/org/apache/solr/client/solrj/embedded/TestEmbeddedSolrServer.java
         // https://github.com/apache/lucene-solr/blob/trunk/solr/solrj/src/test/org/apache/solr/client/solrj/embedded/AbstractEmbeddedSolrServerTestCase.java
         CoreContainer cores = CoreContainer.createAndLoad(home.toAbsolutePath().toString(), xml);
         EmbeddedSolrServer server = new EmbeddedSolrServer(cores, collection);
         log.info("Started embedded Solr (ping status: {}): {}", server.ping().getResponse().get("status"), server);
        
         try {
         SolrFeeder feeder = new SolrFeeder(server);
         feeder.transformAndIndex();
         } catch (IOException e) {
         log.error("Error feeding to ES", e);
         }
         */
        if (conf.get("feedOnStart").booleanValue()) {
            try {
                SolrFeeder feeder = new SolrFeeder();
                feeder.transformAndIndex();
            } catch (IOException e) {
                log.error("Error feeding to ES", e);
            }
        }

        // ISSUE 1: the embedded Solr server does not expose an HTTP interface
//        SolrApp app = new SolrApp(server);
        // ISSUE 2: Version conflict between Jetty of Sparkjava and Jetty of Solr
        SolrApp app = new SolrApp();
        app.run();
    }

    @Override
    protected void feed() throws IOException {
        SolrFeeder feeder = new SolrFeeder();
        feeder.transformAndIndex();
    }
}
