package com.winvector.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;

public final class SerialUtils {
	
	public static String bytesToEncodedString(final byte[] bytes) {
		final byte[] enc = Base64.encodeBase64(bytes);
		final String s = new String(enc); 
		return s.replaceAll("\\s+","");		
	}
	
	public static byte[] bytesFromEncodedString(final String s) {
		final byte[] bytes = Base64.decodeBase64(s.replaceAll("\\s+","").getBytes());
		return bytes;
	}
	
	public static String serializableToString(final Serializable o) throws IOException {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		final ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(bos));
		oos.writeObject(o);
		oos.close();
		return bytesToEncodedString(bos.toByteArray());
	}
	
	@SuppressWarnings("unchecked")
	public static <Y extends Serializable> Y readSerialiazlabeFromString(final String s) throws IOException, ClassNotFoundException {
		final byte[] bytes = bytesFromEncodedString(s);
		final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		final ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(bis));
		final Object o = ois.readObject();
		ois.close();
		return (Y)o;
	}
}
