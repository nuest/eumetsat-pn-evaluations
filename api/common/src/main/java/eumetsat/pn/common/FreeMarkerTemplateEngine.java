/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import spark.ModelAndView;
import spark.TemplateEngine;

/**
 *
 * see README.md at https://github.com/perwendel/spark/
 */
public class FreeMarkerTemplateEngine extends TemplateEngine {

    private final Configuration configuration;

    protected FreeMarkerTemplateEngine(Configuration config) {
        this.configuration = config;
    }

    @Override
    public String render(ModelAndView modelAndView) {
        try {
            StringWriter stringWriter = new StringWriter();

            Template template = configuration.getTemplate(modelAndView.getViewName());
            template.process(modelAndView.getModel(), stringWriter);

            return stringWriter.toString();
        } catch (TemplateException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

//    private Configuration createFreemarkerConfiguration() {
//        Configuration retVal = new Configuration();
//        retVal.setClassForTemplateLoading(FreeMarkerTemplateEngine.class, "freemarker");
//        return retVal;
//    }
}
