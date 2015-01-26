/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common;

import com.github.autermann.yaml.Yaml;
import com.github.autermann.yaml.YamlNode;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import freemarker.template.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

    protected static final String DEFAULT_CONFIG_FILE = "/app.yml";

    protected String productDescriptionRoute = "/product_description";
    
    protected String autocompleteRoute = "/autocomplete";

    protected String searchRoute = "/search";

    protected static final String PUBLIC_ROUTE = "public";

    protected String searchResultsRoute = searchRoute + "/results";

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
    protected String name;
    protected String searchEndpointBaseUrl;
    protected String productEndpointUrlString;
    protected YamlNode config;
    private String MESSAGES_ENTRY = "user_messages";
    private final boolean servletContainer;
    private final String path;
    private final String autocompleteEndpointUrlString;

    public AbstractApp() {
        this(false, DEFAULT_CONFIG_FILE);
    }

    public AbstractApp(boolean servletContainer) {
        this(servletContainer, DEFAULT_CONFIG_FILE);
    }

    public AbstractApp(String configFile) {
        this(false, configFile);
    }

    public AbstractApp(boolean servletContainer, String configFile) {
        this.servletContainer = servletContainer;

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
        this.searchEndpointUrlString = this.searchEndpointBaseUrl + paths.get("search").asTextValue("");
        this.productEndpointUrlString = this.searchEndpointBaseUrl + paths.get("productinfo").asTextValue();
        this.name = this.config.get("name").asTextValue();
        this.port = this.config.get("port").asIntValue(4567);
        this.path = this.config.get("path").asTextValue("");
        
        this.autocompleteEndpointUrlString = config.get("autocomplete").asTextValue();

        log.info("NEW app '{}' based on {}: \n\t\t{}", this.name, configFile, this.toString());
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
     * @throws java.net.MalformedURLException
     * @throws org.json.simple.parser.ParseException
     */
    protected abstract Map<String, Object> describeProduct(String id) throws MalformedURLException, ParseException;

    protected abstract void feed() throws IOException;

    protected abstract void feed(Path configFile) throws IOException;

    public Map<String, Object> addGlobalAttributes(Map<String, Object> attributes) {
        attributes.put("search_endpoint", servletContainer ? "/" + path + searchResultsRoute : searchResultsRoute);
        attributes.put("description_endpoint", servletContainer ? "/" + path + productDescriptionRoute : productDescriptionRoute);
        attributes.put("autocomplete_endpoint", servletContainer ? "/" + path + autocompleteRoute : autocompleteRoute);
        attributes.put("engine", name);
        attributes.put("elem_per_page", ELEM_PER_PAGE);
        attributes.put("path", servletContainer ? "/" + path : "");
        attributes.put("public_path", servletContainer ? "/" + path + "/" + PUBLIC_ROUTE : "");
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
        if (servletContainer) {
            log.info("Running in servlet container, not configuring port, webapp path is {}", this.path);
        } else {
            log.info("Running standalone, configuring port to {}", this.port);
            Spark.staticFileLocation(PUBLIC_ROUTE);
            Spark.port(this.port);
        }

        TemplateEngine engine = new FreeMarkerTemplateEngine(cfg);

        Spark.get(searchRoute, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle search request...");

                setHeaders(response);

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

        Spark.get(searchResultsRoute, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle search request: {}", request.raw().getRequestURL());

                setHeaders(response);

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

        Spark.get(productDescriptionRoute, new TemplateViewRoute() {

            @Override
            public ModelAndView handle(Request request, Response response) {
                log.trace("Handle product description request: ", request.raw().getRequestURL().toString());
                String id = request.queryParams("id");
                Map<String, Object> attributes = new HashMap<>();
                attributes = addGlobalAttributes(attributes);
                
                setHeaders(response);

                try {
                    //get product description info and return them as the input for the template
                    Map<String, Object> data = describeProduct(id);
                    attributes.putAll(data);
                } catch (RuntimeException | MalformedURLException | ParseException e) {
                    log.error("Error during product description page.", e);
//                    errorResponse(e);
                    attributes = addMessage(attributes, MessageLevel.danger, "Error: " + response.toString());
                }

                ModelAndView mav = new ModelAndView(attributes, "/templates/product_description.ftl");

                return mav;
            }
        }, engine);

        Spark.get("/", new Route() {
            @Override
            public Object handle(Request request, Response response) {
                String destination = servletContainer ? "/" + path + searchRoute : searchRoute;
                log.trace("Handle base request > redirect to {}!", destination);
                response.redirect(destination);
                return null;
            }
        });

        Spark.get("/feed", new Route() {
            @Override
            public Object handle(Request request, Response response) {
                log.info("Starting feeding!");

                try {
                    String config = request.queryMap("config").value();
                    if (config != null && !config.isEmpty()) {
                        config = URLDecoder.decode(config, "UTF-8");
                        log.debug("Decoded parameter 'config': {}", config);
                        Path p = Paths.get(config);
                        feed(p);
                    } else {
                        feed();
                    }
                } catch (IOException e) {
                    log.error("Error during feeding", e);
                    errorResponse(e);
                }

                log.info("Done with feeding.");

                response.redirect(servletContainer ? "/" + path : "");
                return null;
            }

        });
        
        Spark.get(autocompleteRoute, new Route() {
            @Override
            public Object handle(Request request, Response response) {
                log.debug("Autocomplete!");

                // TODO this is not sufficient, the body has to be passed on as well!
                response.redirect(autocompleteEndpointUrlString);
                return null;
            }

        });
    }

    protected void errorResponse(Exception e) {
        // TODO return error view
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        String str = errors.toString();
        Spark.halt(401, "Error while returning responses: \n\n\n" + str);
    }

    protected void setHeaders(Response response) {
        response.header("Access-Control-Allow-Origin", "*");
        response.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.header("Access-Control-Allow-Headers", "x-requested-with");
    }

    /**
     * Parse a filter string in the form of key1:val1,val2#key2:val3,val4#
     *
     * @param filterString
     * @return
     */
    protected static Multimap<String, String> parseFiltersTerms(String filterString) {
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AbstractApp [servletContainer = ").append(this.servletContainer);
        sb.append(", path = ").append(this.path);
        sb.append(", searchRoute = ").append(this.searchRoute).append("]");
        return sb.toString();
    }

}
