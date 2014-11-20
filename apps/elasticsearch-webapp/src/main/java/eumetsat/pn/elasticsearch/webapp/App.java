/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.elasticsearch.webapp;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import eumetsat.pn.common.util.SimpleRestClient;
import eumetsat.pn.common.util.SimpleRestClient.WebResponse;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.freemarker.FreeMarkerRoute;

/**
 * A simple example just showing some basic functionality
 *
 * @author danu
 */
public class App {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(App.class);

    protected static final String SEARCH_RESULTS_ROUTE = "/search/results";

    // start elastic client once for all
    private Client client = new TransportClient()
            .addTransportAddress(new InetSocketTransportAddress("localhost",
                            9300));

    private SimpleRestClient rClient = new SimpleRestClient();

    // Freemarker configuration object
    private Configuration cfg;

    //elements per page (currently only a static constant, to be externalized)
    final static int ELEM_PER_PAGE = 10;

    private String searchEndpointUrlString;

    private int port = 4567;

    private final String engine;

    private final String searchEndpointBaseUrl;

    private final String productEndpointUrlString;

    public App() {
        this.cfg = new Configuration(Configuration.VERSION_2_3_21);
        this.cfg.setClassForTemplateLoading(this.getClass(), "/");
        this.searchEndpointBaseUrl = "http://localhost:9200/";
        this.searchEndpointUrlString = this.searchEndpointBaseUrl + "_search";
        this.productEndpointUrlString = this.searchEndpointBaseUrl + "eumetsat-catalogue/product/";
        this.engine = "Elasticsearch";

        log.info("NEW {}", this);
    }

    /**
     * return the pagination information
     *
     * @param total
     * @param from_element
     * @return
     */
    private static Map<String, Object> computePaginationParams(int total, int from_element) {
        Map<String, Object> pagination = new HashMap<>();

        // nb_pages = integer div + 1 if total mod elem_per_page > 0
        int nb_pages = (total / ELEM_PER_PAGE) + (((total % ELEM_PER_PAGE) == 0) ? 0 : 1);

        pagination.put("nb_pages", nb_pages);
        pagination.put("current_page", (from_element / ELEM_PER_PAGE));
        pagination.put("elem_per_page", ELEM_PER_PAGE);

        log.trace("Pagination computed: {} of {}, {} items per page", pagination.get("current_page"), pagination.get("nb_pages"), pagination.get("elem_per_page"));
        return pagination;
    }

