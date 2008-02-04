package pnuts.compiler;

class BeanEnv {
    BeanEnv parent;

    BeanEnv(BeanEnv parent){
	this.parent = parent;
    }
}
