/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.solr.webapp;

import com.github.autermann.yaml.Yaml;
import com.github.autermann.yaml.YamlNode;
import com.google.common.base.Joiner;
import eumetsat.pn.common.AbstractApp;
import com.google.common.base.Stopwatch;
import eumetsat.pn.common.ISO2JSON;
import eumetsat.pn.solr.SolrFeeder;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
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

    private String solrServerEndpoint;
    private final HttpSolrServer solr;

    public SolrApp() {
        this(false);
    }

    public SolrApp(boolean servletContainer) {
        this(servletContainer, DEFAULT_CONFIG_FILE);
    }

    public SolrApp(String configFile) {
        this(false, configFile);
    }

    public SolrApp(boolean servletContainer, String configFile) {
        super(servletContainer, configFile);

        String coll = config.get("searchendpoint").get("collection").asTextValue();
        solrServerEndpoint = searchEndpointBaseUrl + coll;
        productEndpointUrlString = config.get("searchendpoint").get("paths").get("productinfo").asTextValue();

        solr = new HttpSolrServer(solrServerEndpoint);
        try {
            log.debug("Solr server: {} | ping: {} / {}", solr, solr.ping().getStatus(), solr.ping().getResponse().get("status"));
        } catch (SolrServerException | IOException e) {
            log.error("Could not connect to Solr", e);
        }

        log.info("NEW {}", this.toString());
    }

    @Override
    protected Map<String, Object> describeProduct(String id) throws MalformedURLException, ParseException {
        Map<String, Object> data = new HashMap<>();
        //URL url = new URL(this.productEndpointUrlString + "?" + id);

        SolrQuery query = new SolrQuery();
        query.setRequestHandler(productEndpointUrlString);
        query.set("id", id);
        query.setFields("id", "title", "description");
        log.trace("Solr query: {}", query);

        try {
            QueryResponse response = solr.query(query);

            SolrDocument result = (SolrDocument) response.getResponse().get("doc");
            log.trace("Result document: {}", result);

            data.put("id", result.getFieldValue("id"));
            data.put("title", result.getFieldValue("title"));
            data.put("abstract", result.getFieldValue("description"));
        } catch (SolrServerException e) {
            log.error("Error querying Solr", e);
            errorResponse(e);
        }
        return data;
    }

    @Override
    protected Map<String, Object> search(String searchTerms, String filterString, int from, int size
    ) {
        Map<String, Object> data = new HashMap<>();
        // put "session" parameters here rightaway so it can be used in template even when empty result
        data.put("search_terms", searchTerms);
        data.put("filter_terms", filterString);

        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, String> params = new HashMap<>();

        try {
            SolrQuery query = new SolrQuery();

            query.setQuery(searchTerms);
            query.setStart(from);
            query.setRows(size);
            query.setFields("*", "score");

            log.trace("Solr query: {}", query);
            Stopwatch stopwatch = Stopwatch.createStarted();
            QueryResponse response = solr.query(query);

            if (response == null) {
                log.error("Response from {} is null!", this.name);
                data.put("total_hits", 0);
                data = addMessage(data, MessageLevel.danger, "Response is null from " + this.name);
            } else {
                log.trace("Got response: {}", response);

                if (response.getStatus() == 0) {
                    List<Map<String, Object>> resHits = new ArrayList<>();
                    SolrDocumentList results = response.getResults();

                    data.put("total_hits", results.getNumFound());
                    data.put("max_score", results.getMaxScore());
                    Map<String, Object> pagination = computePaginationParams(((Long) (data.get("total_hits"))).intValue(), from);
                    data.put("pagination", pagination);

                    for (SolrDocument result : results) {
                        HashMap<String, Object> resHit = new HashMap<>();

                        resHit.put("id", result.getFieldValue("id"));
                        resHit.put("score", String.format("%.4g", result.getFieldValue("score")));

                        resHit.put("abstract", result.get("description"));
                        resHit.put("title", result.get("title"));
                        resHit.put("keywords", Joiner.on(", ").join((Collection<String>) result.get("keywords")));
                        resHit.put("satellite", result.get("satellite_s"));
                        resHit.put("thumbnail", result.get("thumbnail_s"));
                        resHit.put("status", result.get("status_s"));

                        resHits.add(resHit);
                    }

                    data.put("hits", resHits);
                } else { // non-OK resonse
                    log.error("Received non-200 response: {}", response);
                    data = addMessage(data, MessageLevel.danger, "Non 200 response: " + response.toString());
                }
            }

            stopwatch.stop();

            data.put("elapsed", (double) (stopwatch.elapsed(TimeUnit.MILLISECONDS)) / (double) 1000);
            log.trace("Prepared data for template: {}", data);
        } catch (SolrServerException e) {
            log.error("Error querying Solr", e);
            errorResponse(e);
        }

        return data;
    }

    @Override
    protected String getConfigBasename() {
        return "solr";
    }

    public static void main(String[] args) throws SolrServerException, IOException, URISyntaxException {
        YamlNode n;

        try (InputStream fis = ISO2JSON.class
                .getResourceAsStream("/app.yml")) {
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SolrApp [solr endpoint = ").append(this.solrServerEndpoint);
        sb.append("]");
        return sb.toString();
    }

}
