package com.bukang.standard.resulttype;

public interface ResultType<T> {
	int status();

	String message();

	default T data() {
		return null;
	}

}
