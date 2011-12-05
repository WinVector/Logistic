package com.winvector.util;

import java.io.IOException;

import com.winvector.util.SerialUtils;

import junit.framework.TestCase;

public class TestSerialUtils extends TestCase {
	public void testBase64() throws IOException, ClassNotFoundException {
		byte[] b = new byte[256];
		for(int i=0;i<b.length;++i) {
			b[i] = (byte)i;
		}
		final String enc =  SerialUtils.serializableToString(b);
		assertNotNull(enc);
		for(int i=0;i<enc.length();++i) {
			assertFalse(Character.isWhitespace(enc.charAt(i)));
		}
		final byte[] dec = (byte[])SerialUtils.readSerialiazlabeFromString(enc);
		assertNotNull(dec);
		assertEquals(b.length,dec.length);
		for(int i=0;i<b.length;++i) {
			assertEquals(b[i],dec[i]);
		}
	}
}
