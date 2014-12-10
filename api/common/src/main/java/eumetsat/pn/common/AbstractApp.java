/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common;

import com.github.autermann.yaml.Yaml;
import com.github.autermann.yaml.YamlNode;
import com.google.common.collect.ImmutableMap;
import freemarker.template.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.TemplateEngine;
import spark.TemplateViewRoute;

/**
 *
 * @author danu
 */
public abstract class AbstractApp {

    private static final Logger log = LoggerFactory.getLogger(AbstractApp.class);

    private static final String DEFAULT_CONFIG_FILE = "/app.yml";

    protected static final String PRODUCT_DESCRIPTION_ROUTE = "/product_description";

    protected static final String SEARCH_ROUTE = "/search";

    protected static final String SEARCH_RESULTS_ROUTE = SEARCH_ROUTE + "/results";

    //Static immutable map to create a translation table between the facets and the name displayed on screen to users
    //TODO to externalize in a config file
    public static final ImmutableMap<String, String> FACETS2HIERACHYNAMES = ImmutableMap.of("satellites", "hierarchyNames.satellite",
            "instruments", "hierarchyNames.instrument",
            "categories", "hierarchyNames.category",
            "societalBenefitArea", "hierarchyNames.societalBenefitArea",
            "distribution", "hierarchyNames.distribution"
    );

    //elements per page (currently only a static constant, to be externalized)
    static final int ELEM_PER_PAGE = 10;

    protected Configuration cfg;
    protected String searchEndpointUrlString;
    protected int port;
    protected final String name;
    protected final String searchEndpointBaseUrl;
    protected final String productEndpointUrlString;
    protected YamlNode config;
    private String MESSAGES_ENTRY = "user_messages";

    public AbstractApp() {
        this(DEFAULT_CONFIG_FILE);
    }

    public AbstractApp(String configFile) {
        try (InputStream fis = getClass().getResourceAsStream(configFile)) {
            YamlNode n = new Yaml().load(fis);
            this.config = n.get(getConfigBasename()).get("app");
        } catch (IOException e) {
            log.error("Could not load config from file {}", configFile, e);
        }

        this.cfg = new Configuration(Configuration.VERSION_2_3_21);
        this.cfg.setClassForTemplateLoading(this.getClass(), "/");

        YamlNode searchEndpoint = config.get("searchendpoint");
        this.searchEndpointBaseUrl = searchEndpoint.get("url").asTextValue();
        YamlNode paths = searchEndpoint.get("paths");
        this.searchEndpointUrlString = this.searchEndpointBaseUrl + paths.get("search").asTextValue();
        this.productEndpointUrlString = this.searchEndpointBaseUrl + paths.get("productinfo").asTextValue();
        this.name = this.config.get("name").asTextValue();
        this.port = this.config.get("port").asIntValue(4567);

        log.info("NEW app '{}' based on {}", this.name, configFile);
    }

    protected abstract String getConfigBasename();

    public enum MessageLevel {

        success, info, warning, danger;
    }

    /**
     *
     * @param data
     * @param level
     * @param message
     * @return
     */
    protected Map<String, Object> addMessage(Map<String, Object> data, MessageLevel level, String message) {
        Map<String, String> messages = new HashMap<>();
        if (data.containsKey(MESSAGES_ENTRY)) {
            Map<String, String> existingMessages = (Map<String, String>) data.get(MESSAGES_ENTRY);
            messages.putAll(existingMessages);
        }

        messages.put(message, level.toString());

        data.put(MESSAGES_ENTRY, messages);
        return data;
    }

    /**
     * search using the Rest interface
     *
     * @param searchTerms the search terms
     * @param filterString text for filtering
     * @param from offset of the first element to return
     * @param size maximum number of elements to return
     * @return a map for the template engine
     */
    protected abstract Map<String, Object> search(String searchTerms, String filterString, int from, int size);

    /**
     * get the product description from the search engine
     *
     * @param id Id of the product
     * @return a map for the template engine
     */
    protected abstract Map<String, Object> describeProduct(String id) throws MalformedURLException, ParseException;

    public Map<String, Object> addGlobalAttributes(Map<String, Object> attributes) {
        attributes.put("search_endpoint", SEARCH_RESULTS_ROUTE);
        attributes.put("engine", name);
        attributes.put("elem_per_page", ELEM_PER_PAGE);
        return attributes;
    }

    /**
     * @param total number of found elements
     * @param from_element start element
     * @return the pagination information for the template engine
     */
    protected Map<String, Object> computePaginationParams(int total, int from_element) {
        Map<String, Object> pagination = new HashMap<>();

        // nb_pages = integer div + 1 if total mod elem_per_page > 0
        int nb_pages = (total / ELEM_PER_PAGE) + (((total % ELEM_PER_PAGE) == 0) ? 0 : 1);

        pagination.put("nb_pages", nb_pages);
        pagination.put("current_page", (from_element / ELEM_PER_PAGE));
        pagination.put("elem_per_page", ELEM_PER_PAGE);

        log.trace("Pagination computed: {} of {}, {} items per page", pagination.get("current_page"), pagination.get("nb_pages"), pagination.get("elem_per_page"));
        return pagination;
    }

    public void run() {
//        Path publicDir;
//        try {
//            publicDir = Paths.get(AbstractApp.class.getResource("/public").toURI());
//        } catch (URISyntaxException e) {
//            log.error("Error resolving public dir path", e);
//            return;
//        }
//        log.info("Setting public file location to {}", publicDir.toAbsolutePath());
//        Spark.staticFileLocation(publicDir.toAbsolutePath().toString());
        
        Spark.staticFileLocation("public");
        Spark.port(this.port);

        TemplateEngine engine = new FreeMarkerTemplateEngine(cfg);

        Spark.get(SEARCH_ROUTE, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle search request...");
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
                    Spark.halt(401, "Error while accessing page search_page. error = " + str);
                }
                return mav;
            }
        }, engine);

        Spark.get(SEARCH_RESULTS_ROUTE, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle search request: {}", request.raw().getRequestURL());
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
                    Map<String, Object> data = search(searchTerms, filterTerms, from, size);
                    attributes.putAll(data);
                    mav = new ModelAndView(attributes, "/templates/search_results.ftl");
                } catch (RuntimeException e) {
                    log.error("Error handling search/results", e);
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    String str = errors.toString();
                    Spark.halt(401, "Error while returning responses: \n\n" + str);
                }
                return mav;
            }
        }, engine);

        Spark.get(PRODUCT_DESCRIPTION_ROUTE, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle product description request: ", request.raw().getRequestURL().toString());
                String id = request.queryParams("id");
                ModelAndView mav = null;
                Map<String, Object> attributes = new HashMap<>();
                attributes = addGlobalAttributes(attributes);
                try {
                    //get product description info and return them as the input for the template
                    Map<String, Object> data = describeProduct(id);
                    attributes.putAll(data);
                    mav = new ModelAndView(attributes, "/templates/product_description.ftl");
                } catch (RuntimeException | MalformedURLException | ParseException e) {
                    log.error("Error during product description page.", e);
                    // TODO return error view
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    String str = errors.toString();
                    Spark.halt(401, "Error while returning responses: \n{}" + str);
                }
                return mav;
            }
        }, engine);

        Spark.get("/", new Route() {
            @Override
            public Object handle(Request request, Response response) {
                log.trace("Handle base request > redirect!");
                response.redirect("/search");
                return null;
            }
        });
    }

}
