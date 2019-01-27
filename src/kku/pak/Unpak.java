package kku.pak;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;

public class Unpak
{

	/**
	 * @param args
	 * @throws DataFormatException 
	 * @throws IOException 
	 * @throws Exception 
	 */
	public static void main(String[] args) throws IOException, DataFormatException // throws Exception
	{
		System.out.println("Unpak v1.5 (2019-01-27 - kari.kujansuu@gmail.com)");
		System.out.println("");

		boolean listOnly = false;
		if (args.length < 1 || args.length > 2)
		{
		    usage();
		}
		String fname = args[0];
		if (args.length == 2)
		{
		    String option = args[1];
		    if (option.equalsIgnoreCase("listonly")) 
		        listOnly = true;
		    else 	
		        usage();
		}
		
		try
		{
			doUnpak(fname, listOnly);
		}
		catch (PakException e)
		{
			System.err.println(e.getMessage());
//			e.printStackTrace();
		}
	}

	private static void doUnpak(String fname, boolean listOnly) throws PakException, IOException, DataFormatException // throws Exception
	{
		if (!listOnly) System.out.println("Extracting " + fname);
		if (listOnly) System.out.println("Listing contents of " + fname);

		PakFile f = new PakFile(fname);

		List entries = f.getEntries();
		String hdr = Util.pad("Filename",40) + Util.pad("Code",-5) + Util.pad("Eof",-12);
		System.out.println(hdr);

		int count = 0;
		for (Iterator iter = entries.iterator(); iter.hasNext();)
		{
			PakEntry entry = (PakEntry) iter.next();
			System.out.print(entry);
			if (!listOnly) entry.save();
			System.out.println();
			count++;
		}
		String files;
		if (count == 1)
			files = " file ";
		else
			files = " files ";
		if (listOnly) System.out.println(count + files + "in archive");
		if (!listOnly) System.out.println(count + files + "extracted");
	}

	private static void usage()
	{
		System.out.println("Usage:");
		System.out.println("   java -cp unpak.jar kku.pak.Unpak <pakfile> [ listonly ]");
		System.out.println("or");
		System.out.println("   java -jar unpak.jar <pakfile> [ listonly ]");
		System.out.println("");
		System.exit(0);
	}

}
