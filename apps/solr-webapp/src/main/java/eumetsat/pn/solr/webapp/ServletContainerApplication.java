/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.solr.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.servlet.SparkApplication;

/**
 *
 * @author danu
 */
public class ServletContainerApplication implements SparkApplication {

    private static final Logger log = LoggerFactory.getLogger(ServletContainerApplication.class);
    
    @Override
    public void init() {
        log.info("Initializing...");
        
        SolrApp app = new SolrApp(true);
        app.run();
        
        log.info("Initialized: {}", app);
    }
    
}
