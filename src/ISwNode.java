import java.util.Set;

public interface ISwNode {
	public String getId();
	public void addConnector(Connector c);
	public Set<Connector> getConnectors();
	public boolean isEndDevice();
}
