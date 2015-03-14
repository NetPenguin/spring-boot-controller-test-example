package sandbox;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fooes")
public class FooController {
	@Autowired
	private BarService barService;

	@RequestMapping(method = RequestMethod.GET, value = "/{id}")
	public Bar get(@PathVariable int id) {
		return barService.getBar(id);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/", params = "name")
	public List<Bar> list(@RequestParam String name) {
		return barService.findBarByName(name);
	}
}
