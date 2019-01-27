package kku.pak;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class Util 
{
	/**
	 * @param bytes
	 * @param len
	 * @throws IOException
	 */
	public static short readShort(byte[] bytes, int offset) throws IOException
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes,offset,2);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readShort();
	}      
	
	public static int readInt(byte[] bytes, int offset) throws IOException
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes,offset,4);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readInt();
	}      

	public static long readLong(byte[] bytes, int offset) throws IOException
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes,offset,8);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readLong();
	}      
	
	public static String pad(long num, int width)
	{
		String str = "" + num;
		while (str.length() < width) str = " " + str;
		return str.substring(0,width);
	}

	public static String pad(String str, int width)
	{
		if (width<0)
			while (str.length() < Math.abs(width)) str = " " + str;
		else
			while (str.length() < width) str += " ";
		return str.substring(0,Math.abs(width));
	}

}
