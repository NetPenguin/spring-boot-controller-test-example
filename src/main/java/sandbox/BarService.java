package sandbox;

import java.util.List;

public interface BarService {
	Bar getBar(int id);

	List<Bar> findBarByName(String name);
}
