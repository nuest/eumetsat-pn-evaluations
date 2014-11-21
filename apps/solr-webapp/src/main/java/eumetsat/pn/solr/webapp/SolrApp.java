/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.solr.webapp;

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
public class SolrApp extends AbstractApp {

    private static final Logger log = LoggerFactory.getLogger(SolrApp.class);

    public SolrApp() {
        super();
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
            return data;
        }

        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, String> params = new HashMap<>();

        List<Map<String, String>> resHits = new ArrayList<>();

        log.trace("solr: {}", "...");

        //measure elapsed time
        Stopwatch stopwatch = Stopwatch.createStarted();

        // query Solr
        
        // transform JSON to map for template engine
        
        stopwatch.stop();

        data.put("elapsed", (double) (stopwatch.elapsed(TimeUnit.MILLISECONDS)) / (double) 1000);

        return data;
    }
    
    @Override
    protected String getConfigBasename() {
        return "solr";
    }
    
    public static void main(String[] args) {
        SolrApp app = new SolrApp();
        app.run();
    }
}
