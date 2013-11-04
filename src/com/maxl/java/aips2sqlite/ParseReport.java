package com.maxl.java.aips2sqlite;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ParseReport {

	private String mFileName = "";
	private BufferedWriter mBWriter = null;
	
	public ParseReport(String reportBase, String language, String extension) {
		
		DateFormat df = new SimpleDateFormat("ddMMyy");
		String date_str = df.format(new Date());
		mFileName = reportBase + "_" + date_str + "_" + language + "." + extension;
	}
	
	public BufferedWriter getBWriter() {
		try {
			File report_file = new File(mFileName);
			if (!report_file.exists()) {
				report_file.getParentFile().mkdirs();
				report_file.createNewFile();
			}
			mBWriter = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(report_file.getAbsoluteFile()),"UTF-8"));	
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return mBWriter;
	}
	
	
}
