package gov.usgs.wma.statistics.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.classmate.TypeResolver;

import gov.usgs.wma.statistics.model.JsonData;
import java.math.BigDecimal;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

	public static final String AliveService_ALIVE_NOTES     ="Informs that the service is running from the root path.";
	public static final String AliveService_APPROOT_NOTES   ="Responds in the same way as alive only from the application path.";
	
	public static final String InputService_FORM_NOTES      ="Returns an interactive HTML Form page.";
	
	public static final String VersionService_VERSION_NOTES ="Returns information about the application version and release.";
	public static final String VersionService_APPPATH_NOTES ="Responds in the same way as version only from the application path.";
	
	public static final String StatsService_MEDIANS_NOTES   ="Like the calculate endpoint, this returns statistics. This also returns the median montly data used in the statistics calculations. In order to weight each month equally, if there is more than one sample supplied in a give month/year then the median of that data will be used. For example, if there are three values for 2000-10: 10, 3, and 1, then 3 will be used for Oct 2000 statistics.";

	public static final String BOOLEAN_TRUE   ="true";
	public static final String BOOLEAN_FALSE  ="false";
	public static final String BOOLEAN_VALUES =BOOLEAN_TRUE+","+BOOLEAN_FALSE;
	
	public static final String StatsService_CALCULATE_NOTES      ="Returns the overal and montly statistics for the POST CSV data. The overal statistics include MIN, MAX, and Median values, MIN and MAX dates, Period of Record in years, Sample Count, Latest Percentile. The monthly statistics are assigned using the month number and inlcude the 10th, 25th, 50th, 75th, and 90th percentiles, the MIN and MAX 50th percetial, count of years with data.";
	public static final String StatsService_CALCULATE_DATA       ="CSV time series data of the format \"UTC, Value, [P]\" separated by new line in the POST body.";// Example:   " + StatsService_SERVICE_EXAMPLE;

	public static final String StatsService_MEDIATION_NAME       ="mediation";
	public static final String StatsService_MEDIATION_DEFAULT    ="BelowLand";
	public static final String StatsService_MEDIATION_VALUES     ="AboveDatum,BelowLand,NONE,ASCENDING,DESCENDING";
	public static final String StatsService_CALCULATE_MEDIATION  ="The value sort direction or the groundwater level "+StatsService_MEDIATION_NAME+". NONE means that the data is in value order as provided. The BelowLand default matches the exmaple data.";
	
	public static final String StatsService_MEDIANS_DEFAULT      =BOOLEAN_FALSE;
	public static final String StatsService_CALCULATE_MEDIANS    ="If true, returns the intermediate values used in the calculations. For monthly percentiles it is required that no month have more weight than any other. When a month has multiple values, the median is used. This feature returns these intermediate values. The defaule is " + StatsService_MEDIANS_DEFAULT;
	
	public static final String Days406 = "406";
	public static final String StatsService_ENFORCE_RECENT_DEFAULT    =BOOLEAN_TRUE;
	public static final String StatsService_CALCULATE_ENFORCE_RECENT  ="If true, the statistics will be calculated only if the most recent value is less than " + Days406 + " days old. The default value is " + StatsService_ENFORCE_RECENT_DEFAULT;

	public static final String StatsService_PERCENTILES_DEFAULT  ="10,25,50,75,90";
	public static final String StatsService_CALCULATE_PERCENTILES="A comma delimited list of percentiles like the default " + StatsService_PERCENTILES_DEFAULT;
	
	public static final String StatsService_EXAMPLE_ENCODED      ="data=2005-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2006-06-10T04%3A15%3A00-05%3A00%2C+22.000%0D%0A2007-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2008-06-10T04%3A15%3A00-05%3A00%2C+2.000%0D%0A2009-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2010-06-10T04%3A15%3A00-05%3A00%2C+20.000%0D%0A2011-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2012-06-10T04%3A15%3A00-05%3A00%2C+2.000%0D%0A2013-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2014-06-10T04%3A15%3A00-05%3A00%2C+10.000%0D%0A2015-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2016-06-10T04%3A15%3A00-05%3A00%2C+43.000%0D%0A2017-06-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2018-06-10T04%3A15%3A00-05%3A00%2C+11.000%0D%0A2005-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2006-07-10T04%3A15%3A00-05%3A00%2C+22.000%0D%0A2007-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2008-07-10T04%3A15%3A00-05%3A00%2C+2.000%0D%0A2009-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2010-07-10T04%3A15%3A00-05%3A00%2C+20.000%0D%0A2011-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2012-07-10T04%3A15%3A00-05%3A00%2C+2.000%0D%0A2013-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2014-07-10T04%3A15%3A00-05%3A00%2C+10.000%0D%0A2015-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2016-07-10T04%3A15%3A00-05%3A00%2C+43.000%0D%0A2017-07-10T04%3A15%3A00-05%3A00%2C+1.000%0D%0A2018-06-10T04%3A15%3A00-05%3A00%2C+11.000%0D%0A%09%09%09";
	public static final String StatsService_EXAMPLE_RAW
			="2005-06-10T04:15:00-05:00, 1.000\n"
			+"2006-06-10T04:15:00-05:00, 22.000\n"
			+"2007-06-10T04:15:00-05:00, 1.000\n"
			+"2008-06-10T04:15:00-05:00, 2.000\n"
			+"2009-06-10T04:15:00-05:00, 1.000\n"
			+"2010-06-10T04:15:00-05:00, 20.000\n"
			+"2011-06-10T04:15:00-05:00, 1.000\n"
			+"2012-06-10T04:15:00-05:00, 2.000\n"
			+"2013-06-10T04:15:00-05:00, 1.000\n"
			+"2014-06-10T04:15:00-05:00, 10.000\n"
			+"2015-06-10T04:15:00-05:00, 1.000\n"
			+"2016-06-10T04:15:00-05:00, 43.000\n"
			+"2017-06-10T04:15:00-05:00, 1.000\n"
			+"2018-06-10T04:15:00-05:00, 11.000\n"
			+"2005-07-10T04:15:00-05:00, 1.000\n"
			+"2006-07-10T04:15:00-05:00, 22.000\n"
			+"2007-07-10T04:15:00-05:00, 1.000\n"
			+"2008-07-10T04:15:00-05:00, 2.000\n"
			+"2009-07-10T04:15:00-05:00, 1.000\n"
			+"2010-07-10T04:15:00-05:00, 20.000\n"
			+"2011-07-10T04:15:00-05:00, 1.000\n"
			+"2012-07-10T04:15:00-05:00, 2.000\n"
			+"2013-07-10T04:15:00-05:00, 1.000\n"
			+"2014-07-10T04:15:00-05:00, 10.000\n"
			+"2015-07-10T04:15:00-05:00, 1.000\n"
			+"2016-07-10T04:15:00-05:00, 43.000\n"
			+"2017-07-10T04:15:00-05:00, 1.000\n"
			+"2018-06-10T04:15:00-05:00, 11.000\n";
	
	
	@Autowired
	private TypeResolver typeResolver;
	
	@Bean
	public Docket qwPortalServicesApi() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2)
			.useDefaultResponseMessages(false)
			// TODO are we going to have a public host exposure
			//.host(SwaggerDisplayHost)
			// TODO do we need to block http?
			//.protocols(new HashSet<>(Arrays.asList("https")))
			.additionalModels(typeResolver.resolve(JsonData.class))
			.select()
			.paths(PathSelectors.any())
			.apis(RequestHandlerSelectors.basePackage("gov.usgs.wma.statistics"))
			.build();
		return docket;
	}
	
	
}
