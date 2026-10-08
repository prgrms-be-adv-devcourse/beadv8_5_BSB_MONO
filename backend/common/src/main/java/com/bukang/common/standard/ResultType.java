package com.bukang.common.standard;

public interface ResultType<T> {
	int status();

	String message();

	default T data() {
		return null;
	}

}
