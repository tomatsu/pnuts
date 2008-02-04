package pnuts.compiler;

public abstract class DynamicProxy {

    public Object invoke(Object target){
		throw new RuntimeException();
    }

    public Object invoke(Object target, Object[] args){
		throw new RuntimeException();
    }
}
