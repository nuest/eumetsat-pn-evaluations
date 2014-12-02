# EUMETSAT Elasticsearch and Solr Evaluations

Comparison of search frameworks for the EUMETSAT Product Navigator

## Elasticsearch

Quick guide to launch the prototype for [Elasticsearch](http://www.elasticsearch.org/).

### 1) Run ElasticSearch

Download Elasticsearch at http://www.elasticsearch.org/overview/elkdownloads/.

Run Elasticsearch with the configuration provided in this repository:

```
cd /<path-to-elasticsearch>/bin/
elasticsearch(.bat) -Des.config=/<path-to-local-repo>/apps/elasticsearch-webapp/src/main/resources/elasticsearch.yml
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

### 3) Search web app

The web app is based a on a tiny web framework called [Spark](http://www.sparkjava.com) meant for rapid development. Web pages are built using the simple template engine [Freemarker](http://freemarker.org), Boostrap, and JQuery.

Elastic Search is accessed using the REST interface with a REST client.

* Run ``eumetsat.pn.elasticsearch.webapp.ElasticsearchApp.main()`` in the module ``apps/elasticsearch-webapp``
* Point your browser to http://localhost:4567/
* The configuration file is ``apps/elasticsearch-webapp/src/main/resources/app.yml``

#### Features

* Search is based on having different weights for the different parts of the record (Title, Abstract).
* Use Facetted search to filter out results.
* Highlight found keywords in search results.
* Bookmarkable search.

### 4) Elasticsearch request examples

In elastic-experiment/etc there are also multiple curl requests to experiment with Elasticsearch


## Solr

Quick guide to launch the prototype for [Solr](http://lucene.apache.org/solr/).

### 1) Run Solr

Download and unzip Solr from http://lucene.apache.org/solr/mirrors-solr-latest-redir.html. Install and configure it based on the [quickstart tutorial](http://lucene.apache.org/solr/quickstart.html).

http://stackoverflow.com/questions/7904802/running-solr-in-memory
solrconfig.xml > <directoryFactory name="DirectoryFactory" class="solr.RAMDirectoryFactory"/>

```
cd /<path-to-solr>/bin/
solr start -f -V -s C:\Users\danu\Documents\2014_EUMETSAT\workspace\eumetsat-pn-evaluations\apps\solr-webapp\src\main\resources\

```

### 2) Index metadata records

* Run ``..``



### 3) Search web app

* Run ``eumetsat.pn.solr.webapp.SolrApp.main()`` in the module ``apps/solr-webapp``
* Point your browser to http://localhost:5678/
* The configuration file is ``apps/solr-webapp/src/main/resources/app.yml``


#### Features

* ...

### 4) Solr request examples

...