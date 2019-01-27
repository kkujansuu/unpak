package kku.pak;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class PakEntry
{
	PakFile file;
	private String filename; 
	private short code;
	private long startpos;
	private int eof = 1234;
	private boolean showprogress = false;

	public PakEntry(PakFile file, String filename, short code, int eof, long startpos)
	{
		super();
		this.file = file;
		this.filename = filename;
		this.code = code;
		if (code > 0) this.filename += "." + code; 
		this.eof = eof;
		this.startpos = startpos;
	}

	public String toString()
	{
		return Util.pad(this.filename,40) + Util.pad(this.code,5)+ Util.pad(this.eof,12);
	}

	private List getEditpage(byte[] page) throws IOException
	{
		List lines = new ArrayList();
		ByteArrayInputStream bais = new ByteArrayInputStream(page);
		DataInputStream dis = new DataInputStream(bais);
		short updatecount = dis.readShort();
		while (dis.available()>0)
		{
			byte b = dis.readByte();
			if (b == -1) break; // PageTerminator
			dis.readByte();
			dis.readByte();
			dis.readByte();
			int compressedlinelen = unsigned(dis.readByte());
			byte[] compressedline = new byte[compressedlinelen];
			dis.read(compressedline);

			String line = decompressEditline(compressedline);
			lines.add(line);
		}
		return lines;
	}

	private String decompressEditline(byte[] compressedline)
	{
		int i = 0;
		byte[] line = new byte[0];
//		System.out.println(compressedline.length);
		while (i < compressedline.length)
		{
			int precedingSpaces = unsigned(compressedline[i]) >> 4;
			int nonSpaces = compressedline[i] & 15;
			if (precedingSpaces > 0)
			{
				byte[] blanks = new byte[precedingSpaces];
				for (int j = 0; j < precedingSpaces; j++)
				{
					blanks[j] = 0x20;
				}
				line = append(line, blanks);
			}
			line = append(line, Arrays.copyOfRange(compressedline, i+1, i+1+nonSpaces) );
			i += 1 + nonSpaces;
		}
		return new String(line);
	}

	private int unsigned(byte b)
	{
		if (b<0) return (b+256) & 0xff;
		return b;
	}


	private List getEditdata(List pages) throws IOException
	{
		List lines = new ArrayList();
		if (pages.size() == 0) return lines; // empty file
		int n = pages.size() - 1;
		byte[] lastpage = (byte[]) pages.get(n); 
		int len = lastpage.length;
		short usedpages = Util.readShort(lastpage, len-2);

		while (lastpage.length < 2+6*usedpages)
		{
			lastpage = append((byte[])pages.get(n-1), lastpage);
			n = n-1;
		}
		for (int i=0;i<usedpages;i++)
		{
			int j = lastpage.length-2-6*usedpages+6*i+4;
			short pagenum = Util.readShort(lastpage,j);
			lines.addAll(getEditpage((byte[]) pages.get(pagenum)));
		}
		return lines;
	}

	private byte[] append(byte[] buf1, byte[] buf2)
	{
		byte[] buf = new byte[buf1.length+buf2.length];
		System.arraycopy(buf1,0,buf,0,buf1.length);
		System.arraycopy(buf2,0,buf,buf1.length,buf2.length);
		return buf;
	}

	public List getdata() throws IOException, DataFormatException
	{
		PakRandomAccessFile f = this.file.f;

		f.seek(this.startpos);
//		System.out.println(this.startpos);
		int recnum = 0;
		List pages = new ArrayList();

		Inflater inflater = new Inflater();

		while (true)
		{

			short clen = f.readShort();
//			System.out.println("clen="+clen);
			if (clen == -1)
			{
//				System.out.println("eof");
				return getEditdata(pages);
//				if (recnum == 0) break; //  two eof's -> end of tape;      
//				recnum = 0;
//				continue;
			}
			byte[] buf = new byte[clen];
			f.readFully(buf);
			inflater.setInput(buf);
			byte[] data = new byte[30000];
			int len = inflater.inflate(data);
			inflater.reset();
			if  (recnum > 0)
			{
				int i = 10;
				while (i<len)
				{
					int pagelen = len-i;
					if (pagelen > 2048) pagelen = 2048;
					byte[] page = new byte[pagelen];
					System.arraycopy(data, i, page, 0, pagelen);
					pages.add(page);
					i += pagelen;
				}
			}
			recnum += 1;
			if (odd(clen)) f.read();


		}
	}

	public void save() throws IOException, DataFormatException
	{
	    try {
		    save(this.filename);
        }
        catch (RuntimeException e) {
            if (this.code == 101) {
                System.err.println("--- Error occurred while unpaking EDIT file " + this.filename);
                e.printStackTrace();
                this.code = 0;
                save(this.filename);
                System.err.println("--- Unpaked as binary file: " + this.filename);
            }
            else
                throw e;
        }
	}
	
	public void saveBinary() throws IOException, DataFormatException
	{
	    this.code = 0;
		save();
	}

	public void save(String outfile) throws IOException, DataFormatException
	{
		FileOutputStream fos = new FileOutputStream(outfile);
		
		PakRandomAccessFile f = this.file.f;

		f.seek(this.startpos);
//		System.out.println(this.startpos);
		int recnum = 0;
		List pages = new ArrayList();

		Inflater inflater = new Inflater();

		long totalbytes = 0;
		long lastpct = -1;
		while (true)
		{

			short clen = f.readShort();
//			System.out.println("clen="+clen);
			if (clen == -1) // eof
			{
//				System.out.println("eof");
				if (this.code == 101)
				{
					List lines = getEditdata(pages);
					PrintStream ps = new PrintStream(fos);
					for (Iterator iter = lines.iterator(); iter.hasNext();)
					{
						String line = (String) iter.next();
						ps.println(line);
					}
					ps.close();
					return;
				}
				fos.close();
				return;
			}
			byte[] buf = new byte[clen];
			f.readFully(buf);
			
			
    		byte[] data;
        	int len;
			if (this.file.compression > 0)
        	{
        		data = new byte[30000];
        		inflater.setInput(buf);
        		len = inflater.inflate(data);
        		inflater.reset();
        	}
        	else
        	{
        		data = buf;
        		len = buf.length;
        	}

			
			
			
//			inflater.setInput(buf);
//			byte[] data = new byte[30000];
//			int len = inflater.inflate(data);
//			inflater.reset();
			if  (recnum > 0 && this.code != 101)
			{
				fos.write(data, 10, len-10);
				if (showprogress) 
				{
					totalbytes += len-10;
					long pct = 100*totalbytes/this.eof;
					if (pct != lastpct)
						System.out.print("\r"+this+" "+pct+"%");
					lastpct = pct;
				}
			}
			if  (recnum > 0 && this.code == 101)
			{
				int i = 10;
				while (i<len)
				{
					int pagelen = len-i;
					if (pagelen > 2048) pagelen = 2048;
					byte[] page = new byte[pagelen];
					System.arraycopy(data, i, page, 0, pagelen);
					pages.add(page);
					i += pagelen;
				}
			}
			recnum += 1;
			if (odd(clen)) f.read();
		}
	}

	private boolean odd(short n)
	{
		return (n & 1)== 1;
	}


}
