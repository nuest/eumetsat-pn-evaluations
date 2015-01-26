<!-- Fixed navbar -->
<div class="navbar navbar-inverse navbar-fixed-top" role="navigation">
    <div class="container-fluid">
      <!-- Brand and toggle get grouped for better mobile display -->
      <div class="navbar-header">
        <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
          <span class="sr-only">Toggle navigation</span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
        </button>
        <a class="navbar-brand" href="/">EUMETSAT PN - ${engine}</a>
      </div>

<script type="text/javascript">
$(function() {
    $("#searchform").autocomplete({
        source: function(request, response) {
            /*var wildcard = { "title": "*" + request.term.toLowerCase() + "*" };
            var postData = {
            "query": { "wildcard": wildcard },
            "fields": ["title", "_id", "abstract", "keywords"]
            };*/
            var postData = {
                "fields" : [],
                 "query":{  
                    "match_all":{ }
                 },
                 "suggest":{  
                    "text": request.term.toLowerCase(),
                    "mysuggest":{  
                       "term":{  
                            "field":"_all",
                            "min_word_len": 2
                       }
                    }
                }
            };

            $.ajax({
                url: "${autocomplete_endpoint}",
                type: "POST",
                dataType: "JSON",
                data: JSON.stringify(postData),
                success: function(data) {
                    // only support one word
                    response($.map(data.suggest.mysuggest[0].options, function(item) {
                        return {
                            label: item.text,
                            id: item.text
                        }
                    }));
                },
                });
            },
        minLength: 2,
        select: function( event, ui ) {
                $("#searchform").val(ui.item.id);
            },
        open: function() {
                $( this ).removeClass( "ui-corner-all" ).addClass( "ui-corner-top" );
            },
        close: function() {
                $( this ).removeClass( "ui-corner-top" ).addClass( "ui-corner-all" );
            }
    })
}); 
</script>

      <!-- Collect the nav links, forms, and other content for toggling -->
      <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-1">
        <form class="navbar-form navbar-left" role="search" method='GET' action="${search_endpoint}">
          <div class="form-group">
            <input id="searchform" name="search-terms" type="text" value="${search_terms!""}" class="form-control" placeholder="Search">
                <input type="hidden" name="from" value="0" />
                <input type="hidden" name="size" value="${elem_per_page}" />
                <input type="hidden" name="filter-terms" value=""/>
          </div>
          <button type="submit" class="btn btn-default">Submit</button>
        </form>
      </div><!-- /.navbar-collapse -->
    </div><!-- /.container-fluid -->
</div>