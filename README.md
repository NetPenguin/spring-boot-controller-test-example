# Spring-Boot の @RestController の単体テストを記述する

最近、Spring-Boot を触っています。
Spring-Boot 自体の使い方は、Google 先生に聞けばだいたい教えてくれるので、@RestController なコントローラの単体テストについて書いておきます。

「単体テスト」といえば対象クラスの動作に対するテストの事かと思いますが、コントローラの場合はフレームワークの設定と動作もある程度加味しないとテストから漏れてしまう箇所が多くなる or 結合テスト(手動/自動問わず)で細かな部分までテストすることになってしまうかと思います。

Spring-Boot には `spring-boot-starter-test` というテスト用モジュールがあり、これの中にあるコントローラに対するテスト用のヘルパクラスを利用することで、上述したフレームワークの設定と動作も含めたコントローラのテストを容易に記述できます。

## pom.xml

pom.xml の依存モジュールに、`spring-boot-starter-test` を追加します。また、レスポンスボディを JSON として検証する場合は `org.skyscreamer:jsonassert` も必要なようです。

```xml:pom.xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-test</artifactId>
	<scope>test</scope>
</dependency>
<dependency>
	<groupId>org.skyscreamer</groupId>
	<artifactId>jsonassert</artifactId>
	<version>1.2.3</version>
	<scope>test</scope>
</dependency>
```

`scope` を `test` にして、リリースパッケージに含まれないようにします。

## 単順な RestController のテストの場合

https://spring.io/guides/gs/spring-boot/ で説明されている内容とほぼ変わらないです。

```java:FooControllerTest.java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class FooControllerTest {

	private MockMvc mvc;

	@Before
	public void before() throws Exception {
		mvc = MockMvcBuilders.standaloneSetup(new FooController()).build();
	}

	@Test
	public void testGet__Ok() throws Exception {
		int id = 123;
		mvc.perform(get("/fooes/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string("foo-" + id));
	}
}
```

`MockMvcBuilders.standaloneSetup(...)` を使うことで、Spring MVC のモックを使用したコントローラのテストをすることができます。

上記の例では、レスポンスは文字列になっていますが、実際には JSON オブジェクトの場合が多いかと思います。レスポンスが JSON の場合は `content().json(String jsonContent)` を使うことで検証ができます。ただ、この検証メソッドですが期待値に JSON 文字列を要求するので、実際の場合はコントローラで返したはずのエンティティから JSON 文字列への変換が必要です。
Spring Boot ではデフォルトで Jackson を使用した JSON 文字列への変換がサポートされているので、テストケースでも Spring Boot で設定されている Jackson を使用したいと思います。

```java:FooControllerTest.java
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
// 省略

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JacksonAutoConfiguration.class)
public class FooControllerTest {

    @Autowired
    private ObjectMapper mapper;

    // 省略

	@Test
	public void testGet__Ok() throws Exception {
		int id = 123;
		mvc.perform(get("/fooes/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().json(mapper.writeValueAsString(new Foo(id, "foo-" + id)));
	}
}
```

通常は `@EnableAutoConfiguration` の指定によって自動ロードする Jackson の設定クラスですが、`@Configuration` なクラスであることに変わりはないので、`@SpringApplicationConfiguration` を使用して明示的にロードしています。
TestRunner として `SpringJUnit4ClassRunner` を使用することで、`@SpringApplicationConfiguration` によるテスト用 Context を指定することができます。


## Service をモックする

実際のコントローラの実装では、Service やその他いろいろなコンポーネントを利用しているかと思います。それらのコンポーネントをモックに入れ替えてテストします。

```java:FooControllerTest.java
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.Rule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;
// 省略

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JacksonAutoConfiguration.class)
public class FooControllerTest {

	// 省略

	@Rule
	public MockitoJUnitRule mockitoJUnitRule = new MockitoJUnitRule(this);
	@InjectMocks
	private FooController target;
	@Mock
	private BarService barService;

	@Before
	public void before() throws Exception {
		mvc = MockMvcBuilders.standaloneSetup(target).build();
	}

	@Test
	public void testGet__Ok() throws Exception {
		int id = 123;
		Bar bar = new Bar(id, "bar-" + id);
		when(barService.getBar(id)).thenReturn(bar);

		mvc.perform(get("/fooes/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().json(mapper.writeValueAsString(bar)));

		verify(barService).getBar(id);
	}
}
```

`spring-boot-starter-test` ではモックライブラリとして Mockito が使えるようになっているでの、それを使用します。

`@RunWith` にはすでに `SpringJUnit4ClassRunner.class` を指定しているので、`MockitoJUnitRunner.class` は指定できません。かわりに `@Rule` を使用して `MockitoJUnitRule` を設定しています。

