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
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer.RemoteSolrException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.json.simple.JSONArray;

/**
 *
 * @author danu
 */
public class SolrFeeder extends ISO2JSON {

    private SolrServer server;

    public SolrFeeder() {
        super();
    }
    
    public SolrFeeder(Path configFile) {
        super(configFile);
    }

    public SolrFeeder(SolrServer server) {
        super();
        this.server = server;
    }

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

    @Override
    protected void indexDirContent(Path aSrcDir) {
        log.info("Indexing dir content {}", aSrcDir);

        JSONParser parser = new JSONParser();

        YamlNode endpointConfig = this.config.get("endpoint");
        String collection = endpointConfig.get("collection").asTextValue();

        SolrServer solr;
        if (this.server != null) {
            solr = server;
            log.info("Using embedded SolrServer: {}", solr);
        } else {
            log.info("Endpoint configuration: {}", endpointConfig);

            String solrEndpoint = endpointConfig.get("url").asTextValue();
            solr = new ConcurrentUpdateSolrServer(solrEndpoint + "/" + collection, 10, 1);
//        CloudSolrServer solr = new CloudSolrServer(solrEndpoint);
//        solr.setDefaultCollection(collection);
            log.info("Using HTTP SolrServer: {}", solr);
        }

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

                try {
                    SolrInputDocument input = createInputDoc(jsObj);

                    log.debug("Adding {} to collection {}", file.getName(), collection);
                    log.trace("Full json of {}: {}", file.getName(), input);
                    solr.add(input);

                    cpt++;
                } catch (RuntimeException e) {
                    log.error("Error processing input document {}: {}, {}", file, e, e.getMessage());
                }

                if (cpt % 42 == 0) { // periodically flush
                    log.info("Commiting to server, document count is {}", cpt);
                    UpdateResponse response = solr.commit();
                    log.info("Response status: {} (time: {}): {}", response.getStatus(), response.getElapsedTime(), response.toString());
                }

            } catch (IOException | ParseException | SolrServerException e) {
                log.error("Error comitting document based on file {}", file, e);
            }
        }

        try {
            solr.commit();
        } catch (IOException | SolrServerException e) {
            log.error("Error comitting document", e);
        }

        solr.shutdown();

        log.info("Indexed {} of {} files.", cpt, inputFiles.size());
    }

    private SolrInputDocument createInputDoc(JSONObject jsObj) {
        SolrInputDocument input = new SolrInputDocument();
                // field values should match schema.xml

        // solr can add new fields on the fly: http://heliosearch.org/solr/getting-started/
        String id = (String) jsObj.get(FILE_IDENTIFIER_PROPERTY);
        input.addField("id", id);

        JSONObject info = (JSONObject) jsObj.get("identificationInfo");
        input.addField("title", info.get("title"));
        input.addField("description", info.get("abstract"));
        if (!info.get("thumbnail").toString().isEmpty()) {
            input.addField("thumbnail_s", info.get("thumbnail"));
        }

        JSONArray keywords = (JSONArray) info.get("keywords");
        if (!keywords.isEmpty()) {
            input.addField("keywords", keywords.toArray());
        }

        if (!info.get("status").toString().isEmpty()) {
            input.addField("status_s", info.get("status"));
        }

        JSONObject hierarchy = (JSONObject) jsObj.get("hierarchyNames");

        //public static final ImmutableMap<String, String> FACETS2HIERACHYNAMES = ImmutableMap.of("satellites", "hierarchyNames.satellite",
        //"instruments", "hierarchyNames.instrument",
        //"categories", "hierarchyNames.category",
        //"societalBenefitArea", "hierarchyNames.societalBenefitArea",
        //"distribution", "hierarchyNames.distribution");
        if (hierarchy.get("satellite") != null && !hierarchy.get("satellite").toString().isEmpty()) {
            input.addField("satellite_s", hierarchy.get("satellite"));
        }

        if (hierarchy.get("instrument") != null && !hierarchy.get("instrument").toString().isEmpty()) {
            input.addField("instrument_s", hierarchy.get("instrument"));
        }

        JSONArray categories = (JSONArray) hierarchy.get("category");
        if (categories != null && !categories.isEmpty()) {
            input.addField("category", categories);
        }

        JSONArray sbas = (JSONArray) hierarchy.get("societalBenefitArea");
        if (sbas != null && !sbas.isEmpty()) {
            input.addField("societalBenefitArea_ss", sbas);
        }

        Collection<String> distrs = (Collection<String>) hierarchy.get("distribution");
        if (distrs != null && !distrs.isEmpty()) {
            input.addField("distribution_ss", distrs);
        }

        // https://cwiki.apache.org/confluence/display/solr/Spatial+Search
        JSONObject location = (JSONObject) jsObj.get("location");
        String type = (String) location.get("type");
        if ("envelope".equals(type)) {
            StringBuilder envelope = new StringBuilder();
            // ISO2JSON: envelope.add(leftTopPt); envelope.add(rightDownPt);
            JSONArray coords = (JSONArray) location.get("coordinates");
            JSONArray leftTopPoint = (JSONArray) coords.get(0);
            JSONArray rightDownPoint = (JSONArray) coords.get(1);

            // Spatial search envelope: minX, maxX, maxY, minY
            envelope.append("ENVELOPE(").append(leftTopPoint.get(0)).append(", ");
            envelope.append(rightDownPoint.get(0)).append(", ");
            envelope.append(leftTopPoint.get(1)).append(", ");
            envelope.append(rightDownPoint.get(1)).append(")");
            input.addField("boundingbox", envelope.toString());
        } else {
            log.warn("Unsupported location field value: {}", location.toJSONString());
        }

        JSONObject contact = (JSONObject) jsObj.get("contact");
        input.addField("email_s", contact.get("email"));
        input.addField("address_s", contact.get("address"));

        input.addField("xmldoc", jsObj.get("xmldoc"));

        return input;
    }

    @Override
    protected String getConfigBasename() {
        return "solr";
    }
}
