package sandbox;

import java.util.Arrays;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestHelper {
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private HandlerExceptionResolver handlerExceptionResolver;
	@Autowired
	private ObjectMapper objectMapper;

	public MockMvc mvc(Object controller) {
		StandaloneMockMvcBuilder builder = new StandaloneMockMvcBuilder(controller) {
			@Override
			protected WebApplicationContext initWebAppContext() {
				WebApplicationContext context = super.initWebAppContext();
				StaticListableBeanFactory beanFactory = (StaticListableBeanFactory)context.getAutowireCapableBeanFactory();

				Arrays.stream(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class))
					.filter(name -> applicationContext.findAnnotationOnBean(name, ControllerAdvice.class) != null)
					.forEach(name -> beanFactory.addBean(name, applicationContext.getBean(name)));

				context.getBean(RequestMappingHandlerAdapter.class).afterPropertiesSet();
				return context;
			}
		};
		return builder.setHandlerExceptionResolvers(handlerExceptionResolver).build();
	}

	public String toJson(Object value) throws JsonProcessingException {
		return objectMapper.writeValueAsString(value);
	}
}
