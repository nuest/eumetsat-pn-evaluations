package eumetsat.pn.solr;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import com.github.autermann.yaml.YamlNode;
import eumetsat.pn.common.ISO2JSON;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer.RemoteSolrException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

/**
 *
 * @author danu
 */
public class SolrFeeder extends ISO2JSON {

    public static void main(String[] args) throws IOException {
        try {
            URI configuration = ISO2JSON.class.getResource("/feeder-log4j2.xml").toURI();
            Configurator.initialize("eumetsat.pn", null, configuration);
        } catch (URISyntaxException e) {
            log.error("Could not configure logging: {}", e.getMessage());
        }

        ISO2JSON transformer = new SolrFeeder();
        transformer.transformAndIndex();

        log.info("Finished.");
    }

    protected void indexDirContent(Path aSrcDir) {
        log.info("Indexing dir content {}", aSrcDir);

        JSONParser parser = new JSONParser();

        YamlNode endpointConfig = this.config.get("endpoint");
        log.info("Endpoint configuration: {}", endpointConfig);

        String solrEndpoint = endpointConfig.get("url").asTextValue();
        String collection = endpointConfig.get("collection").asTextValue();
        SolrServer solr = new ConcurrentUpdateSolrServer(solrEndpoint + "/" + collection, 10, 1);

//        CloudSolrServer solr = new CloudSolrServer(solrEndpoint);
//        solr.setDefaultCollection(collection);
        log.debug("Created Solr server: {}", solr);

        SolrPingResponse ping;
        try {
            ping = solr.ping();
            log.debug("Pinged Solr server: {}", ping);
        } catch (SolrServerException | IOException | RemoteSolrException e) {
            log.error("Could not ping Solr server", e);
        }

//        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", endpointConfig.get("cluster.name").asTextValue()).build();
        int cpt = 0;
        Collection<File> inputFiles = FileUtils.listFiles(aSrcDir.toFile(), new String[]{"json"}, false);
        log.info("Indexing {} files...", inputFiles.size());

        for (File file : inputFiles) {
            try {
                String jsonStr = FileUtils.readFileToString(file);
                JSONObject jsObj = (JSONObject) parser.parse(jsonStr);

                SolrInputDocument input = new SolrInputDocument();
                // field values should match schema.xml

                // solr can add new fields on the fly: http://heliosearch.org/solr/getting-started/
                input.addField("id", jsObj.get("fileIdentifier"));
                JSONObject info = (JSONObject) jsObj.get("identificationInfo");
                input.addField("title", info.get("title"));

                String id = (String) jsObj.get(FILE_IDENTIFIER_PROPERTY);
                log.debug("Adding {} to collection {}", id, collection);
                solr.add(input);

                if (cpt % 42 == 0) { // periodically flush
                    log.info("Commiting to server, document count is {}", cpt);
                    UpdateResponse response = solr.commit();
                    log.info("Response status: {} (time: {}): {}", response.getStatus(), response.getElapsedTime(), response.toString());
                }

                cpt++;

            } catch (IOException | ParseException | SolrServerException e) {
                log.error("Error comitting document based on file {}", file, e);
            }
        }

        solr.shutdown();

        log.info("Indexed {} of {} files.", cpt, inputFiles.size());
    }

    @Override
    protected String getConfigBasename() {
        return "solr";
    }
}