    /**
     * get the product description from elastic search index
     *
     * @param id Id of the product
     * @return
     * @throws Exception
     */
    private Map<String, Object> getProductDescriptionFromElSearch(String id) throws MalformedURLException, ParseException {
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

    /**
     * search using the Rest interface
     *
     * @param searchTerms
     * @param from offset of the first element to return
     * @param size maximum nb of elements to return
     * @return
     * @throws Exception
     */
    private Map<String, Object> searchQueryElasticSearch(String searchTerms, String filterString, int from, int size) {
        Map<String, Object> data = new HashMap<>();
        // put "session" parameters here rightaway so it can be used in template even when empty result
        data.put("search_terms", searchTerms);
        data.put("filter_terms", filterString);

        URL url;
        try {
            url = new URL(searchEndpointUrlString);
        } catch (MalformedURLException ex) {
            log.error("Search enpoint URL malformed: {}", searchEndpointUrlString);
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
                + // request facets to refine search
                " \"facets\" :   { \"satellites\": { \"terms\" : { \"field\" : \"hierarchyNames.satellite\", \"size\":5 } }, "
                + "                  \"instruments\": { \"terms\" : { \"field\" : \"hierarchyNames.instrument\", \"size\":5  } }, "
                + "                  \"categories\": { \"terms\" : { \"field\" : \"hierarchyNames.category\", \"size\":5 } }, "
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
            log.error("Response is null!");
            data.put("total_hits", 0);
        } else {
            log.trace("Got response: {}", response);

            if (response.status == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsObj;
                try {
                    jsObj = (JSONObject) parser.parse(response.body);
                } catch (ParseException ex) {
                    log.error("Could not parse search server response: {}", ex);
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

                    resHits.add(resHit);
                }

                data.put("hits", resHits);

                data.put("facets", jsObj.get("facets"));

                data.put("tohide", hiddenFacets);

            } else { // non-200 resonse
                log.error("Received non-200 response: {}", response);
            }
        }

        stopwatch.stop();

        data.put("elapsed", (double) (stopwatch.elapsed(TimeUnit.MILLISECONDS)) / (double) 1000);

        return data;
    }

    public static void main(String[] args) {
        App app = new App();

        app.run();
    }

    public Map<String, Object> addGlobalAttributes(Map<String, Object> attributes) {
        attributes.put("search_endpoint", SEARCH_RESULTS_ROUTE);
        attributes.put("engine", engine);
        attributes.put("elem_per_page", ELEM_PER_PAGE);
        return attributes;
    }

    private void run() {
        Spark.staticFileLocation("public");
        Spark.setPort(this.port);

        Spark.get(new FreeMarkerRoute("/search") {
            @Override
            public Object handle(Request request, Response response) {
                log.trace("Handle search request...");

                setConfiguration(cfg);

                ModelAndView mav = null;
                Map<String, Object> attributes = new HashMap<>();
                addGlobalAttributes(attributes);

                try {
                    mav = new ModelAndView(attributes, "/templates/search_page.ftl");
                } catch (RuntimeException e) {
                    log.error("Error serving request", e);
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    String str = errors.toString();
                    halt(401, "Error while accessing page search_page. error = " + str);
                }

                return mav;
            }
        });

        Spark.get(new FreeMarkerRoute(SEARCH_RESULTS_ROUTE) {
            @Override
            public Object handle(Request request, Response response) {
                log.trace("Handle search request: {}", request.raw().getRequestURL());

                setConfiguration(cfg);
                ModelAndView mav = null;

                Map<String, Object> attributes = new HashMap<>();
                attributes = addGlobalAttributes(attributes);

                try {
                    String searchTerms = request.queryParams("search-terms");
                    String filterTerms = request.queryParams("filter-terms");

                    int from = -1;
                    int size = -1;

                    try {
                        from = Integer.parseInt(request.queryParams("from"));
                    } catch (Exception e) {
                        log.error("Parameter 'from' cannot be converted to int. default to -1.", e);
                    }

                    try {
                        size = Integer.parseInt(request.queryParams("size"));
                    } catch (Exception e) {
                        log.error("Parameter 'size' cannot be converted to int. default to -1.", e);
                    }

                    log.trace("Search terms: {} | Filter terms: {} | From: {} | Size: {}", searchTerms, filterTerms, from, size);

                    Map<String, Object> data = searchQueryElasticSearch(searchTerms, filterTerms, from, size);
                    attributes.putAll(data);

                    mav = new ModelAndView(attributes, "/templates/search_results.ftl");
                } catch (RuntimeException e) {
                    log.error("Error handling search/results", e);
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    String str = errors.toString();
                    halt(401, "Error while returning responses: \n\n" + str);
                }

                return mav;
            }
        });

        /**
         * add product page detail
         */
        Spark.get(new FreeMarkerRoute("/product_description") {
            @Override
            public Object handle(Request request, Response response) {
                log.trace("Handle product description request: ", request.raw().getRequestURL().toString());
                String id = request.queryParams("id");

                setConfiguration(cfg);
                ModelAndView mav = null;
                Map<String, Object> attributes = new HashMap<>();
                attributes = addGlobalAttributes(attributes);

                try {

                    //get product description info and return them as the input for the template
                    Map<String, Object> data = getProductDescriptionFromElSearch(id);
                    attributes.putAll(data);

                    mav = new ModelAndView(attributes, "/templates/product_description.ftl");
                } catch (RuntimeException | MalformedURLException | ParseException e) {
                    log.error("Error during product description page.", e);
                    // TODO return error view
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    String str = errors.toString();
                    halt(401, "Error while returning responses: \n{}" + str);
                }

                return mav;
            }
        });

        /**
         * default page => redirect by default to the search start page
         */
        Spark.get(new Route("/") {
            @Override
            public Object handle(Request request, Response response) {
                log.trace("Handle base request > redirect!");
                response.redirect("/search");
                return null;
            }
        });

    }
}
