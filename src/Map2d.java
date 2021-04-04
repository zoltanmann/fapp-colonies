import java.util.HashMap;
import java.util.Map;

public class Map2d<K1,K2,V> {

	private Map<K1,Map<K2,V>> data;

	public Map2d() {
		data=new HashMap<K1, Map<K2,V>>();
	}

	public boolean containsKey(K1 k1,K2 k2) {
		if(!data.containsKey(k1))
			return false;
		Map<K2,V> map=data.get(k1);
		return map.containsKey(k2);
	}

	public V get(K1 k1,K2 k2) {
		if(!data.containsKey(k1))
			return null;
		Map<K2,V> map=data.get(k1);
		return map.get(k2);
	}

	public void put(K1 k1,K2 k2,V v) {
		Map<K2,V> map;
		if(!data.containsKey(k1)) {
			map=new HashMap<>();
			data.put(k1,map);
		} else {
			map=data.get(k1);
		}
		map.put(k2,v);
	}

	public String toString() {
		StringBuffer sb=new StringBuffer();
		sb.append("[");
		boolean start=true;
		for(K1 k1 : data.keySet()) {
			Map<K2,V> map=data.get(k1);
			for(K2 k2 : map.keySet()) {
				V v=map.get(k2);
				if(!start)
					sb.append(", ");
				sb.append(""+k1+"-"+k2+"-"+v);
				start=false;
			}
		}
		sb.append("]");
		return sb.toString();
	}
}
