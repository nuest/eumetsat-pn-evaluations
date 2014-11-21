<#if user_messages?? && (user_messages?keys?size > 0)>
    <div class="row"><#-- messages -->
        <div class="col-md-12">
            <#list user_messages?keys as key>
                <div class="alert alert-${user_messages[key]} alert-dismissible" role="alert">
                    <button type="button" class="close" data-dismiss="alert"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>
                    <p>${key}</p>
                </div>
            </#list>
        </div>
    </div>
</#if>