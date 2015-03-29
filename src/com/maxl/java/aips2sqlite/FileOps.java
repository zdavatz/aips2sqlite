/*
Copyright (c) 2014 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.maxl.java.aips2sqlite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileOps {

	static public String readCSSfromFile(String filename) {
		String css_str = "";		
        try {
        	FileInputStream fis = new FileInputStream(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line;
            while ((line = br.readLine()) != null) {
                css_str += line;
            }
            System.out.println(">> Success: Read CSS file " + filename);
            br.close();
        }
        catch (Exception e) {
        	System.err.println(">> Error in reading in CSS file");        	
        }
        
		return css_str;	
	}
	
	static public String readFromFile(String filename) {
		String file_str = "";
		try {
			FileInputStream fis = new FileInputStream(filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			String line;
			while ((line = br.readLine()) != null) {
				file_str += (line + "\n");
			}
			br.close();
		} catch (Exception e) {
			System.err.println(">> Error in reading file");
		}

		return file_str;
	}	
	
	static public void writeToFile(String string_to_write, String dir_name,	String file_name) {
		try {
			File wdir = new File(dir_name);
			if (!wdir.exists())
				wdir.mkdirs();
			File wfile = new File(dir_name + file_name);
			if (!wfile.exists()) {
				wfile.getParentFile().mkdirs();
				wfile.createNewFile();
			}
			
			// FileWriter fw = new FileWriter(wfile.getAbsoluteFile());
			CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
			encoder.onMalformedInput(CodingErrorAction.REPORT);
			encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(wfile.getAbsoluteFile()), encoder);
			BufferedWriter bw = new BufferedWriter(osw);
			bw.write(string_to_write);
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static public void zipToFile(String dir_name, String file_name) {
		byte[] buffer = new byte[1024];

		try {
			FileOutputStream fos = new FileOutputStream(dir_name + changeExtension(file_name, "zip"));
			ZipOutputStream zos = new ZipOutputStream(fos);
			ZipEntry ze = new ZipEntry(file_name);
			zos.putNextEntry(ze);
			FileInputStream in = new FileInputStream(dir_name + file_name);

			int len = 0;
			while ((len = in.read(buffer)) > 0) {
				zos.write(buffer, 0, len);
			}
			in.close();
			zos.closeEntry();
			zos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
			
	static public void writeToFile(String path, byte[] buf) {
		try {
			FileOutputStream fos = new FileOutputStream(path);
			fos.write(buf);
			fos.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	static public void encryptFileToDir(String filename, String dir) {
		// First check if path exists
		File f = new File(dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + dir + " does not exist!");
			return;
		}
		try {
			File inputFile = new File(dir + "/" + filename);
			FileInputStream inputStream = new FileInputStream(inputFile);
	        byte[] serializedBytes = new byte[(int) inputFile.length()];
	        inputStream.read(serializedBytes);
	        
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (serializedBytes.length>0) {
				encrypted_msg = crypto.encrypt(serializedBytes);
			}
			// Write to file
			writeToFile(Constants.DIR_OUTPUT + filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + filename +".ser");

	        inputStream.close();
		} catch(IOException e) {
			e.printStackTrace();
		} 
	}
	
	static public void encryptCsvToDir(String in_filename_1, String in_filename_2, String in_dir, 
			String out_filename, String out_dir, int skip, int cols) {
		// First check if paths exist
		File f = new File(in_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + in_dir + " does not exist!");
			return;
		}
		// First check if path exists
		f = new File(out_dir);
		if (!f.exists() || !f.isDirectory()) {
			System.out.println("Directory " + out_dir + " does not exist!");
			return;
		}
		try {
			Map<String, String> gln_map = new TreeMap<String, String>();
			// Load csv file and dump to map			
			{
				FileInputStream glnCodesCsv = new FileInputStream(in_dir + "/" + in_filename_1 + ".csv");
				BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
				String line;
				while ((line=br.readLine()) !=null ) {
					// Semicolon is used as a separator
					String[] gln = line.split(";");
					if (gln.length>(cols-1)) {
						if (cols==2)
							gln_map.put(gln[0], gln[1]);
						else if (cols==3)
							gln_map.put(gln[0], gln[1]+";"+gln[2]);
					}
				}			
				glnCodesCsv.close();				
				br.close();
			}
			// Used when files are merged
			{
				if (!in_filename_2.isEmpty()) {
					FileInputStream glnCodesCsv = new FileInputStream(in_dir + "/" + in_filename_2 + ".csv");
					BufferedReader br = new BufferedReader(new InputStreamReader(glnCodesCsv, "UTF-8"));
					String line;
					while ((line=br.readLine()) !=null ) {
						// Semicolon is used as a separator
						String[] gln = line.split(";");
						if (!gln_map.containsKey(gln[0]) && gln.length>(cols-1)) {
							if (cols==2)
								gln_map.put(gln[0], gln[1]);
							else if (cols==3)
								gln_map.put(gln[0], gln[1]+";"+gln[2]);
						}
					}	
					glnCodesCsv.close();
					br.close();
				}
			}
			// First serialize into a byte array output stream, then encrypt
			Crypto crypto = new Crypto();
			byte[] encrypted_msg = null;
			if (gln_map.size()>0) {
				byte[] serializedBytes = FileOps.serialize(gln_map);
				if (serializedBytes!=null) {
					encrypted_msg = crypto.encrypt(serializedBytes);
				}
			}
			// Write to file
			FileOps.writeToFile(out_dir + out_filename +".ser", encrypted_msg);
			System.out.println("Saved encrypted file " + out_filename +".ser");
		} catch(IOException e) {
			e.printStackTrace();			
		}
	}
	
	static public byte[] serialize(Object obj) {
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();	// new byte array
			ObjectOutputStream sout = new ObjectOutputStream(bout);		// serialization stream header
			sout.writeObject(obj);							// write object to serialied stream
			return (bout.toByteArray());
		} catch(IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static public Object deserialize(byte[] byteArray) {
		try {
			ByteArrayInputStream bin = new ByteArrayInputStream(byteArray);
			ObjectInputStream sin = new ObjectInputStream(bin);
			return sin.readObject();
		} catch(IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static public String changeExtension(String orig_name, String new_extension) {
		int last_dot = orig_name.lastIndexOf(".");
		if (last_dot != -1)
			return orig_name.substring(0, last_dot) + "." + new_extension;
		else
			return orig_name + "." + new_extension;
	}	
	
	static public File touchFile(String path_name) {
		try {
			File db_file = new File(path_name);
			if (!db_file.exists()) {
				db_file.getParentFile().mkdirs();
				db_file.createNewFile();
			}
			return db_file;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
