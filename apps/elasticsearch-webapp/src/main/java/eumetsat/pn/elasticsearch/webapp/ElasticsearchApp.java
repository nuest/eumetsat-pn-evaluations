/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.elasticsearch.webapp;

import eumetsat.pn.common.AbstractApp;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import eumetsat.pn.common.util.SimpleRestClient;
import eumetsat.pn.common.util.SimpleRestClient.WebResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    
    public ElasticsearchApp(String configFile) {
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

        WebResponse response = rClient.doGetRequest(url, headers, params,
                body, debug);

        log.trace("response = " + response);

        JSONParser parser = new JSONParser();

        JSONObject jsObj = (JSONObject) parser.parse(response.body);

        Map<String, Object> identificationInfo = ((Map<String, Object>) (((Map<String, Object>) jsObj.get("_source")).get("identificationInfo")));

        data.put("id", id);
        data.put("title", identificationInfo.get("title"));
        data.put("abstract", identificationInfo.get("abstract"));

        return data;
    }

    /**
     * Parse a filter string in the form of key1:val1,val2#key2:val3,val4#
     *
     * @param filterString
     * @return
     * @throws Exception
     */
    private static Multimap<String, String> parseFiltersTerms(String filterString) {
        Multimap<String, String> filterTermsMap = HashMultimap.create();

        //parse only when there is something to return
        if (filterString != null && filterString.length() > 0) {
            // Do not use a regexpr for the moment but should do
            String[] elems = filterString.split("[\\+, ]");

            for (String elem : elems) {
                // ignore empty elements
                if (elem.length() > 0) {
                    String[] dummy = elem.split(":");
                    if (dummy.length < 2) {
                        throw new RuntimeException("Error filterTermsMap incorrectly formatted. map content = " + elem);
                    }

                    filterTermsMap.put(dummy[0], dummy[1]);
                }
            }
        }

        return filterTermsMap;
    }

    //Static immutable map to create a translation table between the facets and the name displayed on screen to users
    //TODO to externalize in a config file
    static final ImmutableMap<String, String> FACETS2HIERACHYNAMES = ImmutableMap.of("satellites", "hierarchyNames.satellite",
            "instruments", "hierarchyNames.instrument",
            "categories", "hierarchyNames.category",
            "societalBenefitArea", "hierarchyNames.societalBenefitArea",
            "distribution", "hierarchyNames.distribution"
    );

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
        Map<String, String> resHit = null;

        Multimap<String, String> filterTermsMap = parseFiltersTerms(filterString);
        Set<String> hiddenFacets = new HashSet<>(); // to create the list of filters to hide

        String filterConstruct = "";
        if (filterTermsMap.size() > 0) {
            int i = 0;
            String filterTerms = "";
            for (String key : filterTermsMap.keySet()) {
                for (String term : filterTermsMap.get(key)) {
                    if (i == 0) {
                        filterTerms += "{ \"term\" : { \"" + FACETS2HIERACHYNAMES.get(key) + "\":\"" + term + "\"}}";
                    } else {
                        filterTerms += ",{ \"term\" : { \"" + FACETS2HIERACHYNAMES.get(key) + "\":\"" + term + "\"}}";
                    }

                    hiddenFacets.add(key + ":" + term);

                    i++;
                }
            }

            filterConstruct = " \"bool\" : { \"must\" : [" + filterTerms + "] }";
        }

        String body = "{ "
                + // pagination information
                "\"from\" : " + from + ", \"size\" : " + size + ","
                + // request highlighted info
                "\"highlight\" : { \"pre_tags\" : [\"<em><strong>\"], \"post_tags\" : [\"</strong></em>\"], "
                + "                  \"fields\" : { \"identificationInfo.title\": {\"fragment_size\" : 300, \"number_of_fragments\" : 1}, "
                + "                                 \"identificationInfo.abstract\": {\"fragment_size\" : 5000, \"number_of_fragments\" : 1} } } , "
                + // request facets to refine search (here the maximum number of facets can be configured)
                " \"facets\" :   { \"satellites\": { \"terms\" : { \"field\" : \"hierarchyNames.satellite\", \"size\":4 } }, "
                + "                  \"instruments\": { \"terms\" : { \"field\" : \"hierarchyNames.instrument\", \"size\":4  } }, "
                + "                  \"categories\": { \"terms\" : { \"field\" : \"hierarchyNames.category\", \"size\":4 } }, "
                + "                  \"societal Benefit Area\": { \"terms\" : { \"field\" : \"hierarchyNames.societalBenefitArea\", \"size\":5 } }, "
                + "                  \"distribution\": { \"terms\" : { \"field\" : \"hierarchyNames.distribution\", \"size\":5 } } "
                + "                },"
                + // add query info
                "\"query\" : { \"filtered\": { \"query\": "
                + "              { \"simple_query_string\" : { \"fields\" : [\"identificationInfo.title^10\", \"identificationInfo.abstract\"], "
                + "\"query\" : \"" + searchTerms + "\" } "
                + "}"
                + ",\"filter\": {" + filterConstruct + "}"
                + " }}}";

        log.trace("elastic-search request: {}", body);

        //measure elapsed time
        Stopwatch stopwatch = Stopwatch.createStarted();

        WebResponse response = rClient.doGetRequest(url, headers, params,
                body, log.isDebugEnabled());

        if (response == null) {
            log.error("Response from {} is null!", this.name);
            data.put("total_hits", 0);
            data = addMessage(data, MessageLevel.danger, "Response is null from " + this.name);
        } else {
            log.trace("Got response: {}", response);

            if (response.status == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsObj;
                try {
                    jsObj = (JSONObject) parser.parse(response.body);
                } catch (ParseException e) {
                    log.error("Could not parse search server response: {}", e);
                    addMessage(data, MessageLevel.danger, "Could not parse server response: " + e.getMessage());
                    return data;
                }

                data.put("total_hits", ((Map<?, ?>) jsObj.get("hits")).get("total"));

                // compute the pagination information to create the pagination bar
                Map<String, Object> pagination = computePaginationParams(((Long) (data.get("total_hits"))).intValue(), from);
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
                    
                    JSONObject info = (JSONObject) ((JSONObject)hit.get("_source")).get("identificationInfo");
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
        ElasticsearchApp app = new ElasticsearchApp();
        app.run();
    }
}
