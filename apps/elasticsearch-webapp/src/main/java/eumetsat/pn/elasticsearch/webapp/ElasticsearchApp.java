/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.elasticsearch.webapp;

import com.github.autermann.yaml.YamlNode;
import eumetsat.pn.common.AbstractApp;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import eumetsat.pn.common.util.SimpleRestClient;
import eumetsat.pn.common.util.SimpleRestClient.WebResponse;
import eumetsat.pn.elasticsearch.ElasticsearchFeeder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.SimpleQueryStringBuilder;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import static org.elasticsearch.rest.RestStatus.OK;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
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
public class ElasticsearchApp extends AbstractApp {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchApp.class);

    private final SimpleRestClient rClient = new SimpleRestClient();

    public ElasticsearchApp() {
        super();
    }

    public ElasticsearchApp(boolean servletContainer) {
        super(servletContainer);
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

        WebResponse response = rClient.doGetRequest(url, headers, params,
                body, debug);

        log.trace("response = " + response);

        JSONParser parser = new JSONParser();

        JSONObject jsObj = (JSONObject) parser.parse(response.body);

        Map<String, Object> identificationInfo = ((Map<String, Object>) (((Map<String, Object>) jsObj.get("_source")).get("identificationInfo")));

        data.put("id", id);
        data.put("title", identificationInfo.get("title"));
        if (identificationInfo.containsKey("keywords")) {
            data.put("abstract", identificationInfo.get("abstract"));
        }
        if (identificationInfo.containsKey("keywords")) {
            JSONArray keywords = (JSONArray) identificationInfo.get("keywords");
            data.put("keywords", Joiner.on(", ").join(keywords.iterator()));
        }
        if (identificationInfo.containsKey("thumbnail")) {
            data.put("thumbnail", identificationInfo.get("thumbnail").toString());
        }
        if (identificationInfo.containsKey("status")) {
            data.put("status", identificationInfo.get("status").toString());
        }
        if (identificationInfo.containsKey("satellite")) {
            data.put("satellite", identificationInfo.get("satellite").toString());
        }

        return data;
    }

    @Override
    protected Map<String, Object> search(String searchTerms, String filterString, int from, int size) {
        Map<String, Object> data = new HashMap<>();
        // put "session" parameters here rightaway so it can be used in template even when empty result
        data.put("search_terms", searchTerms);
        data.put("filter_terms", filterString);

        YamlNode searchEndpoint = config.get("searchendpoint");
        InetSocketTransportAddress address = new InetSocketTransportAddress(searchEndpoint.get("host").asTextValue(), searchEndpoint.get("port").asIntValue());
        TransportClient client = new TransportClient().addTransportAddress(address);
        log.debug("Using client {}", client);

        SearchRequestBuilder requestBuilder = client.prepareSearch(searchEndpoint.get("index").asTextValue()).setTypes(searchEndpoint.get("type").asTextValue());

        List<Map<String, String>> resHits = new ArrayList<>();
        Map<String, String> resHit = null;

        Multimap<String, String> filterTermsMap = parseFiltersTerms(filterString);
        Set<String> hiddenFacets = new HashSet<>(); // to create the list of filters to hide

        List<FilterBuilder> filters = new ArrayList<>();
        FilterBuilder mustFilter = null;
        if (filterTermsMap.size() > 0) {
            for (String key : filterTermsMap.keySet()) {
                for (String term : filterTermsMap.get(key)) {
                    TermFilterBuilder filter = FilterBuilders.termFilter(FACETS2HIERACHYNAMES.get(key), term);
                    filters.add(filter);
                    // hide the facets that are used for filtering
                    hiddenFacets.add(key + ":" + term);
                }
            }

            mustFilter = FilterBuilders.boolFilter().must(filters.toArray(new FilterBuilder[filters.size()]));
        }

        int lengthOfTitle = 300;
        int lengthOfAbstract = 5000;
        int boostFactorTitle = 10;

        requestBuilder.setFrom(from).setSize(size);
        requestBuilder.addHighlightedField("identificationInfo.title", lengthOfTitle, 1);
        requestBuilder.addHighlightedField("identificationInfo.abstract", lengthOfAbstract, 1);
        requestBuilder.setHighlighterPreTags("<em><strong>");
        requestBuilder.setHighlighterPostTags("</strong></em>");

        TermsFacetBuilder satFacet = FacetBuilders.termsFacet("satellites").field("hierarchyNames.satellite").size(5);
        requestBuilder.addFacet(satFacet);
        TermsFacetBuilder instFacet = FacetBuilders.termsFacet("instruments").field("hierarchyNames.instrument").size(5);
        requestBuilder.addFacet(instFacet);
        TermsFacetBuilder catFacet = FacetBuilders.termsFacet("categories").field("hierarchyNames.category").size(5);
        requestBuilder.addFacet(catFacet);
        TermsFacetBuilder sbaFacet = FacetBuilders.termsFacet("societal Benefit Area").field("hierarchyNames.societalBenefitArea").size(5);
        requestBuilder.addFacet(sbaFacet);
        TermsFacetBuilder disFacet = FacetBuilders.termsFacet("distribution").field("hierarchyNames.distribution").size(5);
        requestBuilder.addFacet(disFacet);

        SimpleQueryStringBuilder queryBuilder = QueryBuilders.simpleQueryString(searchTerms);
        FilteredQueryBuilder filteredQuery = QueryBuilders.filteredQuery(queryBuilder, mustFilter);
        queryBuilder.field("identificationInfo.title", boostFactorTitle);
        queryBuilder.field("identificationInfo.abstract", 2);
        
        requestBuilder.setQuery(filteredQuery);

        log.debug("Elasticsearch request: {}", requestBuilder.toString());

        //measure elapsed time
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        SearchResponse response = requestBuilder.execute().actionGet();

        if (response == null) {
            log.error("Response from {} is null!", this.name);
            data.put("total_hits", 0);
            data = addMessage(data, MessageLevel.danger, "Response is null from " + this.name);
        } else {
            log.trace("Got response: {}", response);

            if (response.status() == OK) {
                JSONParser parser = new JSONParser();
                JSONObject jsObj;
                try {
                    jsObj = (JSONObject) parser.parse(response.body);
                } catch (ParseException e) {
                    log.error("Could not parse search server response: {}", e);
                    addMessage(data, MessageLevel.danger, "Could not parse server response: " + e.getMessage());
                    return data;
                }

                Long hitcount = (Long) ((Map<?, ?>) jsObj.get("hits")).get("total");
                data.put("total_hits", hitcount);
                if (hitcount < 1) {
                    addMessage(data, MessageLevel.info, "No results found!");
                }

                // compute the pagination information to create the pagination bar
                Map<String, Object> pagination = computePaginationParams(hitcount.intValue(), from);
                data.put("pagination", pagination);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> hits = (List<Map<String, Object>>) ((Map<?, ?>) jsObj.get("hits")).get("hits");

                //to get the highlight
                Map<?, ?> highlight = null;
                for (Map<String, Object> hit : hits) {
                    resHit = new HashMap<>();

                    resHit.put("id", (String) hit.get("_id"));
                    resHit.put("score", String.format("%.4g%n", ((Double) hit.get("_score"))));

                    // can have or not title or abstract
                    // strategy. If it doesn't have an abstract or a title match then take it from the _source
                    highlight = (Map<?, ?>) hit.get("highlight");

                    if (highlight.containsKey("identificationInfo.title")) {
                        resHit.put("title", (String) ((JSONArray) highlight.get("identificationInfo.title")).get(0));
                    } else {
                        resHit.put("title", ((String) (((Map<?, ?>) (((Map<?, ?>) hit.get("_source")).get("identificationInfo"))).get("title"))));
                    }

                    if (highlight.containsKey("identificationInfo.abstract")) {
                        resHit.put("abstract", (String) ((JSONArray) highlight.get("identificationInfo.abstract")).get(0));
                    } else {
                        resHit.put("abstract", ((String) (((Map<?, ?>) (((Map<?, ?>) hit.get("_source")).get("identificationInfo"))).get("abstract"))));
                    }

                    JSONObject info = (JSONObject) ((JSONObject) hit.get("_source")).get("identificationInfo");
                    JSONArray keywords = (JSONArray) info.get("keywords");
                    resHit.put("keywords", Joiner.on(", ").join(keywords.iterator()));

                    resHit.put("thumbnail", info.get("thumbnail").toString());
                    resHit.put("status", info.get("status").toString());

                    resHits.add(resHit);
                }

                data.put("hits", resHits);

                data.put("facets", jsObj.get("facets"));

                data.put("tohide", hiddenFacets);

            } else { // non-200 resonse
                log.error("Received non-200 response: {}", response);
                data = addMessage(data, MessageLevel.danger, "Non 200 response: " + response.toString());
            }
        }

        stopwatch.stop();

        data.put("elapsed", (double) (stopwatch.elapsed(TimeUnit.MILLISECONDS)) / (double) 1000);

        return data;
    }

    @Override
    protected String getConfigBasename() {
        return "elasticsearch";
    }

    public static void main(String[] args) {
//        startEmbedded();
//        feedIfEmpty();

        ElasticsearchApp app = new ElasticsearchApp();
        app.run();
    }

    private static void startEmbedded() {
        log.info("Starting embedded Elasticsearch...");
        // http://blog.trifork.com/2012/09/13/elasticsearch-beyond-big-data-running-elasticsearch-embedded/
        Settings settings = ImmutableSettings.settingsBuilder().loadFromClasspath("elasticsearch.yml").build();
        Node node = NodeBuilder.nodeBuilder().settings(settings).build();
        node.start();
        log.info("Embedded Elasticsearch started.");
    }

    private static void feedIfEmpty() {
        SimpleRestClient client = new SimpleRestClient();

        try {
            URL url = new URL("http://localhost:9200/eumetsat-catalogue/product/EO:EUM:DAT:INFO:LFDI");

            WebResponse response = client.doGetRequest(url, new HashMap<String, String>(), new HashMap<String, String>(),
                    null, true);
            if (response.status == 404) {
                ElasticsearchFeeder feeder = new ElasticsearchFeeder();
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
        ElasticsearchFeeder feeder = new ElasticsearchFeeder();
        feeder.transformAndIndex();
    }

    @Override
    protected void feed(Path configFile) throws IOException {
        ElasticsearchFeeder feeder = new ElasticsearchFeeder(configFile);
        feeder.transformAndIndex();
    }
}
