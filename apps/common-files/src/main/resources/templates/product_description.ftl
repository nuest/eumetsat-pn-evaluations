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

                    <#if distribution?? && distribution != "">
                        <p><strong>Distribution</strong>: ${distribution}</p>
                    </#if>

                    <#if sba?? && sba != "">
                        <p><strong>SBA</strong>: ${sba}</p>
                    </#if>

                    <#if category?? && category != "">
                        <p><strong>Category</strong>: ${category}</p>
                    </#if>

                    <#if instrument?? && instrument != "">
                        <p><strong>Instrument</strong>: ${instrument}</p>
                    </#if>

                    <#if boundingbox?? && boundingbox != "">
                        <p><strong>Bounding box</strong>: ${boundingbox}</p>
                    </#if>

                    <#if address?? && address != "">
                        <p><strong>Address</strong>: ${address}</p>
                    </#if>

                    <#if email?? && email != "">
                        <p><strong>E-Mail</strong>: ${email}</p>
                    </#if>

                    <#if address?? && address != "">
                        <p><strong>Address</strong>: ${address}</p>
                    </#if>

                    <#if thumbnail?? && thumbnail != "">
                        <img src="${thumbnail}" alt="Thumbnail for ${id}" class="detailimage" />
                    </#if>

                    <#if xmldoc?? && xmldoc != "">
                        <div style="clear: both;">
                            <p><button type="button" class="btn btn-info" data-toggle="collapse" data-target="#xmldoc">Show XML</button></p>
                            <div id="xmldoc" class="collapse">
                                <textarea class="rawxml" cols="100" rows="12">${xmldoc}</textarea>
                            </div>
                        </div>
                    </#if>

                </div>
            </div><#-- row 1 -->
        </div>
    </body>
</html>