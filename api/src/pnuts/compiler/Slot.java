package pnuts.compiler;

class Slot {

	Object key;
	Object value;
	Slot chain;

	Slot(){
	}

	Slot(Object key, Object value){
		this.key = key;
		this.value = value;
	}
	
	public void set(Object value){
		this.value = value;
	}

	public Object get(){
		return value;
	}
}
