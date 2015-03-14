package sandbox;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContext.class)
@WebAppConfiguration
public class FooControllerTest {

	@Rule
	public MockitoJUnitRule mockitoJUnitRule = new MockitoJUnitRule(this);
	@InjectMocks
	private FooController target;
	@Mock
	private BarService barService;

	@Autowired
	private TestHelper helper;

	@Test
	public void testGet__Ok() throws Exception {
		int id = 123;
		Bar bar = new Bar(id, "bar-" + id);
		when(barService.getBar(anyInt())).thenReturn(bar);

		helper.mvc(target)
			.perform(get("/fooes/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().json(helper.toJson(bar)));

		verify(barService).getBar(id);
	}

	@Test
	public void testGet__NotFound() throws Exception {
		String message = "__message__";
		when(barService.getBar(anyInt())).thenThrow(new EntityNotFoundException(message));

		int id = 123;
		helper.mvc(target)
			.perform(get("/fooes/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(content().json(helper.toJson(new ErrorResponse("EntityNotFoundException", message))));

		verify(barService).getBar(id);
	}

	@Test
	public void testList__Ok() throws Exception {
		String name = "bar-123";
		Bar bar = new Bar(123, name);
		when(barService.findBarByName(anyString())).thenReturn(Arrays.asList(bar));

		helper.mvc(target)
			.perform(get("/fooes/").param("name", " " + name + " "))
			.andExpect(status().isOk())
			.andExpect(content().json(helper.toJson(new Bar[]{bar})));

		verify(barService).findBarByName(name);
	}
}
