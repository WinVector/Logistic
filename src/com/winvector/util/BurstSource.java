package com.winvector.util;

import java.io.IOException;

public interface BurstSource extends Iterable<BurstMap> { 
	void close() throws IOException;
}
