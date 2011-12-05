package com.winvector.util;

import java.io.Serializable;

public interface LineBurster extends Serializable {
	BurstMap parse(String s);
	boolean haveAllFields(BurstMap next);
}
