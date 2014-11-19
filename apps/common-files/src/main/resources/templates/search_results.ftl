<!DOCTYPE html>
<html>
<head>
    	  <title>Search Page</title>
          <!-- Bootstrap core CSS -->
          <link href="../css/bootstrap.min.css" rel="stylesheet">
          <!-- Bootstrap theme -->
          <link href="../css/bootstrap-theme.min.css" rel="stylesheet">
          

          <!-- Custom styles for this template -->
          <link href="../assets/theme.css" rel="stylesheet">

          <!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->
          <!--[if lt IE 9]>
          <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
          <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
          <![endif]-->
          
          <style = text/css>
             !decrease navbar size
             .navbar-nav > li > a {padding-top:5px !important; padding-bottom:5px !important;}
             .navbar {min-height:32px !important}
             
             .badge {
  				padding: 1px 9px 2px;
  				font-size: 09.025px;
  				font-weight: bold;
  				white-space: nowrap;
  				color: #ffffff;
  				background-color: #999999;
  				-webkit-border-radius: 9px;
  				-moz-border-radius: 9px;
  				border-radius: 9px;
			  }
			  .badge:hover {
  				color: #ffffff;
  				text-decoration: none;
  				cursor: pointer;
			  }
          </style>
          
          <script src="../js/jquery-1.11.0.js"></script>
          <script> 
          
          
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

    <div class="row"> <!-- row 2 contains hits and so on -->
        <div class="col-md-2">&nbsp;</div>
        <div class="col-md-10">
            </br>
            <p class="text-muted">
                <small>About ${total_hits} results (${elapsed} milliseconds)</small>
            </p>
        </div>
    </div>

  <div class="row"> <!-- row 3 with the results -->
	  <div class="col-md-2">
	        <ul id="sidebar" class="nav nav-stacked affix">
                    <#if facet??>
                        <#list facets?keys as facet>
                           <!--<a href="#sec0">${facet?cap_first}<span class="badge pull-right">${facets[facet].total}</span></a>-->
                           <p>${facet?cap_first}</p>
                           <ul class="nav">
                                   <#list facets[facet].terms as aterm>
                                      <#if ! tohide?seq_contains("${facet}:${aterm.term}")>
                                        <li><small><a class="facetref" data-filter="${facet?replace(" ","")}:${aterm.term}" href="/search/results?search-terms=${search_terms}&from=0&size=10&filter-terms=${facet}:${aterm.term}"> --- ${aterm.term?lower_case}<span class="badge pull-right">${aterm.count}</span></small></a></li>
                                      </#if>
                                   </#list>
                           </ul>
                        </#list>
                    </#if>
           </ul>
	  </div>
	  <div class="col-md-10">
            <#if list??>
                <#list hits as hit>
                    <h5><a href="/product_description?id=${hit.id}">${hit.title}</a></h5>
                    <p class="text-justify">
                      ${hit.abstract} 
                    </p>
                    <p class="text-muted"><small>score: ${hit.score}</small></p>
                    <hr>
                </#list>
            </#if>
	  </div>
 </div> <!-- row 3 -->

<#if pagination??>
    <div class="row"> <!-- row 4: pagination -->
        <div class="text-center">
               <ul class="pagination">
                 <#assign curr=pagination.current_page>
                 <#if curr == 0 >
                    <li class="disabled"><a href="#">&laquo;</a></li>
                 <#else>
                    <li class="#"><a class="pagiref" data-from="${pagination.elem_per_page * (curr -1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (curr -1)}&size=${pagination.elem_per_page}">&laquo;</a></li>
                 </#if>
                 <#list 1..pagination.nb_pages as index>
                    <#if curr == (index-1)>
                       <li class="active"><a href="#">${index}</a></li>
                    <#else>
                       <li><a class="pagiref" data-from="${pagination.elem_per_page * (index - 1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (index - 1)}&size=${pagination.elem_per_page}">${index}</a></li>
                    </#if>
                 </#list>
                 <#if curr == (pagination.nb_pages-1) >
                    <li class="disabled"><a href="#">&laquo;</a></li>
                 <#else>
                    <li class="#"><a class="pagiref" data-from="${pagination.elem_per_page * (curr + 1)}" href="/search/results?search-terms=${search_terms}&from=${pagination.elem_per_page * (curr +1)}&size=${pagination.elem_per_page}">&raquo;</a></li>
                 </#if>
               </ul>
        </div>
    </div>
 </#if>

</body>
</html>