# EUMETSAT Elasticsearch and Solr Evaluations

Comparison of search frameworks for the EUMETSAT Product Navigator.

This project is based on the following components:

* Bootstrap and jQuery for HTML client (styling, JavaScript)
* **[Freemarker](http://freemarker.org) template engine** for server-side web page generation
* Document **search framewors** based on [Lucene](http://lucene.apache.org/).
  * These search frameworks are started using their shipped web servers.
  * Configuration files for the frameworks are provided in the directories `/apps/<framework>-webapp/src/main/resources`
  * Compared frameworks
    * *Elasticsearch*
    * *Solr*
  * **Feeder** components which transform a given set of XML metadata documents (see directory `/metadata`) to JSON and insert the data into the search frameworks based on these documents.
* **Java libraries**
  * [Sparkjava](http://sparkjava.com/) rapid prototyping web framework
  * Configuration and utilities: yaml, Guava
  * Logging: slfj4, log4j2
  * [JSON Simple](https://code.google.com/p/json-simple/)
* [Maven](http://maven.apache.org/) **build framework**


**Developers:**

* Guillaume Aubert ([@gaubert](https://github.com/gaubert/), EUMETSAT)
* Daniel Nüst ([@nuest](https://github.com/nuest/), 52°North)


## Elasticsearch

Quick guide to launch the prototype for [Elasticsearch](http://www.elasticsearch.org/).

### 1) Run ElasticSearch

Download Elasticsearch at http://www.elasticsearch.org/overview/elkdownloads/.

Run Elasticsearch with the configuration provided within the webapp module:

```
cd /<path-to-elasticsearch>/bin/
elasticsearch(.bat) -Des.config=/<path-to-local-repo>/apps/elasticsearch-webapp/src/main/resources/elasticsearch.yml
```

Example (to make this work in Windows PowerShell the parameter is wrapped: "-D..."):

```
cd /<path-to-elasticsearch>/bin/
.\elasticsearch.bat "-Des.config=C:\Users\danu\Documents\2014_EUMETSAT\workspace\eumetsat-pn-evaluations\apps\elasticsearch-webapp\src\main\resources\elasticsearch.yml"
```

This will create a simple Elasticsearch store without persistence (working in RAM only).

You must also define a new mapping for the products to avoid index with an analyzer the content of the hierarchyNames (topic categories) to be used to create the facets:

```
etc/new_index_mapping.sh
```

### 2) Index metadata records

* Run ``eumetsat.pn.elasticsearch.ElasticsearchFeeder.main()`` in the module ``api/elasticsearch-api``
  * This class extracts info from XML records with XPath and create a JSON stored in a file for each record. Then all the JSON files are indexed in the Elasticsearch database.
  * The configuration file is ``api/elasticsearch-api/src/main/resources/feederconfig.yml``
  * An example JSON record is described here https://gist.github.com/gaubert/e26eb189f7e42317fbb1
* The feeder is also executed if the endpoint is not fed yet when starting the app (see below).

Alternatively, you can start the webapp and use he `/feed` endpoint, see example below.

```
http://localhost:4567/feed?config=C%3A%5CUsers%5Cdanu%5CDocuments%5C2014_EUMETSAT%5Cworkspace%5Ceumetsat-pn-evaluations%5Capps%5Celasticsearch-webapp%5Csrc%5Ctest%5Cresources%5Cfeederconfig.yml
```

Make sure to encode the full file path, e.g. using http://meyerweb.com/eric/tools/dencoder/

### 3) Search web app

The web app is based a on a tiny web framework called [Spark](http://www.sparkjava.com) meant for rapid development. Web pages are built using the simple template engine [Freemarker](http://freemarker.org), Boostrap, and JQuery.

Elastic Search is accessed using the REST interface with a REST client.

* Run ``eumetsat.pn.elasticsearch.webapp.ElasticsearchApp.main()`` in the module ``apps/elasticsearch-webapp``
  * The main method contains an instance of the feeder to feed if the endpoint does not deliver a response for a test document
* Point your browser to http://localhost:4567/
* The configuration file is ``apps/elasticsearch-webapp/src/main/resources/app.yml``

#### Features

* Search is based on having different weights for the different parts of the record (Title, Abstract).
* Use Facetted search to filter out results.
* Highlight found keywords in search results.
* Bookmarkable search.

### 4) Elasticsearch request examples

* Explore Elasticsearch instance: http://localhost:9200/_search?pretty=true
* Search for keyword "water": http://localhost:9200/eumetsat-catalogue/_search?pretty&q=water
* Search for keyword "ocean", retrieve only field `_id` and show extended scoring information: http://localhost:9200/eumetsat-catalogue/_search?pretty&q=ocean&fields=_id&explain=true
* Retrieve specific record: http://localhost:9200/eumetsat-catalogue/product/EO:EUM:DAT:SPOT:S10NDWISA
* Further examples
  * http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/docs.html

#### Highlighted search w/ pagination

```
#!/bin/bash
curl -XGET 'http://localhost:9200/_search?pretty=true' -d '{ "from" : 10, "size" : 10, 
  "highlight" : { 
                  "pre_tags" : ["<strong>"],
                  "post_tags" : ["</strong>"],
                  "fields" : 
                     { "identificationInfo.title": {"fragment_size" : 300, "number_of_fragments" : 1 }, "identificationInfo.abstract": {"fragment_size" : 1000, "number_of_fragments" : 1} } 
                } ,  
  "_source" : false,
  "query" : { 
              "simple_query_string" : 
                  { "fields" : ["identificationInfo.title^10", "identificationInfo.abstract"], "query" : "iasi" } 
            } 
}
'
```

#### Query with score adjustment

```
POST eumetsat-catalogue/product/_search

{
  "explain": true,
  "query" : {
    "simple_query_string" : {
        "fields" : ["identificationInfo.title^10", "identificationInfo.abstract"],
        "query" : "ATOVS METOP"
    }
  }
}
```

## Solr

Quick guide to launch the prototype for [Solr](http://lucene.apache.org/solr/).

### 1) Run Solr

Download and unzip Solr from http://lucene.apache.org/solr/mirrors-solr-latest-redir.html. Install and configure it based on the [quickstart tutorial](http://lucene.apache.org/solr/quickstart.html).

```
cd /<path-to-solr>/example/
java -Dsolr.solr.home=<path-to-workspace>/eumetsat-pn-evaluations/apps/solr-webapp/src/main/resources/ -jar start.jar
```

Example (to make this work in Windows PowerShell the parameter is wrapped: "-D..."):

```
cd /<path-to-solr>/example/
java "-Dsolr.solr.home=C:\Users\danu\Documents\2014_EUMETSAT\workspace\eumetsat-pn-evaluations\apps\solr-webapp\src\main\resources\" -jar start.jar
```

Check if Solr is running by querying the configured collection: http://localhost:8983/solr/eumetsat/query?q=weather

### 2) Configure Solr

To help comparability, we try to stick to default values where possible.

* See https://cwiki.apache.org/confluence/display/solr/A+Step+Closer for details of the `solr.home` - directory
* **core.properties** marks the solr.home directory (https://wiki.apache.org/solr/Core%20Discovery%20%284.4%20and%20beyond%29)
* **conf/schema.xml**
  * http://www.solrtutorial.com/schema-xml.html
  * pre-defined field types suffice
* **conf/solrconfig.xml** - parameters for configuring Solr and Solr search components
  * Run in memory (http://stackoverflow.com/questions/7904802/running-solr-in-memory)
* **solr.xml** - definition of the cores to be used by Solr, the directory is provided as parameter `-s` when starting Solr
* **conf/solrcore.properties** - property configuration file for a core, per each core (optional)

### 2) Index metadata records

* Run ``eumetsat.pn.solr.SolrFeeder.main()`` in the module ``api/solr-api`` manually
* The feeder is also executed if the endpoint is not fed yet when starting the app (see below).

Alternatively, use the `/feed`-endpoint as described above for Elasticsearch, for example:

```
http://localhost:5678/feed?config=C%3A%5CUsers%5Cdanu%5CDocuments%5C2014_EUMETSAT%5Cworkspace%5Ceumetsat-pn-evaluations%5Capps%5Celasticsearch-webapp%5Csrc%5Ctest%5Cresources%5Cfeederconfig.yml
```

### 3) Search web app

* Run ``eumetsat.pn.solr.webapp.SolrApp.main()`` in the module ``apps/solr-webapp``
  * The main method contains an instance of the feeder to feed if the endpoint does not deliver a response for a test document
* Point your browser to http://localhost:5678/
* The configuration file is ``apps/solr-webapp/src/main/resources/app.yml``


#### Features

* Highlighting
* ...

### 4) Solr request examples

* Get by field `id`: http://localhost:8983/solr/eumetsat/get?id=EO:EUM:DAT:INFO:LFDI
* Search everything, ask for fields `id` and `title`: http://localhost:8983/solr/eumetsat/query?q=*:*&fl=id,title
* Search with highlighting: http://localhost:8983/solr/eumetsat/query?q=water&fl=description&hl=true&hl.fragsize=0&hl.preserveMulti=true
* Search grouping by field `satellite_s`: http://localhost:8983/solr/eumetsat/query?q=water&fl=*&group=true&group.field=satellite_s
* Search with faceting parameters: http://localhost:8983/solr/eumetsat/query?q=water&fl=*&facet=true&facet.field=satellite_s&facet.field=keywords&facet.missing=true&facet.limit=4
* Search with filtering: http://localhost:8983/solr/eumetsat/query?q=climate&fl=*&facet=true&facet.field=category&fq=+category:cloud%20+category:level1

## Run in a servlet container

Build the webapp with the profile `warfile` and copy the created `.war`-file to the servlet container, such as Tomcat.

Also set the name of the web application in the configuration file `app.yml`.

```
cd <project dir>/apps/{elasticsearch, solr}-webapp
mvn clean install -P warfile
```

## Run standalone

An alternative to running from Java or in a servlet container is to create an executable jar, see [Step 11 of this tutorial](https://blog.openshift.com/developing-single-page-web-applications-using-java-8-spark-mongodb-and-angularjs/).

To create a standalone uber-jar for the Elasticsearch webapp:

```
cd <project dir>/apps/{elasticsearch, solr}-webapp
mvn clean install -P standalone
```

To run it open a command line, navigate to the directory of the created jar file and execute ``java -jar target/{elasticsearch, solr}-webapp-<version>.jar``.
