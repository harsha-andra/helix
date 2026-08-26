package com.harshaandra.helix.api.soap;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Publishes the SOAP endpoint at /ws and the generated WSDL at /ws/claims.wsdl.
 *
 * The WSDL is generated from claims.xsd at startup rather than hand-maintained, so it can never
 * drift from the schema the JAXB classes were generated from.
 */
@EnableWs
@Configuration
public class SoapWebServiceConfig extends WsConfigurerAdapter {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Required so the servlet can serve the WSDL from the same path it serves SOAP calls.
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "claims")
    public DefaultWsdl11Definition claimsWsdl(XsdSchema claimsSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("ClaimsPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace("http://harsha-andra.dev/helix/claims");
        definition.setSchema(claimsSchema);
        return definition;
    }

    @Bean
    public XsdSchema claimsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/claims.xsd"));
    }
}
