<!DOCTYPE html>
    <html lang="en">
    <head>
      <title>Search Page</title>
      <!-- Bootstrap core CSS -->
      <link href="css/bootstrap.min.css" rel="stylesheet">
      <!-- Bootstrap theme -->
      <link href="css/bootstrap-theme.min.css" rel="stylesheet">

      <!-- Custom styles for this template -->
      <link href="assets/theme.css" rel="stylesheet">

      <!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->
      <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
      <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
      <![endif]-->

      <style = text/css>
         !decrease navbar size
         .navbar-nav > li > a {padding-top:5px !important; padding-bottom:5px !important;}
         .navbar {min-height:32px !important}
      </style>

      <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
      <script>
        /*$(document).ready(function(){
          $("p").click(function(){
             $(this).hide();
          });
        });*/
    </script>
    </head>
    	
    <body role="document">
        
        <#include "navbar.ftl">

        <div class="jumbotron">
            <div class="container">
                <h1>Welcome!</h1>
                <p>This EUMETSAT Product Navigator Prototype is based on ${engine}</p>
            </div>
        </div>

    </body>
</html>
