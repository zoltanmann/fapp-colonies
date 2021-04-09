import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Colony {
	private Set<IHwNode> nodes;
	private Set<Colony> neighbors;
	private List<Application> applications;

	public Colony() {
		nodes=new HashSet<>();
		neighbors=new HashSet<>();
		applications=new ArrayList<>();
	}
}
