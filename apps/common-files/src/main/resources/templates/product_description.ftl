<!DOCTYPE html>
<html>
    <head>
        <title>EUMETSAT PN - ${title!""}</title>

        <#include "head.ftl">
    </head>
    <body>

        <div class="container-fluid">

            <#include "navbar.ftl">

            <#include "messages.ftl">

            <div class="row">
                <div class="col-md-12">
                    <h2>${title}</h2>
                    
                    <p class="text-justify">${abstract}</p>

                    <#if keywords?? && keywords != "">
                        <p><strong>Keywords</strong>: ${keywords}</p>
                    </#if>

                    <#if status?? && status != "">
                        <p><strong>Status</strong>: ${status}</p>
                    </#if>

                    <#if satellite?? && satellite != "">
                        <p><strong>Satellite</strong>: ${satellite}</p>
                    </#if>

                    <#if thumbnail?? && thumbnail != "">
                        <img src="${thumbnail}" alt="Thumbnail for ${id}"/>
                    </#if>
                </div>
            </div><#-- row 1 -->
        </div>
    </body>
</html>