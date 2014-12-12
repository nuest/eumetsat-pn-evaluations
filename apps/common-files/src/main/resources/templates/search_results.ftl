<!DOCTYPE html>
<html>
    <head>
        <title>EUMETSAT PN - Search results for '${search_terms!""}'</title>

        <#include "head.ftl">

              <script> 
                    <#-- TODO: move this to pn.js -->

                  <!-- code to change url on facets dynamically -->
                  $(document).ready(function() {
                                            $("a.facetref").click(function(event){		
                                                    var curr_url = $(location).attr('href');

                                                    // add new element
                                                    var new_filter = $(this).attr("data-filter");

                                                    curr_url += "+" + new_filter;

                                                    // reset from to 0 everytime
                                                    curr_url = curr_url.replace(/from=\d+/g, "from=0");

                                                    //alert("You clicked me! " + curr_url);

                                                    event.preventDefault();
                            location.href = curr_url;

                                            });

                                            // to change dynamically the URL displayed by the browser
                                            // intercept the mouseover event and change the url
                                            $("a.facetref").on('mouseover', function(event){	

                                                var curr_url = $(location).attr('href');

                                                    // replace from with the new from
                                                    var new_filter = $(this).attr("data-filter");

                                                    // reset from to 0 everytime
                                                    curr_url = curr_url.replace(/from=\d+/g, "from=0");

                                                    // to be replaced by a working regexpr
                                                    curr_url += "+" + new_filter;
                                                    //curr_url = curr_url.replace(/filter-terms=\+\w:\w/g, "$&" + new_filter);

                                                    $(this).attr("href", curr_url);
                                            });

                                            // change dynamically the url on click
                                            $("a.pagiref").on('click', function(event){		
                                                    var curr_url = $(location).attr('href');

                                                    // replace from with the new from
                                                    var from = $(this).attr("data-from");

                                                    curr_url = curr_url.replace(/from=\d+/g, "from=" + from);

                                                    event.preventDefault();
                            location.href = curr_url;

                                            });

                                            // to change dynamically the URL displayed by the browser
                                            // intercept the mouseover event and change the url
                                            $("a.pagiref").on('mouseover', function(event){	

                                                var curr_url = $(location).attr('href');

                                                    // replace from with the new from
                                                    var from = $(this).attr("data-from");

                                                    curr_url = curr_url.replace(/from=\d+/g, "from=" + from);

                                                    $(this).attr("href", curr_url);
                                            });
                              });
              </script>
    </head>

    <body>
        <div class="container-fluid">

        <#include "navbar.ftl">

        <#include "messages.ftl">

        <div class="row"><#-- row 2 contains result summary and meta-information -->
            <div class="col-md-2">&nbsp;</div>
            <div class="col-md-10">
                <#if total_hits?? && elapsed??>
                    <p class="text-muted">
                        <small>${total_hits} results (in ${elapsed} milliseconds) <#if tohide??> | Active filters: ${tohide?join(", ")?replace(":", ": ")}</#if></small>
                    </p>
                </#if>
            </div>
        </div>

        <div class="row"><#-- row 3 with the results -->

            <#-- facet column -->
            <#if facets??>
                <div class="col-md-2">
                    <ul id="sidebar" class="nav nav-stacked affix">
                        <#list facets?keys as facet>
                           <#--<a href="#sec0">${facet?cap_first}<span class="badge pull-right">${facets[facet].total}</span></a>-->
                           <li style="margin-top: 0.3em;">
                                <h6>${facet?cap_first}</h6>
                                <ul class="nav">
                                    <#list facets[facet].terms as aterm>
                                       <#if ! tohide?seq_contains("${facet}:${aterm.term}")>
                                            <li><small>
                                            <a class="facetref" data-filter="${facet?replace(" ","")}:${aterm.term}" href="${search_endpoint}?search-terms=${search_terms}&from=0&size=10&filter-terms=${facet}:${aterm.term}">${aterm.term?lower_case}<span class="badge pull-right">${aterm.count}</span>
                                            </a></small></li>
                                       </#if>
                                    </#list>
                                </ul>
                            </li>
                        </#list>
                    </ul>
                </div>
            </#if>

            <#-- result column -->
            <div class="col-md-10" id="resultlist">
                <#if hits??>
                    <ul class="list-unstyled">
                    <#list hits as hit>
                        <li>
                            <#if hit.thumbnail?? && hit.thumbnail != "">
                                <img src="${hit.thumbnail}" width="80" alt="Thumbnail for ${hit.id}"/>
                            </#if>
                            <h5><a href="${description_endpoint}?id=${hit.id}">${hit.title}</a>&nbsp<#if hit.status?? && hit.status != ""><span class="badge">${hit.status}</span></#if></h5>
                            <p class="text-justify">${hit.abstract}
                                
                            </p>
                            <p class="text-muted"><small>Score: ${hit.score} | Keywords: ${hit.keywords} | ID: ${hit.id}</small></p>
                        </li>
                    </#list>
                    </ul>
                </#if>
            </div>
         </div><#-- row 3 -->

        <#if pagination??>
            <div class="row"><#-- row 4: pagination -->
                <div class="text-center">
                       <ul class="pagination">
                         <#assign curr=pagination.current_page>
                         <#if curr == 0 >
                            <li class="disabled"><a href="#">&laquo;</a></li>
                         <#else>
                            <li class="#"><a class="pagiref" data-from="${pagination.elem_per_page * (curr -1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (curr -1)}&size=${pagination.elem_per_page}&filter-terms=${filter_terms}">&laquo;</a></li>
                         </#if>
                         <#list 1..pagination.nb_pages as index>
                            <#if curr == (index-1)>
                               <li class="active"><a href="#">${index}</a></li>
                            <#else>
                               <li><a class="pagiref" data-from="${pagination.elem_per_page * (index - 1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (index - 1)}&size=${pagination.elem_per_page}&filter-terms=${filter_terms}">${index}</a></li>
                            </#if>
                         </#list>
                         <#if curr == (pagination.nb_pages-1) >
                            <li class="disabled"><a href="#">&laquo;</a></li>
                         <#else>
                            <li class="#"><a class="pagiref" data-from="${pagination.elem_per_page * (curr + 1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (curr +1)}&size=${pagination.elem_per_page}&filter-terms=${filter_terms}">&raquo;</a></li>
                         </#if>
                       </ul>
                </div>
            </div>
         </#if>

    </body>
</html>