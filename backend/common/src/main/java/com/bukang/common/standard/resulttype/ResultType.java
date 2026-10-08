package com.bukang.common.standard.resulttype;

public interface ResultType<T> {
	int status();

	String message();

	default T data() {
		return null;
	}

}
