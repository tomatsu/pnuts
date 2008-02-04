package pnuts.compiler;

class Symbol {
	int seq;
	String prefix;

	Symbol(){
		this.prefix = "_";
	}
	Symbol(String prefix){
		this.prefix = prefix;
	}
	String gen(){
		return prefix + seq++;
	}
}
