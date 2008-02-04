package org.pnuts.nio;

public class ByteArrayCharSequence implements CharSequence {
	private byte[] bytes;
	private int offset;
	private int size;

	public ByteArrayCharSequence(byte[] b, int offset, int size){
		this.bytes = b;
		this.offset = offset;
		this.size = size;
	}

	public int length(){
		return this.size;
	}
	
	public char charAt(int index){
		return (char)bytes[offset + index];
	}

	public CharSequence subSequence(int start, int end){
		return new ByteArrayCharSequence(bytes, offset + start, end - start);
	}
	
	public String toString(){
		return new String(bytes, offset, size);
	}
}