## @ControllerAdvice による例外ハンドラを有効にする

最近の Spring MVC では、複数のコントローラで共通になる `@ExceptionHandler` や `@InitBinder` のメソッドを、`@ControllerAdvice` をつけたクラスで定義できます。
なのですが、`MockMvcBuilders.standaloneSetup` で対応されていないため、そのままでは単体テスト時に `@ControllerAdvice` による設定が有効になりません。

`ExceptionHandlerExceptionResolver` のインスタンスを `MockMvcBuilder` に設定することで、`@ControllerAdvice` による例外ハンドラがテスト時も有効になります。
`WebMvcConfigurationSupport` を使用することで `HandlerExceptionResolver` を含むいくつかのコンポーネントを利用出来るようになります。

`@ControllerAdvice` なコンポーネントと、`WebMvcConfigurationSupport` を有効にするために、テスト用の `@Configuration` クラスを作成します。

```java:TestContext.java
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@ComponentScan(
		basePackageClasses = FooController.class,
		useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(ControllerAdvice.class))
public class TestContext extends WebMvcConfigurationSupport {
}
```
`@ControllerAdvice` のコンポーネントだけが有効になるよう、`useDefaultFilters` と `includeFilters` を指定しています。


上記の `TestContext.class` をテストクラスに設定し、また `WebMvcConfigurationSupport` は `ServletContext` を必要とするため、`@WebAppConfiguration` も指定します。

```java:FooControllerTest.java
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.HandlerExceptionResolver;
// 省略

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {JacksonAutoConfiguration.class, TestContext.class})
@WebAppConfiguration
public class FooControllerTest {

	@Autowired
	private HandlerExceptionResolver handlerExceptionResolver;

	// 省略

	@Before
	public void before() throws Exception {
		mvc = MockMvcBuilders.standaloneSetup(target)
			.setHandlerExceptionResolvers(handlerExceptionResolver)
			.build();
	}

	// 省略

	@Test
	public void testGet__NotFound() throws Exception {
		String message = "__message__";
		when(barService.getBar(anyInt())).thenThrow(new EntityNotFoundException(message));

		int id = 123;
		mvc.perform(get("/fooes/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(content().json(mapper.writeValueAsString(new ErrorResponse("EntityNotFoundException", message))));

		verify(barService).getBar(id);
	}
}
```

## @ControllerAdvice による WebDataBinder 設定を有効にする

`@ExceptionHandler` の場合は `StandaloneMockMvcBuilder` にそのための設定メソッドがあったのですが、`@InitBinder` のための設定メソッドは残念ながら無いようです。
そのため `StandaloneMockMvcBuilder` を継承して、やや無理やりな方法ですが `RequestMappingHandlerAdapter` に `@ControllerAdvice` なコンポーネントが設定されるようにします。

```java:FooControllerTest.java
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
// 省略

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {JacksonAutoConfiguration.class, TestContext.class})
@WebAppConfiguration
public class FooControllerTest {

	@Autowired
	private ApplicationContext applicationContext;

	// 省略

	@Before
	public void before() throws Exception {
		StandaloneMockMvcBuilder builder = new StandaloneMockMvcBuilder(target) {
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

		mvc = builder.setHandlerExceptionResolvers().build();
	}

	// 省略

	@Test
	public void testList__Ok() throws Exception {
		String name = "bar-123";
		Bar bar = new Bar(123, name);
		when(barService.findBarByName(anyString())).thenReturn(Arrays.asList(bar));

		mvc.perform(get("/fooes/").param("name", " " + name + " "))
			.andExpect(status().isOk())
			.andExpect(content().json(mapper.writeValueAsString(new Bar[]{bar})));

		verify(barService).findBarByName(name);
	}
}
```

`@ControllerAdvice` なクラスの `@InitBinder` メソッドで、`StringTrimmerEditor` を設定しています。そのため、クエリパラメータの前後の空白は除去されてからコントローラのメソッドに渡されています。

## 再利用しやすい形にする

上述した設定や修正を、複数のコントローラ用テストで使えるよう、ヘルパクラスを用意します。
また、いくつかの設定については、テスト用コンテキストクラスに移動します。

```java:TestHelper.java
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
```

```java:TestContext.java
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@ComponentScan(
		basePackageClasses = FooController.class,
		useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(ControllerAdvice.class))
@Import(JacksonAutoConfiguration.class)
public class TestContext extends WebMvcConfigurationSupport {
	@Bean
	public TestHelper testHelper() {
		return new TestHelper();
	}
}
```

上記を利用することで、テスト対象に直接関係しない処理をテストケースから追い出すことができます。

```java:FooControllerTest.java
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
```
