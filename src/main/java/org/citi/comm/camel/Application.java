package org.citi.comm.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twilio.TwilioComponent;
import org.apache.camel.component.twitter.AbstractTwitterComponent;
import org.apache.camel.component.twitter.timeline.TwitterTimelineComponent;
import org.apache.camel.component.twitter.data.TimelineType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.twilio.type.PhoneNumber;

@SpringBootApplication
@ComponentScan(basePackages="org.citi.comm.camel") 
@PropertySource("classpath:onboarding.properties")
public class Application{

    // Twitter

    @Value("${twitter.consumer.key}")
    String twitterConsumerKey;

    @Value("${twitter.consumer.secret}")
    String twitterConsumerSecret;

    @Value("${twitter.access.token}")
    private String twitterAccessToken;

    @Value("${twitter.access.token.secret}")
    private String twitterAccessTokenSecret;

    @Value("${twitter.target.user}")
    private String twitterTargetUser;

    // Twilio

    @Value("${twilio.username}")
    private String twilioUsername;

    @Value("${twilio.password}")
    private String twilioPassword;

    @Value("${twilio.number.from}")
    private String twilioNumberFrom;

    @Value("${twilio.number.to}")
    private String twilioNumberTo;
    
    @Value("${server.port}")
    String serverPort;
    
    @Value("${citi.api.path}")
    String contextPath;
    
    @Autowired
    Environment clientBankerMap;
    
    static String body = null;
    
    public static void main(String[] args) throws Exception {
    	SpringApplication.run(Application.class, args);
    }    
    
    @Bean
    ServletRegistrationBean servletRegistrationBean() {
        ServletRegistrationBean servlet = new ServletRegistrationBean();
    	servlet.setName("CamelServlet");
        return servlet;
    }

    @Component
    class RestClientBankerRouter extends RouteBuilder {
    
        @Override
        public void configure() throws Exception {   
            setupTwitter(getContext().getComponent("twitter-timeline", TwitterTimelineComponent.class));
            setupTwilio(getContext().getComponent("twilio", TwilioComponent.class));
            
            //from twilio
			rest("/fromTwilio").id("citi-handle")
			  .post()
			  .route()
			  .log("${header.body}")
			  .log("${header.from}")
			  .log("${header.to}")
	          .process(new Processor() {
	            public void process(Exchange exchange) throws Exception {
              	body = exchange.getIn().getHeader("Body", String.class);
              	exchange.getIn().setBody(body);
	            }
	          })
	          .setHeader("Content-Type", simple("application/xml"))
	          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
	          .to("twitter-timeline://"+clientBankerMap.getProperty("${header.from}"));
			
			//from twitter
            //home?type=polling&delay=60&consumerKey=[s]&consumerSecret=[s]&accessToken=[s]&accessTokenSecret=[s]
            from("twitter-timeline://home?type=polling&delay=6000000&consumerKey=[s]&consumerSecret=[s]&accessToken=[s]&accessTokenSecret=[s]")
	                .filter(simple("${body.text} starts with 'SEND: '"))
	                .log("tweet = ${body.text}")
	                .setBody(simple("${body.text.substring(6)}")) 
	                .log("message = ${body}")
	                .setHeader("CamelTwilioTo", constant(new PhoneNumber("${header.to}")))
	                .setHeader("CamelTwilioFrom", constant(new PhoneNumber("${header.from}")))
	                .setHeader("CamelTwilioBody", simple("${body} from your banker "))
	                .toD("twilio://message/create");


        }
    }       
        
        private void setupTwitter(AbstractTwitterComponent twitter) {
            twitter.setConsumerKey(twitterConsumerKey);
            twitter.setConsumerSecret(twitterConsumerSecret);
            twitter.setAccessToken(twitterAccessToken);
            twitter.setAccessTokenSecret(twitterAccessTokenSecret);
        }
        
        private void setupTwilio(TwilioComponent twilio) {
            twilio.getConfiguration().setUsername(twilioUsername);
            twilio.getConfiguration().setPassword(twilioPassword);
        }
}

