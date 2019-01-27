package kku.pak;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.print.attribute.standard.Compression;
/*
 *
 * Header structure from PAK source:
	struct
	{
	    char   ident[4]; 	// "PAK "
	    short  version;		// 1 or 2
	    ulong  data_offset;	// offset for actual BACKUP data 
	    ulong  filesize;  	// size of the archive (for single-file archives?)
	    short  compression; // 0: no compression, 1:zlib compression
	    ulong  splitsize; 	// if > 0 then multifile archive
	    int64  totalsize; 	// size of the archive (for multi-file archives?)
	    short  numfiles; 	// number of files in the archive (for multi-file archives?)
	    POS    comment_ptr;
	    POS    unpak_cmd_ptr;
	    POS    after_cmd_ptr;
	} header;

 */
public class PakFile
{

	private List entrylist = new ArrayList();
	PakRandomAccessFile f;
	short version;
	short compression = 1;

	public PakFile(String fname) throws PakException, IOException, DataFormatException
	{
		int pos = 0;
		f = new PakRandomAccessFile(fname,"r");
		f.seek(pos);
		byte[] bytes = new byte[4];
		f.read(bytes);
		String s = new String(bytes);
		if (s.equals("PAKX")) throw new PakException("Encrypted PAK archive not supported: " + fname );
		if (!s.equals("PAK "))
		{
			// maybe self-extracting
			long eof = f.length();
			if (eof < 4)
				throw new PakException(fname + " does not look like a PAK archive");
//			System.out.println("eof="+eof);
			f.seek(eof-4);
			pos = f.readInt();
			if (pos < 0 || pos >= eof)
				throw new PakException(fname + " does not look like a PAK archive");
//			System.out.println("pos="+pos);
			f.seek(pos);
			f.read(bytes);
			s = new String(bytes);
			if (s.equals("PAKX")) throw new PakException("Encrypted PAK archive not supported: " + fname );
			if (!s.equals("PAK "))
				throw new PakException(fname + " does not look like a PAK archive");
		}
//		System.out.println(new String(bytes));
		this.version = f.readShort();
		System.out.println("Archive version: " + this.version);
		System.out.println("");
		int data_offset = f.readInt();
		if (this.version > 1)
		{
			int filesize = f.readInt();
			compression = f.readShort();
			int splitSize = f.readInt();
			long totalSize = f.readLong();
//			System.out.println("filesize="+filesize+"compression="+compression+" splitsize="+splitSize+" totalsize="+totalSize);
//			if (splitSize > 0 ) throw new PakException("Multi-file PAK archive not supported: " + fname );
			if (splitSize > 0 )
			{
				f.setSplitSize(splitSize);
			}
			else
				if (totalSize != f.length()) System.err.println("Warning: file might be corrupted; expected EOF="+totalSize);
		}
		f.seek(pos+data_offset);
		
        long filecount = 0;
        long recnum = 0;
        Inflater inflater = new Inflater();
        long nextfilePos = 0;
        while (true)
        {
            long curpos = f.getFilePointer();
//        	if (recnum == 0) System.out.println("0 curpos="+curpos);
            short clen = f.readShort();
			if (clen == -1)
            {
                filecount++;
                if (recnum == 0) break; //  two eof's -> end of tape;      
                recnum = 0;
                if (this.version == 2)
                {
                	int fsize = f.readInt();
                	nextfilePos = curpos + 6 + fsize;
//                	System.out.println("  fsize="+fsize);
//                	System.out.println("  curpos="+curpos);
//                	System.out.println("  nextfilePos="+nextfilePos);
                }
                if (this.version == 3)
                {
                	long fsize = f.readLong();
                	nextfilePos = curpos + 10 + fsize;
                }
                continue;
            }
            byte[] buf = new byte[clen];
            f.readFully(buf);
//            System.out.println(count);
            if  (recnum == 0)
            {
        		byte[] data;
            	int len;
				if (compression > 0)
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
        		if (len < 146) break;
                if (filecount > 1)
                {
                	String filename = new String(data,46,24);
                	filename = fnamecollapse(filename);
                	//                code = struct.unpack(">h",x[144:146])[0]
                	short code = Util.readShort(data,144);
                	int eof = Util.readInt(data,30);
//                	System.out.println(filename+" "+code+" "+curpos);
                	PakEntry e = new PakEntry(this,filename,code,eof,curpos);
                	this.entrylist.add(e);
                }
                if (this.version == 2 && nextfilePos > 0) 
                {
//                	System.out.println("Jump to " + nextfilePos);
                	f.seek(nextfilePos);
                    recnum = 1;
                	continue;
                }
            }       
            recnum += 1;
            if (odd(clen)) f.read();
        }
//        f.close();
	}

	void close() throws IOException
	{
		f.close();
	}
	
//    int getData(byte[] buf)
//    {
//	inflater.setInput(buf);
//	byte[] data = new byte[30000];
//	int len = inflater.inflate(data);
//	inflater.reset();
//    }

	private boolean odd(short n)
	{
		return (n & 1)== 1;
	}

	private String fnamecollapse(String filename)
	{
		String vol;
		if (filename.charAt(0) == '\\')
			vol = "$" + filename.substring(2,8);
		else
			vol = filename.substring(0,8);
		vol = vol.trim();
		String subvol = filename.substring(8,16).trim();
		String file = filename.substring(16,24).trim();
		return vol + "." + subvol + "." + file;
	}

	private void dump(byte[] data, int len)
	{
		boolean inQuotes = false;
		for (int i = 0; i < len; i++)
		{
			byte c = data[i];
			if (c < ' ' || c > '~')
			{
				if (inQuotes) System.out.print("'");
				inQuotes = false;
				System.out.print(" "+c);
			}
			else
			{
				if (!inQuotes) System.out.print("'");
				inQuotes = true;
				System.out.print((char)c);
			}
		}
		System.out.println();
	}

	public List getEntries()
	{
		return entrylist;
	}

}
