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
import com.google.common.collect.Multimap;
import eumetsat.pn.common.ISO2JSON;
import eumetsat.pn.common.util.SimpleRestClient;
import eumetsat.pn.solr.SolrFeeder;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.json.simple.JSONObject;
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
        query.setFields("*");
        log.trace("Solr query: {}", query);

        try {
            QueryResponse response = solr.query(query);

            SolrDocument result = (SolrDocument) response.getResponse().get("doc");
            log.trace("Result document: {}", result);

            data.put("id", result.getFieldValue("id"));
            data.put("title", result.getFieldValue("title"));
            data.put("abstract", result.getFieldValue("description"));

            if (result.getFieldValue("thumbnail_s") != null) {
                data.put("thumbnail", result.getFieldValue("thumbnail_s"));
            }
            if (result.getFieldValue("status_s") != null) {
                data.put("status", result.getFieldValue("status_s"));
            }
            if (result.getFieldValue("satellite_s") != null) {
                data.put("satellite", result.get("satellite_s"));
            }
            if (result.getFieldValue("keywords") != null) {
                data.put("keywords", Joiner.on(", ").join((Collection<String>) result.get("keywords")));
            }
            if (result.getFieldValue("distribution_ss") != null) {
                data.put("distribution", Joiner.on(", ").join((Collection<String>) result.get("distribution_ss")));
            }
            if (result.getFieldValue("category") != null) {
                data.put("category", Joiner.on(", ").join((Collection<String>) result.getFieldValue("category")));
            }
            if (result.getFieldValue("instrument_s") != null) {
                data.put("instrument", result.getFieldValue("instrument_s"));
            }
            if (result.getFieldValue("boundingbox") != null) {
                data.put("boundingbox", result.getFieldValue("boundingbox"));
            }
            if (result.getFieldValue("address_s") != null) {
                data.put("address", result.getFieldValue("address_s"));
            }
            if (result.getFieldValue("email_s") != null) {
                data.put("email", result.getFieldValue("email_s"));
            }
            if (result.getFieldValue("societalBenefitArea_ss") != null) {
                data.put("sba", Joiner.on(", ").join((Collection<String>) result.getFieldValue("societalBenefitArea_ss")));
            }
            if (result.getFieldValue("xmldoc") != null) {
                data.put("xmldoc", result.getFieldValue("xmldoc"));
            }
        } catch (SolrServerException e) {
            log.error("Error querying Solr", e);
            errorResponse(e);
        }
        return data;
    }

    @Override
    protected Map<String, Object> search(String searchTerms, String filterString, int from, int size) {
        Map<String, Object> data = new HashMap<>();
        // put "session" parameters here rightaway so it can be used in template even when empty result
        data.put("search_terms", searchTerms == null ? "*:*" : searchTerms);
        data.put("filter_terms", filterString == null ? "" : filterString);

        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, String> params = new HashMap<>();

        try {
            SolrQuery query = new SolrQuery();

            query.setQuery(searchTerms);
            query.setStart(from == -1 ? 0 : from);
            query.setRows(size);
            query.setFields("id", "title", "description", "thumbnail_s", "status_s", "score"); // "exclude" xmldoc
            query.setParam("qt", "edismax"); // probably default already
            
            // boosting
            query.setParam("qf", "title^10 description status^2 keywords");

            // set highlight, see also https://cwiki.apache.org/confluence/display/solr/Standard+Highlighter
            query.setHighlight(true).setHighlightSnippets(17).setHighlightFragsize(0); // http://wiki.apache.org/solr/HighlightingParameters
            query.setParam("hl.preserveMulti", "true"); // preserve non-matching keywords
            query.setParam("hl.fl", "id", "title", "description", "keywords"); // "*"); // select fields to highlight
            // override defaults:
            query.setParam("hl.simple.pre", "<em><strong>");
            query.setParam("hl.simple.post", "</strong></em>");

            // configure faceting, see also http://wiki.apache.org/solr/SolrFacetingOverview and http://wiki.apache.org/solr/Solrj and https://wiki.apache.org/solr/SimpleFacetParameters and 
            query.setFacet(true).setFacetLimit(4).setFacetMissing(true);
            // not in API, probably normally set in schema.xml:
            query.setParam("facet.field", "satellite_s", "instrument_s", "category", "societalBenefitArea_ss", "distribution_ss");

            // filtering
            Set<String> hiddenFacets = new HashSet<>(); // hiding no facets yet
            if (filterString != null && !filterString.isEmpty()) {
                Multimap<String, String> filterTermsMap = parseFiltersTerms(filterString);

                if (filterTermsMap.size() > 0) {
                    for (Map.Entry<String, String> entry : filterTermsMap.entries()) {
                        String filter = " +" + entry.getKey() + ":" + entry.getValue();
                        query.addFilterQuery(filter);

                        hiddenFacets.add(entry.getKey() + ":" + entry.getValue());
                    }
                }
            }
            data.put("tohide", hiddenFacets);

            log.debug("Solr query: {}", query);
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
                    Map<String, Map<String, List<String>>> highlights = response.getHighlighting();

                    data.put("total_hits", results.getNumFound());
                    data.put("max_score", results.getMaxScore());
                    Map<String, Object> pagination = computePaginationParams(((Long) (data.get("total_hits"))).intValue(), from);
                    data.put("pagination", pagination);

                    for (SolrDocument result : results) {
                        HashMap<String, Object> resHit = new HashMap<>();

                        String currentId = (String) result.getFieldValue("id");
                        Map<String, List<String>> currentHighlights = highlights.get(currentId);
                        resHit.put("id", currentId);
                        resHit.put("score", String.format("%.4g", result.getFieldValue("score")));

                        resHit.put("abstract", hightlightIfGiven(result, currentHighlights, "description"));

                        resHit.put("title", hightlightIfGiven(result, currentHighlights, "title"));
                        resHit.put("keywords", Joiner.on(", ").join((Collection<String>) hightlightIfGiven(result, currentHighlights, "keywords")));
                        resHit.put("satellite", result.get("satellite_s"));
                        resHit.put("thumbnail", result.get("thumbnail_s"));
                        resHit.put("status", result.get("status_s"));
                        resHit.put("distribution", result.get("distribution_ss"));

                        resHits.add(resHit);
                    }

                    data.put("hits", resHits);

                    // faceting information:
                    List<FacetField> facets = response.getFacetFields();
                    log.trace("Facets ({}): {}", facets.size(), facets);

                    //jsObj.get("facets").get("categories").get("terms") - then term und count
                    // convert to format of Elasticsearch:
                    Map<String, Object> facetsJson = new HashMap<>();
                    for (FacetField facet : facets) {
                        Map<String, Object> facetMap = new HashMap<>();
                        facetMap.put("total", facet.getValueCount());
                        List<Map<String, Object>> terms = new ArrayList<>();
                        for (Count count : facet.getValues()) {
                            if (count.getCount() > 0) {
                                Map<String, Object> termMap = new HashMap<>();
                                termMap.put("count", count.getCount());
                                termMap.put("term", count.getName() == null ? "N/A" : count.getName());
                                terms.add(termMap);
                            }
                        }
                        facetMap.put("terms", terms);
                        facetsJson.put(facet.getName(), facetMap);
                    }
                    data.put("facets", facetsJson);
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
            SolrFeeder feeder = new SolrFeeder();
            feeder.transformAndIndex();
        }

        if (conf.get("feedIfEmpty").booleanValue()) {
            feedIfEmpty();
        }

        // ISSUE 1: the embedded Solr server does not expose an HTTP interface
//        SolrApp app = new SolrApp(server);
        // ISSUE 2: Version conflict between Jetty of Sparkjava and Jetty of Solr
        SolrApp app = new SolrApp();
        app.run();
    }

    private static void feedIfEmpty() {
        SimpleRestClient client = new SimpleRestClient();

        try {
            URL url = new URL("http://localhost:8983/solr/eumetsat/get?id=EO:EUM:DAT:INFO:LFDI");

            SimpleRestClient.WebResponse response = client.doGetRequest(url, new HashMap<String, String>(), new HashMap<String, String>(),
                    null, true);
            if (response.body.contains("\"doc\":null")) { //response.status == 404) {
                SolrFeeder feeder = new SolrFeeder();
                feeder.transformAndIndex();
            } else {
                log.info("Not feeding, found {}", url);
            }
        } catch (Exception e) {
            log.error("Could not feed empty endpoint", e);
        }
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

    private Object hightlightIfGiven(SolrDocument doc, Map<String, List<String>> highlights, String field) {
        if (highlights != null && highlights.containsKey(field)) {
            List<String> result = highlights.get(field);
            if (result.size() == 1) {
                return result.get(0);
            } else {
                return (result);
            }
        } else {
            return doc.get(field);
        }
    }

}
