package kku.pak;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class PakRandomAccessFile {
	private String fname;
	private String mode;
	private String curfname;
	RandomAccessFile f;
	private long filesize;
	private long curpos;
	private long filestart;
	private long splitSize;
	private int seq;
	private int openedseq;
	
	public PakRandomAccessFile(String fname, String mode) throws FileNotFoundException, IOException {
		this.fname = fname;
		this.mode = mode; // must be "r"
		this.curfname = fname;
		this.splitSize = 0; // for multifile archives this will be set later with setSplitSize()
		this.curpos = 0;
		this.filesize = 0;
		this.filestart = 0;
		this.seq = 0;
		this.openedseq = -1;
		openfile();
	}

	public void close() throws IOException {
		f.close();
	}

	private int get_fileseq(long curpos) {
		if (this.splitSize == 0) return 0;
		return (int) (curpos / this.splitSize);
	}

	public long getFilePointer() {
		return this.curpos;
	}

	public long length() throws IOException {
		return this.f.length();  // not correct but this is called only for the first file...
	}

	private void openfile() throws IOException, FileNotFoundException {
		if (this.openedseq == this.seq) return; 
		if (this.seq == 0)
			this.curfname = this.fname;
		else
			this.curfname = generate_filename(this.fname,this.seq);
		if (this.f != null) this.f.close();
		this.f = new RandomAccessFile(this.curfname,this.mode);
		this.filestart = this.seq*this.splitSize;
		this.filesize = this.f.length();
		this.openedseq = this.seq; 
	}

	private String generate_filename(String fname, int seq) {
		{
			String suffix = "" + seq;
			int i = fname.lastIndexOf(File.separator);
			String path;
			if (i == -1)
				path = "";
			else
				path = fname.substring(0,i+1);
			String name = fname.substring(i+1) + "0000000";
			String name_with_seq = name.substring(0,8-suffix.length()) +suffix;
			return path + name_with_seq;
		}
	}

	public void read() throws IOException {
		this.f.read();
		this.curpos += 1;
	}

	public void read(byte[] bytes) throws IOException {
		this.readFully(bytes);
	}

	public void readFully(byte[] buf) throws IOException {
	    long   offset;
	    int readcount;
	    long space;
	    int off = 0;
	    long count = buf.length;

	    while (count > 0)
	    {
	        this.seq = get_fileseq(this.curpos);
	        openfile();
	        offset = this.curpos - this.filestart;
	        space = this.filesize - offset;
	        if (count <= space)
	            readcount = (int) count;
	        else
	            readcount = (int) space;
	        this.f.readFully(buf, off, readcount);
	        this.curpos += readcount;
	        off += readcount;
	        count -= readcount;
	    }
	}

	public int readInt() throws IOException {
		byte[] buf = new byte[4];
		this.readFully(buf);
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readInt();
	}

	public long readLong() throws IOException {
		byte[] buf = new byte[8];
		this.readFully(buf);
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readLong();
	}

	public short readShort() throws IOException {
		byte[] buf = new byte[2];
		this.readFully(buf);
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bais);
		return dis.readShort();
	}

	public void seek(long startpos) throws IOException {
		while (true)
		{
			if (startpos < this.filestart)
			{
				this.seq--;
				openfile();
				continue;
			}
			if (startpos >= this.filestart + this.filesize )
			{
				this.seq++;
				openfile();
				continue;
			}
			f.seek(startpos-this.filestart);
			this.curpos = startpos;
			return;
		}
	}

	public void setSplitSize(int splitSize) {
		this.splitSize = splitSize;
	}

}
